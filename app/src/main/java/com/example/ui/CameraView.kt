package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.util.ImageStorageHelper
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private const val CAMERA_VIEW_TAG = "DocFolioCameraView"

/**
 * Full-screen CameraX document scanner with Accompanist runtime CAMERA permission handling,
 * framing guides, flash control, tap-to-focus, and a zero-permission Gallery fallback.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraView(
    onPhotoCaptured: (Uri) -> Unit,
    onPickFromGallery: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var hasRequestedOnce by rememberSaveable { mutableStateOf(false) }

    if (cameraPermissionState.status.isGranted) {
        CameraCaptureSurface(
            onPhotoCaptured = onPhotoCaptured,
            onPickFromGallery = onPickFromGallery,
            onClose = onClose
        )
    } else {
        val permanentlyDenied = hasRequestedOnce && !cameraPermissionState.status.shouldShowRationale
        CameraPermissionScreen(
            permanentlyDenied = permanentlyDenied,
            onGrantClick = {
                if (permanentlyDenied) {
                    val settingsIntent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(settingsIntent) }
                } else {
                    hasRequestedOnce = true
                    cameraPermissionState.launchPermissionRequest()
                }
            },
            onPickFromGallery = onPickFromGallery,
            onClose = onClose
        )
    }
}

@Composable
private fun CameraPermissionScreen(
    permanentlyDenied: Boolean,
    onGrantClick: () -> Unit,
    onPickFromGallery: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            FilledIconButton(
                onClick = onClose,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .minimumInteractiveComponentSize()
                    .testTag("camera_permission_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.camera_close_button)
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.camera_permission_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (permanentlyDenied) {
                            stringResource(R.string.camera_permission_denied)
                        } else {
                            stringResource(R.string.camera_permission_rationale)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onGrantClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("camera_permission_request_button")
                    ) {
                        Text(
                            text = if (permanentlyDenied) {
                                stringResource(R.string.camera_permission_settings)
                            } else {
                                stringResource(R.string.camera_permission_grant)
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = onPickFromGallery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("camera_permission_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.camera_open_gallery))
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureSurface(
    onPhotoCaptured: (Uri) -> Unit,
    onPickFromGallery: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var useFrontCamera by rememberSaveable { mutableStateOf(false) }
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var canSwitchCamera by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var isViewActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { isViewActive = false }
    }

    // Update flash mode on the active ImageCapture use case without rebinding the camera
    LaunchedEffect(imageCaptureRef, flashMode) {
        imageCaptureRef?.flashMode = flashMode
    }

    // Auto-hide tap-to-focus indicator ring
    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(750L)
            focusTapOffset = null
        }
    }

    // Bind CameraX Preview + ImageCapture and guarantee unbindAll() on dispose
    DisposableEffect(lifecycleOwner, previewView, useFrontCamera) {
        var isDisposed = false
        var activeProvider: ProcessCameraProvider? = null
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        providerFuture.addListener(
            {
                if (isDisposed) return@addListener
                try {
                    val cameraProvider = providerFuture.get()
                    activeProvider = cameraProvider

                    val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    canSwitchCamera = hasBack && hasFront

                    val selector = when {
                        useFrontCamera && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                        hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                        hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> {
                            cameraError = context.getString(R.string.camera_unavailable_body)
                            return@addListener
                        }
                    }

                    val preview = Preview.Builder()
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setFlashMode(flashMode)
                        .build()

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture
                    )

                    boundCamera = camera
                    imageCaptureRef = imageCapture
                    hasFlashUnit = camera.cameraInfo.hasFlashUnit()
                    cameraError = null
                } catch (e: Exception) {
                    Log.e(CAMERA_VIEW_TAG, "Failed to bind CameraX use cases", e)
                    cameraError = e.localizedMessage ?: context.getString(R.string.camera_unavailable_body)
                }
            },
            mainExecutor
        )

        onDispose {
            isDisposed = true
            boundCamera = null
            imageCaptureRef = null
            runCatching { activeProvider?.unbindAll() }
        }
    }

    val scrimColor = MaterialTheme.colorScheme.scrim
    val reticleColor = MaterialTheme.colorScheme.tertiary
    val onOverlayColor = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor)
    ) {
        if (cameraError != null) {
            CameraUnavailableFallback(
                message = cameraError ?: stringResource(R.string.camera_unavailable_body),
                onPickFromGallery = onPickFromGallery,
                onClose = onClose
            )
            return@Box
        }

        // CameraX PreviewView with tap-to-focus
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .testTag("camerax_preview_view")
                .pointerInput(boundCamera) {
                    detectTapGestures { offset ->
                        focusTapOffset = offset
                        val camera = boundCamera ?: return@detectTapGestures
                        runCatching {
                            val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(point)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            camera.cameraControl.startFocusAndMetering(action)
                        }
                    }
                }
        )

        // Document alignment reticle + tap-to-focus ring drawn dynamically from Canvas DrawScope size
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameWidth = size.width * 0.84f
            val frameHeight = (frameWidth * 1.35f).coerceAtMost(size.height * 0.60f)
            val left = (size.width - frameWidth) / 2f
            val top = (size.height - frameHeight) / 2f
            val right = left + frameWidth
            val bottom = top + frameHeight
            val cornerLen = (frameWidth * 0.09f).coerceIn(24.dp.toPx(), 44.dp.toPx())
            val strokePx = 3.dp.toPx()

            // Subtle corner guide brackets for A4 sheets & requisition slips
            drawLine(reticleColor, Offset(left, top), Offset(left + cornerLen, top), strokePx, StrokeCap.Round)
            drawLine(reticleColor, Offset(left, top), Offset(left, top + cornerLen), strokePx, StrokeCap.Round)

            drawLine(reticleColor, Offset(right, top), Offset(right - cornerLen, top), strokePx, StrokeCap.Round)
            drawLine(reticleColor, Offset(right, top), Offset(right, top + cornerLen), strokePx, StrokeCap.Round)

            drawLine(reticleColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokePx, StrokeCap.Round)
            drawLine(reticleColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokePx, StrokeCap.Round)

            drawLine(reticleColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokePx, StrokeCap.Round)
            drawLine(reticleColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokePx, StrokeCap.Round)

            // Faint inner border
            drawRect(
                color = onOverlayColor.copy(alpha = 0.22f),
                topLeft = Offset(left, top),
                size = Size(frameWidth, frameHeight),
                style = Stroke(width = 1.dp.toPx())
            )

            // Tap-to-focus indicator ring
            focusTapOffset?.let { tap ->
                drawCircle(
                    color = reticleColor,
                    radius = 28.dp.toPx(),
                    center = tap,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Top Bar: Close, Title Pill, Flash Toggle
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scrimColor.copy(alpha = 0.55f),
                        contentColor = onOverlayColor
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("camera_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.camera_close_button)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = scrimColor.copy(alpha = 0.58f)
                ) {
                    Text(
                        text = stringResource(R.string.camera_scan_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = onOverlayColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (hasFlashUnit) {
                    val (flashIcon, flashDesc) = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn to stringResource(R.string.camera_flash_on)
                        ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff to stringResource(R.string.camera_flash_off)
                        else -> Icons.Default.FlashAuto to stringResource(R.string.camera_flash_auto)
                    }
                    FilledIconButton(
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                else -> ImageCapture.FLASH_MODE_AUTO
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = scrimColor.copy(alpha = 0.55f),
                            contentColor = onOverlayColor
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("camera_flash_button")
                    ) {
                        Icon(imageVector = flashIcon, contentDescription = flashDesc)
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = scrimColor.copy(alpha = 0.52f)
            ) {
                Text(
                    text = stringResource(R.string.camera_align_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = onOverlayColor.copy(alpha = 0.92f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            AnimatedVisibility(
                visible = captureError != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = captureError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Bottom Controls: Gallery shortcut, Primary Shutter Button, Lens Switch
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = scrimColor.copy(alpha = 0.68f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onPickFromGallery,
                    enabled = !isCapturing,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = onOverlayColor.copy(alpha = 0.16f),
                        contentColor = onOverlayColor
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("camera_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = stringResource(R.string.camera_open_gallery)
                    )
                }

                // Tactile Shutter Capture Button
                val captureContentDescription = stringResource(R.string.camera_capture_button)
                Surface(
                    onClick = {
                        val imageCapture = imageCaptureRef ?: return@Surface
                        if (isCapturing) return@Surface
                        isCapturing = true
                        captureError = null

                        val capturePair = runCatching {
                            ImageStorageHelper.createTempCaptureFile(context)
                        }.getOrElse { err ->
                            isCapturing = false
                            captureError = err.localizedMessage ?: "Unable to prepare capture file."
                            return@Surface
                        }

                        val (tempFile, fileUri) = capturePair
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    if (!isViewActive) return
                                    isCapturing = false
                                    onPhotoCaptured(fileUri)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e(CAMERA_VIEW_TAG, "Photo capture failed", exception)
                                    runCatching { tempFile.delete() }
                                    if (!isViewActive) return
                                    isCapturing = false
                                    captureError = exception.localizedMessage
                                        ?: "Capture failed. Please hold steady and try again."
                                }
                            }
                        )
                    },
                    enabled = !isCapturing && imageCaptureRef != null,
                    shape = CircleShape,
                    color = onOverlayColor.copy(alpha = 0.22f),
                    border = BorderStroke(3.dp, onOverlayColor),
                    modifier = Modifier
                        .size(76.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = captureContentDescription
                        }
                        .testTag("camera_capture_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                color = onOverlayColor,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(30.dp)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = onOverlayColor,
                                modifier = Modifier.size(58.dp)
                            ) {}
                        }
                    }
                }

                if (canSwitchCamera) {
                    FilledIconButton(
                        onClick = { useFrontCamera = !useFrontCamera },
                        enabled = !isCapturing,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = onOverlayColor.copy(alpha = 0.16f),
                            contentColor = onOverlayColor
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("camera_switch_lens_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = stringResource(R.string.camera_switch_lens)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(52.dp))
                }
            }
        }
    }
}

@Composable
private fun CameraUnavailableFallback(
    message: String,
    onPickFromGallery: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            FilledIconButton(
                onClick = onClose,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .minimumInteractiveComponentSize()
                    .testTag("camera_unavailable_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.camera_close_button)
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NoPhotography,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = stringResource(R.string.camera_unavailable_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onPickFromGallery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("camera_unavailable_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.camera_open_gallery))
                    }
                }
            }
        }
    }
}
