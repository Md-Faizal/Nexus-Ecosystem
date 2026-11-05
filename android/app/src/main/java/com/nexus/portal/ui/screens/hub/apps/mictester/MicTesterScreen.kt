package com.nexus.portal.ui.screens.hub.apps.mictester

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nexus.portal.ui.components.TechGridBackground
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicTesterScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    
    // Check permission state dynamically
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121216))
    ) {
        TechGridBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Vocal Studio & Mic Test",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!hasRecordPermission) {
                PermissionRequiredView(onGrantClick = {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                })
            } else {
                MicTesterDashboardView()
            }
        }
    }
}

@Composable
fun PermissionRequiredView(onGrantClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22222E).copy(alpha = 0.9f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Red.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.MicOff,
                    contentDescription = null,
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Audio Permission Required",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To test microphone loopbacks, measure voice frequencies, and enjoy real-time karaoke DSP, please grant mic record access.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Grant Record Access", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicTesterDashboardView() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Instantiate Singletons inside view composition
    val loopbackEngine = remember { MicAudioManager.LoopbackEngine() }
    val clipRecorder = remember { MicAudioManager.VoiceClipRecorder() }
    val synthPlayer = remember { MicAudioManager.SynthBackingTrackPlayer() }

    // Hardware source listings
    var availableMics by remember { mutableStateOf(listOf<MicSource>()) }
    var selectedMic by remember { mutableStateOf<MicSource?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // UI States
    var isLoopbackOn by remember { mutableStateOf(false) }
    var isSynthOn by remember { mutableStateOf(false) }
    
    var echoDelay by remember { mutableFloatStateOf(250f) }
    var echoDecay by remember { mutableFloatStateOf(0.40f) }
    
    var recordingState by remember { mutableStateOf("IDLE") } // IDLE, RECORDING, PLAYING
    
    val micLevel by loopbackEngine.amplitudeFlow.collectAsState()

    val recordFile = remember { File(context.cacheDir, "mic_test_voice.mp3").absolutePath }

    // Fetch hardware inputs on launch
    LaunchedEffect(Unit) {
        availableMics = MicAudioManager.getAvailableMicrophones(context)
        selectedMic = availableMics.firstOrNull()
    }

    // Update settings reactively on change
    LaunchedEffect(echoDelay, echoDecay, selectedMic) {
        loopbackEngine.echoDelayMs = echoDelay.toInt()
        loopbackEngine.echoDecay = echoDecay
        loopbackEngine.inputDevice = selectedMic?.info
    }

    // Stop all audio on Composable dispose
    DisposableEffect(Unit) {
        onDispose {
            loopbackEngine.stop()
            clipRecorder.stopRecording(context)
            clipRecorder.stopPlaying()
            synthPlayer.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // --- HARDWARE SOURCE DROPDOWN ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22222E).copy(alpha = 0.85f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SELECT MICROPHONE HARDWARE",
                    color = Color(0xFF00FFCC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable { isDropdownExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedMic?.name ?: "Default System Microphone",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.LightGray)
                    }

                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(Color(0xFF22222E))
                    ) {
                        availableMics.forEach { mic ->
                            DropdownMenuItem(
                                text = { Text(mic.name, color = Color.White, fontSize = 14.sp) },
                                onClick = {
                                    selectedMic = mic
                                    isDropdownExpanded = false
                                    loopbackEngine.switchDevice(context, mic.info)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- GLOWING DECIBEL SOUNDWAVE VISUALIZER ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.15f), RoundedCornerShape(16.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                SoundwaveVisualizerCanvas(amplitude = micLevel)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isLoopbackOn || recordingState == "RECORDING") Color.Green else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLoopbackOn) "LOOPBACK FEED ACTIVE" else if (recordingState == "RECORDING") "RECORDING INPUT" else "MIC INPUT STANDBY",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Peak RMS: ${(micLevel * 100).toInt()}%",
                    color = Color.DarkGray,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- REALTIME KARAOKE & ECHO CONTROLS ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22222E).copy(alpha = 0.85f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "REAL-TIME VOCAL EFFECTS & KARAOKE",
                    color = Color(0xFF00FFCC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Real-time Loopback Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Live Mic Loopback", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Use headphones to prevent squealing audio loops", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isLoopbackOn,
                        onCheckedChange = { on ->
                            isLoopbackOn = on
                            if (on) {
                                loopbackEngine.start(context)
                            } else {
                                loopbackEngine.stop()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FFCC),
                            checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.3f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(16.dp))

                // Reverb Presets
                Text("Vocal Reverb Presets", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        Triple("Normal", 0f, 0.0f),
                        Triple("Studio", 120f, 0.25f),
                        Triple("Chamber", 280f, 0.45f),
                        Triple("Large Hall", 480f, 0.70f)
                    )
                    presets.forEach { (name, delay, decay) ->
                        val isSelected = echoDelay == delay && echoDecay == decay
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF00FFCC).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                .border(1.dp, if (isSelected) Color(0xFF00FFCC) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable {
                                    echoDelay = delay
                                    echoDecay = decay
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) Color(0xFF00FFCC) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Echo Sliders
                Text("Echo Feedback Delay (${echoDelay.toInt()} ms)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = echoDelay,
                    onValueChange = { echoDelay = it },
                    valueRange = 0f..600f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00FFCC),
                        activeTrackColor = Color(0xFF00FFCC),
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Echo Feedback Decay (${(echoDecay * 100).toInt()}%)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = echoDecay,
                    onValueChange = { echoDecay = it },
                    valueRange = 0f..0.85f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00FFCC),
                        activeTrackColor = Color(0xFF00FFCC),
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(16.dp))

                // Arpeggio Synth loop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Synthwave Backing Beat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Loops dynamic cyberpunk analog chords arpeggios", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isSynthOn,
                        onCheckedChange = { on ->
                            isSynthOn = on
                            if (on) {
                                synthPlayer.start()
                            } else {
                                synthPlayer.stop()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FFCC),
                            checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.3f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- CLIP RECORDING TEST PANEL ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22222E).copy(alpha = 0.85f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .padding(bottom = 32.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "OFFLINE RECORDING & REPLAY TESTER",
                    color = Color(0xFF00FFCC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Record / Stop button
                    Button(
                        onClick = {
                            if (recordingState == "RECORDING") {
                                clipRecorder.stopRecording(context)
                                recordingState = "IDLE"
                                android.widget.Toast.makeText(context, "Voice test clip saved", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                // Close conflicting loops
                                if (isLoopbackOn) {
                                    isLoopbackOn = false
                                    loopbackEngine.stop()
                                }
                                clipRecorder.startRecording(context, recordFile, selectedMic)
                                recordingState = "RECORDING"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (recordingState == "RECORDING") Color(0xFFE57373) else Color(0xFF00FFCC)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = if (recordingState == "RECORDING") Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (recordingState == "RECORDING") "Stop Mic" else "Record Clip",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Replay / Stop Replay Button
                    Button(
                        onClick = {
                            if (recordingState == "PLAYING") {
                                clipRecorder.stopPlaying()
                                recordingState = "IDLE"
                            } else {
                                if (File(recordFile).exists()) {
                                    recordingState = "PLAYING"
                                    clipRecorder.startPlaying(recordFile) {
                                        recordingState = "IDLE"
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "No voice clip recorded yet!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = recordingState != "RECORDING",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (recordingState == "PLAYING") Color(0xFFE57373) else Color.White.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = if (recordingState == "PLAYING") Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (recordingState == "PLAYING") "Stop Replay" else "Replay Voice",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Delete Clip button
                    IconButton(
                        onClick = {
                            val f = File(recordFile)
                            if (f.exists()) {
                                f.delete()
                                android.widget.Toast.makeText(context, "Test clip deleted", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "No clip to delete", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = recordingState == "IDLE",
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE57373))
                    }
                }
            }
        }
    }
}

@Composable
fun SoundwaveVisualizerCanvas(amplitude: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    
    // Wave animation phases
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteTransitionSpec(),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val ampGlow = amplitude * 50f + 2f // Base height of waveform

        val barCount = 40
        val barWidth = width / (barCount * 1.5f)
        val barSpacing = barWidth * 0.5f

        // Draw multiple layered cybernetic visualizer elements
        val glowBrush = Brush.linearGradient(
            colors = listOf(Color(0xFF00FFCC), Color(0xFF00AAFF)),
            start = Offset(0f, 0f),
            end = Offset(width, height)
        )

        for (i in 0 until barCount) {
            val progress = i.toFloat() / barCount
            // Dynamic oscillation mapping peak levels
            val sineWave = Math.sin((progress * 4.0 * Math.PI) + phase).toFloat()
            val finalHeight = (ampGlow * Math.abs(sineWave) + (amplitude * 20f * (1f - progress))).coerceIn(4f, height - 16f)

            val x = i * (barWidth + barSpacing) + (barSpacing / 2f)
            val y = midY - (finalHeight / 2f)

            // Outer glowing soft wave bars
            drawRoundRect(
                brush = glowBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, finalHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
                alpha = if (amplitude > 0.02f) 0.85f else 0.25f
            )
        }
        
        // Dynamic horizontal neon frequency baseline grid
        drawLine(
            color = Color(0xFF00FFCC).copy(alpha = 0.2f),
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), phase * 10f)
        )
    }
}

private fun infiniteTransitionSpec(): InfiniteRepeatableSpec<Float> {
    return infiniteRepeatable(
        animation = tween(durationMillis = 2000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    )
}
