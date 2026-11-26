package com.panel.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveCircleOutline
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
    val shape = LocalIconLook.current.shape
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        val record = LocalIconBounds.current
        Box(
            Modifier
                .size(iconSize)
                .onGloballyPositioned { c ->
                    val r = c.boundsInWindow()
                    record(com.panel.launcher.FOLDER_PREFIX + folder.id,
                        android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()))
                }
                .surface(shape, p),
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

/**
 * Opened folder: it grows out of the folder icon, with an editable name and its apps over the
 * blurred home screen. Tap outside or press Back to close.
 */
@Composable
fun BoxScope.FolderOverlay(vm: LauncherViewModel, folderId: String?, onClose: () -> Unit) {
    // Remember the last folder so it can animate closed after folderId becomes null.
    val lastId = remember { mutableStateOf<String?>(null) }
    if (folderId != null) lastId.value = folderId
    val folder = lastId.value?.let { vm.folders[it] }
    val visible = folderId != null && vm.folders[folderId] != null
    val t by animateFloatAsState(
        if (visible) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 420f), label = "folder",
    )
    if (folder == null || t <= 0.01f) return
    val gone = vm.folders[folder.id] == null
    LaunchedEffect(gone) { if (gone) onClose() }

    val apps = vm.folderApps(folder)
    val origin = vm.iconBounds[com.panel.launcher.FOLDER_PREFIX + folder.id]
    val view = LocalView.current
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = t.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.25f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    )
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val shape = RoundedCornerShape(36.dp)
    Column(
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.86f)
            .widthIn(max = 400.dp)
            .graphicsLayer {
                alpha = t.coerceIn(0f, 1f)
                val sc = 0.25f + 0.75f * t
                scaleX = sc; scaleY = sc
                if (origin != null) {
                    translationX = (origin.exactCenterX() - view.width / 2f) * (1f - t)
                    translationY = (origin.exactCenterY() - view.height / 2f) * (1f - t)
                }
            }
            .shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xD92A2A2E))
            .border(0.75.dp, Color(0x1FFFFFFF), shape)
            .pointerInput(Unit) { detectTapGestures { } } // taps inside don't close it
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp),
    ) {
        BasicTextField(
            value = name,
            onValueChange = { name = it; vm.renameFolder(folder.id, it.ifBlank { "Folder" }) },
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                fontFamily = PixelDisplay,
            ),
            cursorBrush = SolidColor(Color(0xFF0A84FF)),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(GridCells.Fixed(4), modifier = Modifier.heightIn(max = 440.dp)) {
            items(apps, key = { it.key }) { app ->
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalWallpaperText provides TextOnDarkWallpaper) {
                        AppCell(
                            app = app, iconSize = 56.dp, roundness = vm.prefs.iconRoundness, showLabel = true,
                            onOpen = { vm.launch(app); onClose() },
                            menu = {
                                listOf(
                                    MenuAction("Remove from folder", Icons.Outlined.RemoveCircleOutline) { vm.removeFromFolder(folder.id, app.key) },
                                    MenuAction("App info", Icons.Outlined.Info) { vm.repo.openAppInfo(app) },
                                    MenuAction("Uninstall", Icons.Outlined.Delete, destructive = true) { vm.repo.uninstall(app) },
                                )
                            },
                        )
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
