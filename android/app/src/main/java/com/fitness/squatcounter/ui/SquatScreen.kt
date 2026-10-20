package com.fitness.squatcounter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fitness.squatcounter.ui.components.CameraPreview
import com.fitness.squatcounter.ui.components.DebugOverlay
import com.fitness.squatcounter.ui.components.SkeletonOverlay

@Composable
fun SquatScreen(
    viewModel: SquatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    if (!hasCameraPermission) {
        // Permission Request UI
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF111827))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Camera Permission Required",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Live squat tracking runs 100% on-device. No images are saved or transmitted.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Grant Permission", color = Color.White)
                }
            }
        }
    } else {
        // Live Squat Counter UI
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            // 1. Camera live preview with ImageAnalysis
            CameraPreview(
                onFrame = { imageProxy, isFrontCamera ->
                    viewModel.onCameraFrame(imageProxy, isFrontCamera)
                }
            )

            // 2. Skeleton Overlay (bones & joints)
            SkeletonOverlay(
                landmarks = uiState.landmarks,
                isFullBodyVisible = uiState.isFullBodyVisible
            )

            // 3. Debug Overlay HUD (reps, state, angles, FPS, rejection reason)
            DebugOverlay(
                uiState = uiState,
                onReset = { viewModel.resetCounter() }
            )
        }
    }
}
