package com.videooptimizer.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.videooptimizer.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var historyFragment: HistoryFragment? = null
    private var exportsFragment: ExportsFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            historyFragment = HistoryFragment()
            exportsFragment = ExportsFragment()
            // commitNow() runs synchronously so onViewCreated fires before we call addSharedVideo
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, historyFragment!!, "history")
                .add(R.id.fragment_container, exportsFragment!!, "exports")
                .hide(exportsFragment!!)
                .commitNow()
        } else {
            historyFragment = supportFragmentManager.findFragmentByTag("history") as? HistoryFragment
            exportsFragment = supportFragmentManager.findFragmentByTag("exports") as? ExportsFragment
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_videos -> {
                    supportFragmentManager.beginTransaction()
                        .show(historyFragment!!).hide(exportsFragment!!).commit()
                    true
                }
                R.id.nav_exports -> {
                    supportFragmentManager.beginTransaction()
                        .show(exportsFragment!!).hide(historyFragment!!).commit()
                    true
                }
                else -> false
            }
        }

        handleShareIntent(intent)
    }

    // Called when the app is already running and a video is shared into it
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        uri ?: return

        // Switch to History tab so the user sees the added video
        binding.bottomNav.selectedItemId = R.id.nav_videos
        // Defer one frame so the fragment is fully attached before we call into it
        binding.root.post { historyFragment?.addSharedVideo(uri) }
    }
}
