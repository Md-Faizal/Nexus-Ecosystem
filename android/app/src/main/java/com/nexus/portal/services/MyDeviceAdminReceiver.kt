package com.nexus.portal.services

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Nexus Node Elevated: Admin Active", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Nexus Node Revoked: Admin Inactive", Toast.LENGTH_SHORT).show()
    }
}
