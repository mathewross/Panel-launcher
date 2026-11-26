package com.panel.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.launcher.LauncherViewModel
import com.panel.launcher.data.AppEntry
import com.panel.launcher.data.Folder

/** Folder on the home grid: a rounded tile with up to four mini icons, and its name. */
@Composable
fun FolderVisual(vm: LauncherViewModel, folder: Folder, iconSize: Dp, showLabel: Boolean) {
    val p = LocalTiles.current
    val apps = vm.folderApps(folder).take(4)
    val shape = RoundedCornerShape(vm.prefs.iconRoundness.coerceIn(0, 50))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        Box(
            Modifier.size(iconSize).clip(shape).background(p.bg).border(0.75.dp, p.border, shape),
            contentAlignment = Alignment.Center,
        ) {
            val mini = iconSize * 0.3f
            Column(verticalArrangement = Arrangement.spacedBy(iconSize * 0.05f)) {
                apps.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(iconSize * 0.05f)) {
                        row.forEach { AppIcon(it, mini, vm.prefs.iconRoundness) }
                    }
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                folder.name, style = WallpaperText, fontSize = 11.5.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.width(iconSize + 16.dp),
            )
        }
    }
}

/** Opened folder: editable name and its apps. Tap outside or press Back to close. */
@Composable
fun BoxScope.FolderOverlay(vm: LauncherViewModel, folderId: String?, onClose: () -> Unit) {
    // Remember the last folder so it can animate closed after folderId becomes null.
    val lastId = remember { mutableStateOf<String?>(null) }
    if (folderId != null) lastId.value = folderId
    val folder = lastId.value?.let { vm.folders[it] }
    val visible = folderId != null && vm.folders[folderId] != null
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
    ) {
        if (folder == null) return@AnimatedVisibility
        val apps = vm.folderApps(folder)
        // Close once the folder dissolves (e.g. the last-but-one app was removed).
        val gone = vm.folders[folder.id] == null
        LaunchedEffect(gone) { if (gone) onClose() }
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xF2222224))
                .pointerInput(Unit) { detectTapGestures { } } // don't close when tapping inside
                .padding(20.dp),
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it; vm.renameFolder(folder.id, it.ifBlank { "Folder" }) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Color(0xFF0A84FF)),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Tap the name to rename", color = Color(0xFF8E8E93), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(GridCells.Fixed(4), modifier = Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.key }) { app ->
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalWallpaperText provides TextOnDarkWallpaper) {
                            AppCell(
                                app = app, iconSize = 54.dp, roundness = vm.prefs.iconRoundness, showLabel = true,
                                onOpen = { vm.launch(app); onClose() },
                                menu = {
                                    listOf(
                                        MenuAction("Remove from folder") { vm.removeFromFolder(folder.id, app.key) },
                                        MenuAction("App info") { vm.repo.openAppInfo(app) },
                                        MenuAction("Uninstall") { vm.repo.uninstall(app) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Add to folder…" from the app panel: pick a folder or start a new one. */
@Composable
fun FolderPickerDialog(vm: LauncherViewModel, app: AppEntry, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    val existing = vm.folders.values.toList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${app.label} to…") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(existing, key = { it.id }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { vm.addToFolder(f.id, app.key); onDismiss() }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("📁  ${f.name}", modifier = Modifier.weight(1f))
                            Text("${vm.folderApps(f).size}", color = Color(0xFF8E8E93))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, singleLine = true,
                    label = { Text("New folder name") }, placeholder = { Text("e.g. Finance") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.addToFolder(null, app.key, newName.ifBlank { "Folder" })
                onDismiss()
            }) { Text("Create folder") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
