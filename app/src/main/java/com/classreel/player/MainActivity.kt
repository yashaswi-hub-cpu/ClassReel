package com.classreel.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.classreel.player.captions.CaptionEngine
import com.classreel.player.data.HistoryItem
import com.classreel.player.data.HistoryStore
import com.classreel.player.ui.ClassReelTheme
import com.classreel.player.ui.HomeScreen
import com.classreel.player.ui.Ink
import com.classreel.player.ui.PlayerScreen
import com.classreel.player.ui.VideoSource

class MainActivity : ComponentActivity() {

    private val store by lazy { HistoryStore(applicationContext) }
    private val engine by lazy { CaptionEngine(applicationContext) }
    private var current by mutableStateOf<VideoSource?>(null)

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) open(uri, persist = true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            ClassReelTheme {
                Box(Modifier.fillMaxSize().background(Ink)) {
                    val video = current
                    if (video == null) {
                        HomeScreen(
                            store = store,
                            onPick = {
                                picker.launch(
                                    arrayOf("video/*", "application/x-matroska", "application/octet-stream")
                                )
                            },
                            onOpen = { openFromHistory(it) }
                        )
                    } else {
                        PlayerScreen(
                            video = video,
                            store = store,
                            engine = engine,
                            onClose = { current = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { open(it, persist = false) }
        }
    }

    private fun open(uri: Uri, persist: Boolean) {
        if (persist) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
        }
        current = VideoSource(uri, displayName(uri))
    }

    private fun openFromHistory(item: HistoryItem) {
        val uri = Uri.parse(item.uri)
        val ok = try {
            contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
        if (ok) {
            current = VideoSource(uri, item.name)
        } else {
            Toast.makeText(
                this,
                "Can't reach that file any more. Tap \"Open a video\" and pick it again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i)?.let { return it }
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
    }
}
