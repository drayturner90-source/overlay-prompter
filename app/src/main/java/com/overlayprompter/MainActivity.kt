package com.overlayprompter

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var script: EditText
    private var openCameraAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        script = findViewById(R.id.script)
        script.setText(prefs.script)

        bindSlider(R.id.speed, R.id.speedLabel, prefs.speed, { "Scroll speed: $it" }) { prefs.speed = it }
        bindSlider(R.id.size, R.id.sizeLabel, prefs.textSize, { "Text size: $it" }) { prefs.textSize = it }
        bindSlider(R.id.opacity, R.id.opacityLabel, prefs.opacity, { "Background darkness: $it%" }) { prefs.opacity = it }

        findViewById<Button>(R.id.start).setOnClickListener { start(openCamera = false) }
        findViewById<Button>(R.id.startCamera).setOnClickListener { start(openCamera = true) }

        // The overlay keeps running via a notification; Android 13+ asks before showing it.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the "Display over other apps" settings screen.
        if (openCameraAfterPermission && Settings.canDrawOverlays(this)) {
            openCameraAfterPermission = false
            start(openCamera = true)
        }
    }

    override fun onPause() {
        super.onPause()
        prefs.script = script.text.toString()
    }

    private fun bindSlider(
        barId: Int,
        labelId: Int,
        initial: Int,
        label: (Int) -> String,
        save: (Int) -> Unit,
    ) {
        val bar = findViewById<SeekBar>(barId)
        val text = findViewById<TextView>(labelId)
        bar.progress = initial
        text.text = label(initial)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, value: Int, fromUser: Boolean) {
                text.text = label(value)
                save(value)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    private fun start(openCamera: Boolean) {
        prefs.script = script.text.toString()
        if (prefs.script.isBlank()) {
            Toast.makeText(this, "Type or paste a script first", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Turn on \"Display over other apps\" for Overlay Prompter", Toast.LENGTH_LONG).show()
            openCameraAfterPermission = openCamera
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }

        startForegroundService(Intent(this, OverlayService::class.java))

        if (openCamera) {
            try {
                startActivity(Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "Couldn't find a camera app — open it yourself", Toast.LENGTH_LONG).show()
            }
        }
    }
}
