package app.srpass.kykfz01.ui.components

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.srpass.kykfz01.ui.util.hasMediaAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pseudo bucket id for the "all photos" album. */
private const val ALL_PHOTOS_ID = Long.MIN_VALUE

/** An album (folder) as reported by MediaStore, plus the synthetic "all photos" entry. */
data class MediaAlbum(
    val id: Long,
    val name: String,
    val count: Int,
    val cover: Uri?
)

/** A single image inside an album. */
private data class MediaPhoto(val uri: Uri)

// ---------------------------------------------------------------------------
// MediaStore access
// ---------------------------------------------------------------------------

/** Loads every available album, most populated first, with "全部照片" pinned on top. */
private suspend fun loadAlbums(context: Context): List<MediaAlbum> = withContext(Dispatchers.IO) {
    val buckets = LinkedHashMap<Long, MutableAlbum>()
    var total = 0
    var totalCover: Uri? = null

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH
    )
    // Newest first, so the first row we meet per bucket is also its freshest cover.
    val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sort
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = c.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val relCol = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val bucketId = if (bucketCol >= 0) c.getLong(bucketCol) else 0L
                val uri =
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                total++
                if (totalCover == null) totalCover = uri

                val displayName = if (nameCol >= 0) c.getString(nameCol) else null
                val relativePath = if (relCol >= 0) c.getString(relCol) else null
                val name = displayName?.takeIf { it.isNotBlank() }
                    ?: relativePath?.trimEnd('/')?.substringAfterLast('/')
                        ?.takeIf { it.isNotBlank() }
                    ?: "其他"

                val album = buckets.getOrPut(bucketId) { MutableAlbum(bucketId, name) }
                album.count++
                if (album.cover == null) album.cover = uri
            }
        }
    }

    val result = ArrayList<MediaAlbum>(buckets.size + 1)
    result.add(MediaAlbum(ALL_PHOTOS_ID, "全部照片", total, totalCover))
    buckets.values.sortedByDescending { it.count }.forEach {
        result.add(MediaAlbum(it.id, it.name, it.count, it.cover))
    }
    result
}

private class MutableAlbum(val id: Long, val name: String) {
    var count = 0
    var cover: Uri? = null
}

/** Loads the images of one album (or all of them for [ALL_PHOTOS_ID]), newest first. */
private suspend fun loadPhotos(context: Context, albumId: Long): List<MediaPhoto> =
    withContext(Dispatchers.IO) {
        val result = ArrayList<MediaPhoto>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val selection = if (albumId == ALL_PHOTOS_ID) null
        else "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val args = if (albumId == ALL_PHOTOS_ID) null else arrayOf(albumId.toString())

        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                sort
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    result.add(
                        MediaPhoto(
                            ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                        )
                    )
                }
            }
        }
        result
    }

// ---------------------------------------------------------------------------
// Thumbnails
// ---------------------------------------------------------------------------

/**
 * In-memory thumbnail cache sized by bytes, not by entry count, so a long grid
 * cannot flush itself on every scroll pass (which is what makes thumbnails
 * reload and flicker while flinging).
 */
private object ThumbCache {
    private const val MAX_BYTES = 32 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun get(uri: Uri): Bitmap? = cache.get(uri.toString())
    fun put(uri: Uri, bitmap: Bitmap) = cache.put(uri.toString(), bitmap)
}

/**
 * Thumbnail decodes are capped at a handful at a time. Letting every grid cell
 * fire its own coroutine floods MediaProvider with concurrent requests, which
 * both starves the scroll and makes individual loads time out.
 */
private val thumbDispatcher = Dispatchers.IO.limitedParallelism(4)

/**
 * Loads a MediaStore thumbnail, showing a placeholder until it arrives.
 *
 * Note the explicit [CancellationException] rethrow: a plain runCatching would
 * swallow the cancellation that happens when a cell scrolls off screen, and the
 * load would then be recorded as a permanent failure.
 */
