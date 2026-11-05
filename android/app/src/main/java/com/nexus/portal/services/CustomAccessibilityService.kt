package com.nexus.portal.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class CustomAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val eventType = AccessibilityEvent.eventTypeToString(event.eventType)
        val className = event.className?.toString() ?: "Unknown Class"
        val packageName = event.packageName?.toString() ?: "Unknown Package"
        
        // Broadcast custom events
        val intent = Intent("com.nexus.portal.ACCESSIBILITY_EVENT").apply {
            putExtra("event_type", eventType)
            putExtra("class_name", className)
            putExtra("package_name", packageName)
        }
        sendBroadcast(intent)
    }

    override fun onInterrupt() {}
}
