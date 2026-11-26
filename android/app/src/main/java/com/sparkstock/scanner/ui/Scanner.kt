package com.sparkstock.scanner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sparkstock.scanner.R
import com.sparkstock.scanner.data.TagParser
import java.util.concurrent.Executors

/**
 * The camera, reading SparkStock's QR labels continuously. Each label is reported once (the same one again only after
 * a pause), with a beep and a buzz; QR codes that aren't SparkStock labels are reported with onInvalid. Below it, a
 * field to type the tag when a label is damaged.
 */
@Composable
fun QrScanner(
    onTag: (String) -> Unit,
    onInvalid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.1f).clip(RoundedCornerShape(16.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                CameraPreview(onTag, onInvalid)
            } else {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.camera_needed), color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    if (!asked) {
                        Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow_camera)) }
                    } else {
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }) { Text(stringResource(R.string.open_settings)) }
                    }
                }
            }
        }
        ManualTag(onTag, onInvalid)
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onTag: (String) -> Unit, onInvalid: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val currentOnTag by rememberUpdatedState(onTag)
    val currentOnInvalid by rememberUpdatedState(onInvalid)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val beep = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    // the last code read and when, so holding the label in front of the camera doesn't repeat it
    val last = remember { arrayOf<String?>(null) }
    val lastAt = remember { longArrayOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            scanner.close()
            executor.shutdown()
            beep?.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy ->
                        val media = proxy.image
                        if (media == null) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { codes ->
                                val raw = codes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                                val now = System.currentTimeMillis()
                                if (raw == last[0] && now - lastAt[0] < 2500) return@addOnSuccessListener
                                last[0] = raw
                                lastAt[0] = now
                                val tag = TagParser.parse(raw)
                                if (tag == null) {
                                    currentOnInvalid()
                                } else {
                                    beep?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnTag(tag)
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                    runCatching {
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                view
            },
        )
        // the area to aim at
        Box(
            Modifier.align(Alignment.Center).fillMaxSize(0.62f)
                .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(20.dp)),
        )
        Text(
            stringResource(R.string.point_camera),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            FilledIconToggleButton(
                checked = torch,
                onCheckedChange = {
                    torch = it
                    camera?.cameraControl?.enableTorch(it)
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            ) {
                Icon(if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, stringResource(R.string.flashlight))
            }
        }
    }
}

@Composable
private fun ManualTag(onTag: (String) -> Unit, onInvalid: () -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        val tag = TagParser.parse(text)
        if (tag == null) onInvalid() else onTag(tag)
        text = ""
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.type_tag)) },
            placeholder = { Text(stringResource(R.string.tag_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) submit() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { submit() }, enabled = text.isNotBlank()) { Text(stringResource(R.string.add)) }
    }
}
