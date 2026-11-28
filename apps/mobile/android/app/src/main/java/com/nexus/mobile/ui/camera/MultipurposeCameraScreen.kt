package com.nexus.mobile.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.nexus.mobile.ui.components.FzLimeAccent
import com.nexus.mobile.ui.components.NexusCircularButton
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.nexusSpecularBorder

enum class CameraMode(val label: String, val icon: ImageVector, val hint: String) {
    QR_PAIR("QR / Pair", Icons.Rounded.QrCodeScanner, "Scan Nexus Device Flow QR or Wi-Fi code"),
    RECEIPT("Receipt", Icons.Rounded.DocumentScanner, "Capture receipt -> Daily Budget (Dummy)"),
    AI_VISION("AI Vision", Icons.Rounded.AutoAwesome, "Snap frame -> Ask AI Chat (Dummy)"),
    VAULT("Vault", Icons.Rounded.SaveAlt, "Quick capture -> Nexus Storage (Dummy)"),
}

@Composable
fun MultipurposeCameraScreen(
    onClose: () -> Unit,
    onOpenAiChat: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalNexusPalette.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var activeMode by remember { mutableStateOf(CameraMode.QR_PAIR) }
    var captureFeedback by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasPermission) {
            Camera2PreviewView(modifier = Modifier.fillMaxSize())
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission required", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(999.dp),
                        color = FzLimeAccent,
                    ) {
                        Text(
                            text = "Grant Camera Access",
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NexusCircularButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Close Camera",
                onClick = onClose,
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = nexusSpecularBorder(true, 0.75f),
            ) {
                Text(
                    text = activeMode.label,
                    color = FzLimeAccent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.size(40.dp))
        }

        // Bottom controls & mode switcher
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.7f),
                border = nexusSpecularBorder(true, 0.75f),
            ) {
                Text(
                    text = captureFeedback ?: activeMode.hint,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            // Shutter button
            Surface(
                onClick = {
                    captureFeedback = when (activeMode) {
                        CameraMode.QR_PAIR -> "QR Scanner ready (pair via Auth / Devices)"
                        CameraMode.RECEIPT -> "Receipt captured -> Ready for Daily Budget (Dummy)"
                        CameraMode.AI_VISION -> {
                            onOpenAiChat()
                            "Opening AI Chat..."
                        }
                        CameraMode.VAULT -> "Saved to Nexus Vault (Dummy)"
                    }
                },
                shape = CircleShape,
                color = FzLimeAccent,
                modifier = Modifier.size(66.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = "Capture",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // 4-mode glass pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = palette.glassSurface.copy(alpha = 0.85f),
                border = nexusSpecularBorder(true, 0.75f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CameraMode.entries.forEach { mode ->
                        val selected = mode == activeMode
                        Surface(
                            onClick = {
                                activeMode = mode
                                captureFeedback = null
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) FzLimeAccent.copy(alpha = 0.2f) else Color.Transparent,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    mode.icon,
                                    contentDescription = mode.label,
                                    tint = if (selected) FzLimeAccent else Color.White,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) FzLimeAccent else Color.White,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun Camera2PreviewView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraDevice?.close()
            cameraDevice = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
                        val backId = manager.cameraIdList.firstOrNull { id ->
                            manager.getCameraCharacteristics(id)
                                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                        } ?: manager.cameraIdList.firstOrNull() ?: return

                        runCatching {
                            manager.openCamera(backId, object : CameraDevice.StateCallback() {
                                override fun onOpened(camera: CameraDevice) {
                                    cameraDevice = camera
                                    val previewSurface = Surface(surface)
                                    val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        addTarget(previewSurface)
                                    }
                                    @Suppress("DEPRECATION")
                                    camera.createCaptureSession(
                                        listOf(previewSurface),
                                        object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                runCatching {
                                                    session.setRepeatingRequest(req.build(), null, null)
                                                }
                                            }
                                            override fun onConfigureFailed(session: CameraCaptureSession) = Unit
                                        },
                                        null,
                                    )
                                }
                                override fun onDisconnected(camera: CameraDevice) {
                                    camera.close()
                                }
                                override fun onError(camera: CameraDevice, error: Int) {
                                    camera.close()
                                }
                            }, null)
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
    )
}
