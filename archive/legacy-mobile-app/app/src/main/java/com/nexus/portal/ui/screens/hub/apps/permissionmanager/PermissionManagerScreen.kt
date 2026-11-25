package com.nexus.portal.ui.screens.hub.apps.permissionmanager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.services.FloatingOverlayService
import com.nexus.portal.services.MyDeviceAdminReceiver
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.theme.PortalTheme

@Composable
fun PermissionManagerScreen(onBackClick: () -> Unit, context: Context) {
    val primaryColor = PortalTheme.colors.primary
    val scrollState = rememberScrollState()

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
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PortalTheme.colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "PERMISSION MANAGER",
                    color = PortalTheme.colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            
            Spacer(modifier = Modifier.height(30.dp))

            // Superuser control center
            Text(
                text = "SYSTEM HOOK CONTROLLER",
                color = PortalTheme.colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 1. System Draggable Overlay HUDS
            PermissionItem(
                title = "Draggable Floating HUD Overlay",
                statusText = if (Settings.canDrawOverlays(context)) "AUTHORIZED" else "UNAUTHORIZED",
                isAuthorized = Settings.canDrawOverlays(context),
                primaryColor = primaryColor,
                onClick = {
                    if (!Settings.canDrawOverlays(context)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        // Toggle service execution
                        val serviceIntent = Intent(context, FloatingOverlayService::class.java)
                        // This just a simple toggle placeholder, typically would track service running state
                        context.startService(serviceIntent)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Notification Interception Service
            PermissionItem(
                title = "Notification Interceptor Service",
                statusText = "CONFIGURE",
                isAuthorized = true,
                primaryColor = primaryColor,
                onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Accessibility Gestures Logger Service
            PermissionItem(
                title = "Accessibility Gesture Logger",
                statusText = "CONFIGURE",
                isAuthorized = true,
                primaryColor = primaryColor,
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Device Security Admin
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)
            val isAdminActive = devicePolicyManager.isAdminActive(adminComponent)

            PermissionItem(
                title = "Device Administrator Control",
                statusText = if (isAdminActive) "ACTIVE" else "INACTIVE",
                isAuthorized = isAdminActive,
                primaryColor = primaryColor,
                onClick = {
                    if (!isAdminActive) {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enables Nexus system locking tools.")
                        }
                        context.startActivity(intent)
                    } else {
                        devicePolicyManager.removeActiveAdmin(adminComponent)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    statusText: String,
    isAuthorized: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PortalTheme.colors.surfaceCard)
            .border(1.dp, PortalTheme.colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = PortalTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap to configure or toggle state",
                    color = PortalTheme.colors.textSecondary,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isAuthorized) primaryColor.copy(alpha = 0.15f) else Color(0x1AFF3366))
                    .border(1.dp, if (isAuthorized) primaryColor else Color(0xFFFF3366), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusText,
                    color = if (isAuthorized) primaryColor else Color(0xFFFF3366),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
