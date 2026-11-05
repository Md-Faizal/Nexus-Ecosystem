package com.nexus.portal.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.BatteryManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.theme.PortalTheme
import com.nexus.portal.ui.theme.toColor
import com.nexus.portal.data.repository.ProfileRepository

class AndroidWebAppBridge(private val context: Context) {
    
    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun getBatteryPercentage(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    @JavascriptInterface
    fun getGPSCoordinates(): String {
        return "51.5074 N, 0.1278 W" // Static placeholder coordinates for high-security sandbox
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSandboxScreen(onBackClick: () -> Unit) {


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PortalTheme.colors.background)
    ) {
        TechGridBackground()
        
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PortalTheme.colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "WEB DASHBOARD SANDBOX",
                    color = PortalTheme.colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Full Screen Embedded Hardware-Accelerated WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        
                        // Register Kotlin WebView Interface
                        addJavascriptInterface(AndroidWebAppBridge(ctx), "AndroidInterface")
                        
                        loadUrl(com.nexus.portal.core.NetworkConfig.sandboxUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
