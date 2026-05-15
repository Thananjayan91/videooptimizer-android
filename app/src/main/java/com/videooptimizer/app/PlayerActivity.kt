package com.videooptimizer.app

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var seekBar: SeekBar

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateSeekBar()
            progressHandler.postDelayed(this, 250)
        }
    }

    // Tap detection for dispatchTouchEvent
    private var tapDownX = 0f
    private var tapDownY = 0f
    private var tapPointerCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        val uriString = intent.getStringExtra("uri") ?: run { finish(); return }

        seekBar = findViewById(R.id.seek_bar)
        seekBar.max = 1000
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar) {
                progressHandler.removeCallbacks(progressRunnable)
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                val duration = player?.duration?.takeIf { it > 0 } ?: return
                player?.seekTo((sb.progress.toFloat() / 1000f * duration).toLong())
                progressHandler.post(progressRunnable)
            }
        })

        val playerView = findViewById<PlayerView>(R.id.player_view)
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        exo.seekTo(0)
                        exo.pause()
                        seekBar.progress = 0
                        progressHandler.removeCallbacks(progressRunnable)
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) progressHandler.post(progressRunnable)
                    else progressHandler.removeCallbacks(progressRunnable)
                }
            })
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapDownX = ev.rawX
                tapDownY = ev.rawY
                tapPointerCount = 1
            }
            MotionEvent.ACTION_POINTER_DOWN -> tapPointerCount++
            MotionEvent.ACTION_UP -> {
                if (tapPointerCount == 1) {
                    val moved = abs(ev.rawX - tapDownX) > 20 || abs(ev.rawY - tapDownY) > 20
                    if (!moved && !isTouchInControls(ev.rawY)) {
                        player?.let { if (it.isPlaying) it.pause() else it.play() }
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isTouchInControls(rawY: Float): Boolean {
        val controls = findViewById<View>(R.id.bottom_controls) ?: return false
        val loc = IntArray(2)
        controls.getLocationOnScreen(loc)
        return rawY >= loc[1]
    }

    private fun updateSeekBar() {
        val exo = player ?: return
        val duration = exo.duration.takeIf { it > 0 } ?: return
        seekBar.progress = ((exo.currentPosition.toFloat() / duration) * 1000).toInt()
    }

    override fun onStop() {
        super.onStop()
        progressHandler.removeCallbacks(progressRunnable)
        player?.pause()
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)
        player?.release()
        player = null
        super.onDestroy()
    }
}
