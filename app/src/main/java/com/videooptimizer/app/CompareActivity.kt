package com.videooptimizer.app

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CompareActivity : AppCompatActivity() {

    private val TAG = "CompareActivity"

    private var playerOriginal: ExoPlayer? = null
    private var playerExported: ExoPlayer? = null
    private var isPlaying = false

    private lateinit var fab: FloatingActionButton
    private lateinit var seekBar: SeekBar
    private lateinit var exportedUri: String

    private lateinit var viewOriginal: PlayerView
    private lateinit var viewExported: PlayerView

    private var isSeeking = false
    private var seekWasPlaying = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private var syncTick = 0
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateSeekBar()
            // Re-sync exported player if it drifts more than 300ms from original
            if (!isSeeking && ++syncTick >= 4) {
                syncTick = 0
                val origPos = playerOriginal?.currentPosition ?: 0L
                val expPos  = playerExported?.currentPosition ?: 0L
                if (kotlin.math.abs(origPos - expPos) > 300) {
                    playerExported?.seekTo(origPos)
                }
            }
            progressHandler.postDelayed(this, 250)
        }
    }

    // Zoom/pan state
    private var currentScale = 1f
    private var currentTransX = 0f
    private var currentTransY = 0f
    private val maxScale = 5f

    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_compare)
        hideSystemBars()

        val originalUri = intent.getStringExtra("original_uri") ?: run { finish(); return }
        exportedUri = intent.getStringExtra("exported_uri") ?: run { finish(); return }
        val platformLabel = intent.getStringExtra("platform") ?: "Exported"

        findViewById<TextView>(R.id.tv_export_label).text = platformLabel.uppercase()

        val originalSize = intent.getLongExtra("original_size", 0L)
        val outputSize = intent.getLongExtra("output_size", 0L)

        if (originalSize > 0) {
            findViewById<TextView>(R.id.tv_original_size).apply {
                text = FileManager.formatSize(originalSize)
                visibility = View.VISIBLE
            }
        }
        if (outputSize > 0) {
            val saved = if (originalSize > 0)
                "  (${((1f - outputSize.toFloat() / originalSize) * 100).toInt()}% smaller)" else ""
            findViewById<TextView>(R.id.tv_export_size).apply {
                text = FileManager.formatSize(outputSize) + saved
                visibility = View.VISIBLE
            }
        }

        fab = findViewById(R.id.fab_play_pause)
        fab.setOnClickListener { togglePlayback() }

        seekBar = findViewById(R.id.seek_bar)
        seekBar.max = 1000
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar) {
                isSeeking = true
                seekWasPlaying = isPlaying
                progressHandler.removeCallbacks(progressRunnable)
                playerOriginal?.pause()
                playerExported?.pause()
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                val duration = playerOriginal?.duration?.takeIf { it > 0 } ?: run {
                    isSeeking = false; return
                }
                val seekTo = (sb.progress.toFloat() / 1000f * duration).toLong()
                seekBothAndResume(seekTo)
            }
        })

        viewOriginal = findViewById(R.id.player_original)
        viewExported = findViewById(R.id.player_exported)

        setupGestures()

        playerOriginal = buildPlayer(viewOriginal, originalUri, prepare = true, mute = false)

        playerOriginal?.addListener(object : Player.Listener {
            private var exportedBuilt = false

            override fun onPlaybackStateChanged(state: Int) {
                when {
                    state == Player.STATE_READY && !exportedBuilt -> {
                        exportedBuilt = true
                        playerExported = buildPlayer(viewExported, exportedUri, prepare = true, mute = true)
                    }
                    state == Player.STATE_ENDED -> {
                        playerOriginal?.seekTo(0)
                        playerExported?.seekTo(0)
                        playerOriginal?.pause()
                        playerExported?.pause()
                        isPlaying = false
                        seekBar.progress = 0
                        progressHandler.removeCallbacks(progressRunnable)
                        showFab()
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                if (isSeeking) return  // ignore transient pause during seek
                if (!playing && isPlaying) {
                    playerExported?.pause()
                    isPlaying = false
                    progressHandler.removeCallbacks(progressRunnable)
                    showFab()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Original player error: ${error.message}")
                runOnUiThread {
                    Toast.makeText(this@CompareActivity,
                        "Original video unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun showFab() {
        fab.visibility = View.VISIBLE
        fab.animate().alpha(0.55f).setDuration(200).start()
    }

    private fun hideFab() {
        fab.animate().alpha(0f).setDuration(200).withEndAction {
            fab.visibility = View.GONE
        }.start()
    }

    private fun seekBothAndResume(seekTo: Long) {
        val players = listOfNotNull(playerOriginal, playerExported)
        var confirmedCount = 0

        // Defined first so listener can reference it via resume.run()
        val resume = object : Runnable {
            var seekListener: Player.Listener? = null
            override fun run() {
                seekListener?.let { l -> players.forEach { p -> p.removeListener(l) } }
                progressHandler.removeCallbacks(this)
                isSeeking = false
                if (seekWasPlaying) {
                    isPlaying = true
                    playerOriginal?.play()
                    playerExported?.play()
                    progressHandler.post(progressRunnable)
                }
            }
        }

        val seekListener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                confirmedCount++
                if (confirmedCount >= players.size) resume.run()
            }
        }
        resume.seekListener = seekListener

        progressHandler.postDelayed(resume, 800)   // safety timeout
        players.forEach { it.addListener(seekListener) }
        playerOriginal?.seekTo(seekTo)
        playerExported?.seekTo(seekTo)
    }

    private fun updateSeekBar() {
        val player = playerOriginal ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        seekBar.progress = ((player.currentPosition.toFloat() / duration) * 1000).toInt()
    }

    private fun setupGestures() {
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale = (currentScale * detector.scaleFactor).coerceIn(1f, maxScale)
                applyTransform()
                return true
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                togglePlayback()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent,
                distanceX: Float, distanceY: Float
            ): Boolean {
                if (currentScale > 1f) {
                    currentTransX -= distanceX
                    currentTransY -= distanceY
                    clampTranslation()
                    applyTransform()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                currentScale = 1f
                currentTransX = 0f
                currentTransY = 0f
                applyTransform()
                return true
            }
        })

        val overlay = findViewById<View>(R.id.gesture_overlay)
        overlay.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun clampTranslation() {
        val maxTx = viewOriginal.width * (currentScale - 1f) / 2f
        val maxTy = viewOriginal.height * (currentScale - 1f) / 2f
        currentTransX = currentTransX.coerceIn(-maxTx, maxTx)
        currentTransY = currentTransY.coerceIn(-maxTy, maxTy)
    }

    private fun applyTransform() {
        for (view in listOf(viewOriginal, viewExported)) {
            view.scaleX = currentScale
            view.scaleY = currentScale
            view.translationX = currentTransX
            view.translationY = currentTransY
        }
    }

    private fun togglePlayback() {
        if (isPlaying) {
            playerOriginal?.pause()
            playerExported?.pause()
            isPlaying = false
            progressHandler.removeCallbacks(progressRunnable)
            showFab()
        } else {
            playerOriginal?.play()
            playerExported?.play()
            isPlaying = true
            progressHandler.post(progressRunnable)
            hideFab()
        }
    }

    private fun buildPlayer(view: PlayerView, uriString: String, prepare: Boolean, mute: Boolean): ExoPlayer? {
        return try {
            ExoPlayer.Builder(this).build().also { exo ->
                view.player = exo
                if (mute) exo.volume = 0f
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player error [$uriString]: ${error.message}")
                        runOnUiThread {
                            Toast.makeText(this@CompareActivity,
                                "Could not play video", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
                if (prepare) exo.prepare()
                exo.playWhenReady = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create player [$uriString]: ${e.message}")
            null
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        progressHandler.removeCallbacks(progressRunnable)
        playerOriginal?.pause()
        playerExported?.pause()
        isPlaying = false
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)
        playerOriginal?.release()
        playerExported?.release()
        playerOriginal = null
        playerExported = null
        super.onDestroy()
    }
}
