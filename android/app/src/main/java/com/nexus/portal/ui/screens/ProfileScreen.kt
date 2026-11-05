package com.nexus.portal.ui.screens

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.nexus.portal.R
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.nexus.portal.data.repository.ProfileRepository
import com.nexus.portal.ui.components.GlassmorphicButton
import com.nexus.portal.ui.components.GlassmorphicCard
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.theme.PortalTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBackClick: (() -> Unit)?, onLoginComplete: (() -> Unit)? = null) {
    val primaryColor = PortalTheme.colors.primary
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val GOOGLE_WEB_CLIENT_ID = "994653744337-qeec0n17pd7c6l8ojcjhvb66c0eq9n4o.apps.googleusercontent.com"

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
    }
    
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.let {
                    val displayName = it.displayName ?: "Google User"
                    ProfileRepository.addProfile(
                        name = displayName,
                        themeHex = "#00FFCC", // Default color for Google users
                        enabledApps = listOf("permissions", "update", "profiles", "sandbox"),
                        oauthToken = it.idToken,
                        email = it.email,
                        photoUrl = it.photoUrl?.toString()
                    )
                    if (onBackClick != null) {
                        onBackClick()
                    } else if (onLoginComplete != null) {
                        onLoginComplete()
                    }
                }
            } catch (e: ApiException) {
                Log.w("GoogleSignIn", "signInResult:failed code=" + e.statusCode)
                errorMessage = "Google Sign-In Failed: ${e.statusCode} (Did you set the Web Client ID?)"
                isLoading = false
            }
        } else {
            isLoading = false
            errorMessage = "Sign-In Canceled/Failed (Code: ${result.resultCode}). Ensure SHA-1 matches the APK's keystore."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PortalTheme.colors.background)
    ) {
        TechGridBackground()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PortalTheme.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = "WELCOME",
                    color = PortalTheme.colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // Profile Form Panel
            GlassmorphicCard {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Welcome",
                    tint = primaryColor,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "GET STARTED",
                    color = primaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Step into the Nexus!",
                    color = PortalTheme.colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Sign in to unlock a seamless, personalized experience across all your devices. Let's get your digital journey started today.",
                    color = PortalTheme.colors.textSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Features list
                val features = listOf(
                    "Personalized, vibrant interface themes",
                    "Seamless cross-device synchronization",
                    "Lightning-fast node access"
                )
                
                var showFeatures by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(300)
                    showFeatures = true
                }
                
                Column {
                    features.forEachIndexed { index, feature ->
                        AnimatedVisibility(
                            visible = showFeatures,
                            enter = fadeIn(animationSpec = tween(500, delayMillis = index * 200)) + 
                                    slideInVertically(initialOffsetY = { 20 }, animationSpec = tween(500, delayMillis = index * 200))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically, 
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle, 
                                    contentDescription = null, 
                                    tint = primaryColor, 
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = feature, 
                                    color = PortalTheme.colors.textSecondary, 
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Button
            if (isLoading) {
                CircularProgressIndicator(color = primaryColor, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                val infiniteTransition = rememberInfiniteTransition(label = "Glow")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "GlowAlpha"
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(
                                2.dp,
                                Brush.horizontalGradient(
                                    colors = listOf(primaryColor.copy(alpha = glowAlpha), primaryColor.copy(alpha = 0.2f), primaryColor.copy(alpha = glowAlpha))
                                ),
                                RoundedCornerShape(28.dp)
                            )
                    )
                    
                    GlassmorphicButton(
                        text = "SIGNUP/LOGIN WITH GOOGLE",
                        glowingColor = primaryColor,
                        onClick = {
                            isLoading = true
                            errorMessage = ""
                            launcher.launch(googleSignInClient.signInIntent)
                        },
                        icon = {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
