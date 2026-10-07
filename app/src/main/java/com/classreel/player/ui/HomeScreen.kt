package com.classreel.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classreel.player.data.DeviceVideo
import com.classreel.player.data.HistoryItem
import com.classreel.player.data.HistoryStore
import com.classreel.player.data.VideoLibrary
import com.classreel.player.util.ago
import com.classreel.player.util.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    store: HistoryStore,
    hasStorage: Boolean,
    onGrantStorage: () -> Unit,
    onPick: () -> Unit,
    onOpen: (HistoryItem) -> Unit,
    onOpenVideo: (DeviceVideo) -> Unit
) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(store.all()) }
    var tab by remember { mutableIntStateOf(if (items.isEmpty()) 0 else 1) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var askClear by remember { mutableStateOf(false) }
    val videos by produceState(initialValue = emptyList<DeviceVideo>(), hasStorage, tab) {
        value = if (hasStorage && tab == 0) withContext(Dispatchers.IO) { VideoLibrary.query(ctx) } else emptyList()
    }

    if (askClear) {
        ClearHistoryDialog(
            onConfirm = {
                store.clear()
                items = emptyList()
                selected = emptySet()
                askClear = false
            },
            onDismiss = { askClear = false }
        )
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp)
        ) {
            item { Header() }
            item { Tabs(tab) { tab = it; selected = emptySet(); if (it == 1) items = store.all() } }
            if (tab == 0) {
                if (!hasStorage) {
                    item { StoragePrompt(onGrantStorage) }
                } else if (videos.isEmpty()) {
                    item {
                        Text(
                            "No videos found on this phone.",
                            color = Muted, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 40.dp)
                        )
                    }
                } else {
                    items(videos, key = { it.uri.toString() }) { v -> LibraryRow(v) { onOpenVideo(v) } }
                }
            } else {
                historySection(
                    items = items,
                    query = query,
                    onQuery = { query = it },
                    selected = selected,
                    onToggle = { u -> selected = if (u in selected) selected - u else selected + u },
                    onOpen = onOpen,
                    onDeleteSelected = {
                        store.removeAll(selected)
                        selected = emptySet()
                        items = store.all()
                    },
                    onCancelSelect = { selected = emptySet() },
                    onAskClear = { askClear = true }
                )
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Amber)
                .clickable(onClick = onPick)
                .padding(horizontal = 28.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Ink)
                Spacer(Modifier.width(8.dp))
                Text("Open a video", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf("Library", "History").forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) Amber else Slate2)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(label, color = if (on) Ink else Chalk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun StoragePrompt(onGrant: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp)) {
        Text(
            "See all the videos on your phone",
            color = Chalk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Allow access to your videos so they show up here. You can still use \"Open a video\" without it.",
            color = Muted, fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Slate2)
                .clickable(onClick = onGrant)
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Text("Allow access", color = Amber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun LibraryRow(v: DeviceVideo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Slate)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Slate2),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Amber)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.name, color = Chalk, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${v.folder} · ${fmtTime(v.durationMs)}",
                color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.statusBarsPadding().padding(top = 28.dp, bottom = 22.dp)) {
        Text(
            "Class Reel",
            color = Chalk,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 38.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Play any lecture. Pick up exactly where you left.",
            color = Muted,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(16.dp))
        ChalkLine()
    }
}

@Composable
private fun ChalkLine() {
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = Chalk.copy(alpha = 0.25f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Muted,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp)
    )
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(Slate2),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Amber, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "No history yet",
            color = Chalk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Play a lecture and it shows up here, with the minute you stopped at.",
            color = Muted, fontSize = 14.sp
        )
    }
}
