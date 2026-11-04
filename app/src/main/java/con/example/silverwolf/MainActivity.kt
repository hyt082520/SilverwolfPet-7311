package com.example.silverwolf

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var pendingStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(60, 240, 60, 60)
        }

        val title = TextView(this).apply {
            text = "🎮 银狼桌宠"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        val tip = TextView(this).apply {
            text = "\n1. 点击「启动」，首次会要求「显示在其他应用上层」权限\n" +
                   "2. 授权后银狼自动出现\n" +
                   "3. 长按银狼可缩小，再长按恢复\n" +
                   "4. 单击说话，双击放信号波\n"
            textSize = 15f
            gravity = Gravity.CENTER
        }

        val btnStart = Button(this).apply {
            text = "启动银狼"
            setOnClickListener { ensurePermissionThenStart() }
        }
        val btnStop = Button(this).apply {
            text = "关闭桌宠"
            setOnClickListener {
                stopService(Intent(this@MainActivity, PetService::class.java))
                Toast.makeText(this@MainActivity, "已关闭", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(title)
        root.addView(tip)
        root.addView(btnStart)
        root.addView(btnStop)
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun ensurePermissionThenStart() {
        if (!Settings.canDrawOverlays(this)) {
            pendingStart = true
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            startPet()
        }
    }

    private fun startPet() {
        ContextCompat.startForegroundService(
            this, Intent(this, PetService::class.java)
        )
        Toast.makeText(this, "银狼已上线～", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (pendingStart && Settings.canDrawOverlays(this)) {
            pendingStart = false
            startPet()
        }
    }
}
