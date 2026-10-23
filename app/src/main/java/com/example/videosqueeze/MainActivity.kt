package com.example.videosqueeze

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VideoSqueezeApp(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
private fun VideoSqueezeApp(context: ComponentActivity) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var preset by remember { mutableStateOf(QualityPreset.SMALL) }
    var codec by remember { mutableStateOf(VideoCodec.HEVC) }
    var presetMenu by remember { mutableStateOf(false) }
    var codecMenu by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("یک ویدئو انتخاب کن تا اطلاعات و تنظیمات فشرده‌سازی نمایش داده شود.") }
    var outputInfo by remember { mutableStateOf<String?>(null) }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val progressHolder = remember { ProgressHolder() }
    val progressPoll = remember {
        object : Runnable {
            override fun run() {
                val active = transformer
                if (working && active != null) {
                    val state = active.getProgress(progressHolder)
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED) progress = progressHolder.progress.coerceIn(0, 100)
                    handler.postDelayed(this, 450)
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            outputInfo = null
            outputUri = null
            try {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                videoInfo = readVideoInfo(context, uri)
                status = "ویدئو آماده فشرده‌سازی است."
            } catch (error: Exception) {
                videoInfo = null
                status = "خواندن اطلاعات ویدئو ناموفق بود: ${error.localizedMessage ?: "خطای ناشناخته"}"
            }
        }
    }

    MaterialTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(topBar = { TopAppBar(title = { Text("Video Squeeze", fontWeight = FontWeight.Bold) }) }) { padding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("فشرده‌سازی ویدئو روی گوشی", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("آفلاین • بدون آپلود فایل • خروجی MP4", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("۱. انتخاب فایل", fontWeight = FontWeight.SemiBold)
                            Button(onClick = { if (!working) picker.launch(arrayOf("video/*")) }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                                Text(if (videoInfo == null) "انتخاب ویدئو" else "انتخاب ویدئوی دیگر")
                            }
                            videoInfo?.let { info ->
                                Text(info.displayName, fontWeight = FontWeight.SemiBold)
                                InfoRow("حجم اولیه", formatBytes(info.sizeBytes))
                                InfoRow("وضوح تصویر", info.resolutionLabel)
                                InfoRow("مدت", formatDuration(info.durationMs))
                                InfoRow("فرمت منبع", info.mimeType ?: "نامشخص")
                            }
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("۲. تنظیمات فشرده‌سازی", fontWeight = FontWeight.SemiBold)
                            ExposedDropdownMenuBox(expanded = presetMenu, onExpandedChange = { presetMenu = it }) {
                                OutlinedTextField(
                                    value = preset.title,
                                    onValueChange = {}, readOnly = true, label = { Text("سطح فشرده‌سازی") },
                                    supportingText = { Text(preset.description) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetMenu) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                                    QualityPreset.entries.forEach { item -> DropdownMenuItem(text = { Text(item.title) }, onClick = { preset = item; presetMenu = false }) }
                                }
                            }
                            ExposedDropdownMenuBox(expanded = codecMenu, onExpandedChange = { codecMenu = it }) {
                                OutlinedTextField(
                                    value = codec.title, onValueChange = {}, readOnly = true,
                                    label = { Text("کدک خروجی") },
                                    supportingText = { Text(if (codec == VideoCodec.HEVC) "معمولاً حجم کمتر؛ سازگاری دستگاه را بررسی کن." else "سازگاری گسترده‌تر با دستگاه‌ها و برنامه‌ها.") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = codecMenu) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = codecMenu, onDismissRequest = { codecMenu = false }) {
                                    VideoCodec.entries.forEach { item -> DropdownMenuItem(text = { Text(item.title) }, onClick = { codec = item; codecMenu = false }) }
                                }
                            }
                            videoInfo?.let { info ->
                                val targetBitrate = estimateVideoBitrate(info, preset)
                                val estimated = estimateOutputBytes(info, targetBitrate)
                                val reduction = reductionPercent(info.sizeBytes, estimated)
                                Text("برآورد حجم خروجی: ${if (estimated > 0) formatBytes(estimated) else "نامشخص"}", fontWeight = FontWeight.SemiBold)
                                Text("نرخ بیت درخواستی تصویر: ${targetBitrate / 1000} kbps", style = MaterialTheme.typography.bodySmall)
                                if (reduction != null) Text("کاهش تخمینی: ${reduction.coerceAtLeast(0)}٪", style = MaterialTheme.typography.bodySmall)
                                Text("این اعداد تخمینی‌اند؛ انکودر سخت‌افزاری ممکن است نرخ بیت را تغییر دهد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("۳. پردازش", fontWeight = FontWeight.SemiBold)
                            if (working) {
                                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                                Text("پیشرفت: $progress٪")
                                OutlinedButton(onClick = {
                                    transformer?.cancel()
                                    handler.removeCallbacks(progressPoll)
                                    working = false
                                    progress = 0
                                    status = "عملیات لغو شد."
                                    transformer = null
                                }, modifier = Modifier.fillMaxWidth()) { Text("لغو فشرده‌سازی") }
                            } else {
                                Button(
                                    onClick = {
                                        val uri = selectedUri
                                        val info = videoInfo
                                        if (uri == null || info == null) {
                                            status = "ابتدا یک ویدئو انتخاب کن."
                                        } else {
                                            startCompression(
                                                context = context,
                                                uri = uri,
                                                info = info,
                                                preset = preset,
                                                codec = codec,
                                                onStarted = { active ->
                                                    transformer = active
                                                    working = true
                                                    progress = 0
                                                    outputInfo = null
                                                    outputUri = null
                                                    status = "فشرده‌سازی در حال اجراست…"
                                                    handler.post(progressPoll)
                                                },
                                                onSuccess = { savedUri, outputBytes ->
                                                    handler.removeCallbacks(progressPoll)
                                                    working = false
                                                    progress = 100
                                                    transformer = null
                                                    outputUri = savedUri
                                                    val reduction = reductionPercent(info.sizeBytes, outputBytes)
                                                    outputInfo = "حجم نهایی: ${formatBytes(outputBytes)}${reduction?.let { " • کاهش حجم: $it٪" } ?: ""}"
                                                    status = "فشرده‌سازی با موفقیت تمام شد."
                                                },
                                                onFailure = { message ->
                                                    handler.removeCallbacks(progressPoll)
                                                    working = false
                                                    transformer = null
                                                    status = "فشرده‌سازی ناموفق بود: $message"
                                                }
                                            )
                                        }
                                    },
                                    enabled = !working && videoInfo != null,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("شروع فشرده‌سازی") }
                            }
                            Text(status, style = MaterialTheme.typography.bodyMedium)
                            outputInfo?.let { Text(it, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            outputUri?.let { uri ->
                                OutlinedButton(onClick = { shareOutput(context, uri) }, modifier = Modifier.fillMaxWidth()) { Text("اشتراک‌گذاری فایل خروجی") }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("نکته: برای حجم کمتر معمولاً H.265 مناسب است؛ اگر گوشی از رمزگذاری HEVC پشتیبانی نکند، ممکن است عملیات خطا بدهد. نسخه فعلی برای فایل‌های محلی و خروجی MP4 طراحی شده است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun readVideoInfo(context: Context, uri: Uri): VideoInfo {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        val name = queryDisplayName(context, uri) ?: "video_${System.currentTimeMillis()}"
        val size = querySize(context, uri)
        return VideoInfo(name, size, duration, width, height, bitrate, mime)
    } finally {
        runCatching { retriever.release() }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

private fun querySize(context: Context, uri: Uri): Long = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    } ?: 0L
}.getOrDefault(0L)

@OptIn(UnstableApi::class)
private fun startCompression(
    context: ComponentActivity,
    uri: Uri,
    info: VideoInfo,
    preset: QualityPreset,
    codec: VideoCodec,
    onStarted: (Transformer) -> Unit,
    onSuccess: (Uri, Long) -> Unit,
    onFailure: (String) -> Unit
) {
    val tempDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
    val tempFile = File(tempDirectory, "squeezed_${System.currentTimeMillis()}.mp4")
    val targetBitrate = estimateVideoBitrate(info, preset)
    val ioExecutor = Executors.newSingleThreadExecutor()
    val mainHandler = Handler(Looper.getMainLooper())
    val encoderFactory = DefaultEncoderFactory.Builder(context)
        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(targetBitrate).build())
        .build()
    val listener = object : Transformer.Listener {
        override fun onCompleted(composition: Composition, result: ExportResult) {
            // Copying a large video can take seconds or minutes; never do it on the UI thread.
            ioExecutor.execute {
                try {
                    if (!tempFile.exists() || tempFile.length() <= 0L) throw IllegalStateException("فایل خروجی ساخته نشد یا خالی است")
                    val outputUri = saveToMediaLibrary(context, tempFile, info.displayName)
                    val outputSize = if (Build.VERSION.SDK_INT >= 29) {
                        context.contentResolver.openAssetFileDescriptor(outputUri, "r")?.use { it.length }?.takeIf { it > 0 } ?: tempFile.length()
                    } else tempFile.length()
                    tempFile.delete()
                    mainHandler.post { onSuccess(outputUri, outputSize) }
                } catch (error: Exception) {
                    mainHandler.post { onFailure(error.localizedMessage ?: "ذخیره فایل خروجی ناموفق بود") }
                } finally {
                    ioExecutor.shutdown()
                }
            }
        }

        override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
            tempFile.delete()
            ioExecutor.shutdownNow()
            mainHandler.post { onFailure(exception.localizedMessage ?: "خطای موتور فشرده‌سازی") }
        }
    }

    try {
        val instance = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .setVideoMimeType(codec.mimeType)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()
        onStarted(instance)
        instance.start(EditedMediaItem.Builder(MediaItem.fromUri(uri)).build(), tempFile.absolutePath)
    } catch (error: Exception) {
        tempFile.delete()
        ioExecutor.shutdownNow()
        onFailure(error.localizedMessage ?: "شروع فشرده‌سازی ممکن نشد")
    }
}

private fun saveToMediaLibrary(context: Context, source: File, originalName: String): Uri {
    val baseName = originalName.substringBeforeLast('.', originalName).replace(Regex("[^A-Za-z0-9_ -]"), "_").take(70).ifBlank { "video" }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val name = "${baseName}_compressed_$stamp.mp4"
    return if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoSqueeze")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("ساخت فایل در گالری ناموفق بود")
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output -> FileInputStream(source).use { input -> input.copyTo(output) } }
                ?: throw IllegalStateException("باز کردن فایل خروجی ناموفق بود")
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
            uri
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    } else {
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "VideoSqueeze").apply { mkdirs() }
        val target = File(folder, name)
        FileInputStream(source).use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }
}

private fun shareOutput(context: Context, uri: Uri) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "اشتراک‌گذاری ویدئو"))
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "نامشخص"
    val total = durationMs / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}