@Composable
fun MediaThumbnail(
    uri: Uri?,
    modifier: Modifier = Modifier,
    size: Size = Size(320, 320)
) {
    val context = LocalContext.current
    var bmp by remember(uri) { mutableStateOf(uri?.let { ThumbCache.get(it) }) }

    LaunchedEffect(uri, size.width, size.height) {
        if (uri == null || bmp != null) return@LaunchedEffect
        val loaded = withContext(thumbDispatcher) {
            try {
                context.contentResolver.loadThumbnail(uri, size, null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }
        if (loaded != null) {
            ThumbCache.put(uri, loaded)
            bmp = loaded
        }
    }

    val bitmap = bmp
    if (bitmap != null) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

// ---------------------------------------------------------------------------
// The picker
// ---------------------------------------------------------------------------

/**
 * Full-height, album-aware photo picker: a dropdown album switcher on top, an
 * adaptive photo grid with numbered selection badges in the middle and a confirm
 * bar at the bottom. This mirrors what WeChat / Google Photos / the system photo
 * picker do, instead of dumping the whole MediaStore into one flat list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerSheet(
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var albums by remember { mutableStateOf<List<MediaAlbum>>(emptyList()) }
    var currentAlbum by remember { mutableStateOf<MediaAlbum?>(null) }
    var photos by remember { mutableStateOf<List<MediaPhoto>>(emptyList()) }
    var loadingAlbums by remember { mutableStateOf(true) }
    var loadingPhotos by remember { mutableStateOf(true) }
    var albumMenuOpen by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Uri>() }
    val gridState = rememberLazyGridState()

    // Full access (or Android 14 partial access, where the user hand-picked
    // the photos we are allowed to see -- we can still show what is visible).
    // API 30~32 reads via READ_EXTERNAL_STORAGE, see MediaPermissionCompat.
    val hasFullAccess = remember { hasMediaAccess(context) }

    LaunchedEffect(Unit) {
        loadingAlbums = true
        // try/finally so a failed MediaStore query can never leave the sheet
        // stuck on the spinner forever.
        try {
            val loaded = loadAlbums(context)
            albums = loaded
            currentAlbum = loaded.firstOrNull()
        } finally {
            loadingAlbums = false
        }
    }

    LaunchedEffect(currentAlbum?.id) {
        val id = currentAlbum?.id ?: return@LaunchedEffect
        loadingPhotos = true
        try {
            photos = loadPhotos(context, id)
        } finally {
            loadingPhotos = false
        }
        // Reset to the top so a new album does not inherit the previous scroll
        // offset. This must be the non-suspending variant: scrollToItem() blocks
        // until the grid has been laid out, which never happens on the first run
        // (the grid is not composed yet -- we are showing the spinner), so the
        // coroutine would stall here and never reach the lines above.
        gridState.requestScrollToItem(0)
    }

    fun toggle(uri: Uri) {
        if (!selected.remove(uri)) selected.add(uri)
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    BackHandler(enabled = albumMenuOpen) { albumMenuOpen = false }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // ---- Header ---------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "选择图片",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            // ---- Album switcher -------------------------------------------
            AlbumSwitcher(
                album = currentAlbum,
                expanded = albumMenuOpen,
                onClick = { albumMenuOpen = !albumMenuOpen }
            )

            if (!hasFullAccess) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "当前只能访问你选择的照片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { openAppSettings() }) { Text("管理") }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ---- Grid + album dropdown -------------------------------------
            AlbumContent(
                modifier = Modifier.weight(1f),
                gridState = gridState,
                albums = albums,
                photos = photos,
                loading = loadingAlbums || loadingPhotos,
                selected = selected,
                albumMenuOpen = albumMenuOpen,
                currentAlbumId = currentAlbum?.id,
                onToggle = { toggle(it) },
                onPickAlbum = { album ->
                    currentAlbum = album
                    albumMenuOpen = false
                },
                onCloseMenu = { albumMenuOpen = false }
            )

            // ---- Confirm bar ------------------------------------------------
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (selected.isEmpty()) "未选择图片" else "已选择 ${selected.size} 张",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onConfirm(selected.toList()) },
                    enabled = selected.isNotEmpty()
                ) {
                    Text(if (selected.isEmpty()) "插入" else "插入 (${selected.size})")
                }
            }
        }
    }
}

/**
 * Eats the over-scroll and the leftover fling velocity that a scrollable leaves
 * behind once it hits an edge.
 *
 * Both are handed up to the parent [androidx.compose.material3.ModalBottomSheet]
 * otherwise, which owns the drag-to-dismiss gesture -- the panel then slides and
 * bounces under the finger at the end of the list. The only exception is pulling
 * down while already at the top, which stays wired to drag-to-dismiss.
 */
@Composable
private fun rememberOverScrollGuard(
    state: Any,
    canScrollBackward: () -> Boolean
): NestedScrollConnection = remember(state) {
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset = if (available.y > 0f && !canScrollBackward()) {
            Offset.Zero // top edge, pulling down: keep drag-to-dismiss alive
        } else {
            available.copy(x = 0f) // every other edge: swallow it
        }

        override suspend fun onPostFling(
            consumed: Velocity,
            available: Velocity
        ): Velocity = if (available.y > 0f && !canScrollBackward()) {
            Velocity.Zero
        } else {
            available
        }
    }
}

@Composable
private fun AlbumContent(
    modifier: Modifier,
    gridState: LazyGridState,
    albums: List<MediaAlbum>,
    photos: List<MediaPhoto>,
    loading: Boolean,
    selected: List<Uri>,
    albumMenuOpen: Boolean,
    currentAlbumId: Long?,
    onToggle: (Uri) -> Unit,
    onPickAlbum: (MediaAlbum) -> Unit,
    onCloseMenu: () -> Unit
) {
    val overScrollGuard = rememberOverScrollGuard(gridState) { gridState.canScrollBackward }

    Box(modifier.fillMaxWidth()) {
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            photos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "该相册暂无图片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                val orderOf = selected.withIndex().associate { it.value to it.index + 1 }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(overScrollGuard)
                ) {
                    items(photos, key = { it.uri.toString() }) { photo ->
                        PhotoCell(
                            photo = photo,
                            order = orderOf[photo.uri] ?: 0,
                            onClick = { onToggle(photo.uri) }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = albumMenuOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AlbumListPanel(
                albums = albums,
                currentId = currentAlbumId,
                onPick = onPickAlbum,
                onDismiss = onCloseMenu
            )
        }
    }
}

@Composable
private fun AlbumSwitcher(album: MediaAlbum?, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                album?.name ?: "加载中…",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${album?.count ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhotoCell(
    photo: MediaPhoto,
    order: Int,
    onClick: () -> Unit
) {
    val selected = order > 0
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        MediaThumbnail(photo.uri, Modifier.fillMaxSize())
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .border(
                    1.5.dp,
                    if (selected) primary else Color.White.copy(alpha = 0.85f),
                    CircleShape
                )
                .background(if (selected) primary else Color.Black.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(
                    order.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun AlbumListPanel(
    albums: List<MediaAlbum>,
    currentId: Long?,
    onPick: (MediaAlbum) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val overScrollGuard = rememberOverScrollGuard(listState) { listState.canScrollBackward }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(overScrollGuard)
            ) {
                items(albums, key = { it.id }) { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(album) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MediaThumbnail(
                            album.cover,
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            size = Size(200, 200)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                album.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${album.count} 张",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (album.id == currentId) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (album.id != albums.lastOrNull()?.id) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 84.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}
