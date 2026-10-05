package com.classreel.player.captions

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

enum class CaptionModel(
    val label: String,
    val hint: String,
    val folder: String,
    val url: String,
    val roman: Boolean,
    val sizeMb: Int
) {
    HINGLISH(
        "Hinglish", "Hindi speech written in English letters",
        "vosk-model-small-hi-0.22",
        "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip", true, 42
    ),
    ENGLISH_IN(
        "English (Indian accent)", "Best when the teacher speaks mostly English",
        "vosk-model-small-en-in-0.4",
        "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip", false, 36
    )
}

sealed interface ModelState {
    object Missing : ModelState
    data class Downloading(val pct: Int) : ModelState
    object Loading : ModelState
    object Ready : ModelState
    data class Error(val msg: String) : ModelState
}

data class Caption(val startMs: Long, val endMs: Long, val text: String)

/**
 * Offline speech-to-text using Vosk. The model is downloaded once (~40 MB) and then
 * everything runs on the phone, no internet needed.
 */
class CaptionEngine(private val ctx: Context) {

    val state = MutableStateFlow<ModelState>(ModelState.Missing)
    val partial = MutableStateFlow<Caption?>(null)
    val finals = ConcurrentSkipListMap<Long, Caption>()

    private val prefs = ctx.getSharedPreferences("captions", Context.MODE_PRIVATE)

    @Volatile
    var model: CaptionModel =
        CaptionModel.values().firstOrNull { it.name == prefs.getString("model", "") } ?: CaptionModel.HINGLISH
        private set

    @Volatile
    var active = false

    private var vosk: Model? = null

    @Volatile
    private var recognizer: Recognizer? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)
    private val lock = Mutex()

    private var segStart = -1L
    private var lastEnd = 0L

    private fun modelDir(m: CaptionModel) = File(ctx.filesDir, "models/${m.folder}")
    fun isDownloaded(m: CaptionModel) = File(modelDir(m), ".done").exists()

    suspend fun switchModel(m: CaptionModel) {
        lock.withLock {
            if (m == model && state.value == ModelState.Ready) return
            closeRecognizer()
            model = m
            prefs.edit().putString("model", m.name).apply()
            finals.clear()
            state.value = ModelState.Missing
        }
        ensureReady()
    }

    suspend fun ensureReady() {
        lock.withLock {
            if (state.value == ModelState.Ready && recognizer != null) return
            val m = model
            withContext(Dispatchers.IO) {
                try {
                    if (!isDownloaded(m)) {
                        state.value = ModelState.Downloading(0)
                        download(m)
                    }
                    state.value = ModelState.Loading
                    LibVosk.setLogLevel(LogLevel.WARNINGS)
                    val mdl = Model(modelDir(m).absolutePath)
                    val rec = Recognizer(mdl, 16000f)
                    vosk = mdl
                    recognizer = rec
                    state.value = ModelState.Ready
                } catch (t: Throwable) {
                    state.value = ModelState.Error(t.message ?: t.javaClass.simpleName)
                }
            }
        }
    }

    private fun download(m: CaptionModel) {
        val root = File(ctx.filesDir, "models").apply { mkdirs() }
        val tmp = File(ctx.cacheDir, m.folder + ".zip")
        val conn = URL(m.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: (m.sizeMb * 1024L * 1024L)
        var done = 0L
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    state.value = ModelState.Downloading(((done * 100) / total).toInt().coerceIn(0, 99))
                }
            }
        }
        state.value = ModelState.Loading
        modelDir(m).deleteRecursively()
        val rootPath = root.canonicalPath + File.separator
        ZipInputStream(tmp.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val f = File(root, e.name)
                require(f.canonicalPath.startsWith(rootPath)) { "Bad zip entry" }
                if (e.isDirectory) {
                    f.mkdirs()
                } else {
                    f.parentFile?.mkdirs()
                    f.outputStream().use { zin.copyTo(it) }
                }
                e = zin.nextEntry
            }
        }
        tmp.delete()
        File(modelDir(m), ".done").writeText("ok")
    }

    private fun closeRecognizer() {
        try {
            recognizer?.close()
            vosk?.close()
        } catch (_: Throwable) {
        }
        recognizer = null
        vosk = null
    }

    private fun conv(t: String) = if (model.roman) Romanizer.toRoman(t) else t

    /** Called from the audio thread. Never blocks; drops audio if recognition falls behind. */
    fun feed(pcm: ByteArray, startMs: Long, endMs: Long) {
        if (!active || recognizer == null) return
        if (pending.get() > 40) return
        pending.incrementAndGet()
        worker.execute {
            try {
                val r = recognizer ?: return@execute
                if (segStart < 0) segStart = startMs
                lastEnd = endMs
                if (r.acceptWaveForm(pcm, pcm.size)) {
                    emitFinal(JSONObject(r.result).optString("text"), endMs)
                } else {
                    val p = JSONObject(r.partialResult).optString("partial")
                    if (p.isNotBlank()) partial.value = Caption(segStart, endMs, conv(p))
                }
            } catch (_: Throwable) {
            } finally {
                pending.decrementAndGet()
            }
        }
    }

    private fun emitFinal(text: String, endMs: Long) {
        if (text.isNotBlank() && segStart >= 0) {
            finals[segStart] = Caption(segStart, endMs, conv(text))
        }
        segStart = -1
        partial.value = null
    }

    /** The video jumped somewhere else: finish what we heard and start fresh. */
    fun reset() {
        if (recognizer == null) return
        worker.execute {
            try {
                val r = recognizer ?: return@execute
                val t = JSONObject(r.finalResult).optString("text")
                if (t.isNotBlank() && segStart >= 0) finals[segStart] = Caption(segStart, lastEnd, conv(t))
                r.reset()
            } catch (_: Throwable) {
            }
            segStart = -1
            partial.value = null
        }
    }

    /** What should be on screen at this moment of the video. */
    fun captionAt(posMs: Long): String? {
        val p = partial.value
        val c: Caption = if (p != null && posMs >= p.startMs - 300 && posMs <= p.endMs + 1500) {
            p
        } else {
            val e = finals.floorEntry(posMs + 300)?.value ?: return null
            if (posMs > e.endMs + 1500) return null
            e
        }
        return windowText(c, posMs)
    }

    private fun windowText(c: Caption, pos: Long): String {
        val words = c.text.split(' ')
        if (words.size <= 16) return c.text
        val span = (c.endMs - c.startMs).coerceAtLeast(1)
        val frac = ((pos - c.startMs).toFloat() / span).coerceIn(0f, 1f)
        val center = (frac * words.size).toInt()
        val to = (center + 8).coerceIn(14, words.size)
        val from = (to - 14).coerceAtLeast(0)
        return words.subList(from, to).joinToString(" ")
    }
}
