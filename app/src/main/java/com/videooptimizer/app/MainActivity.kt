package com.videooptimizer.app

import android.content.Intent
import android.net.Uri
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
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, historyFragment!!, "history")
                .add(R.id.fragment_container, exportsFragment!!, "exports")
                .hide(exportsFragment!!)
                .commit()
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

        if (intent?.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            uri?.let { historyFragment?.addSharedVideo(it) }
        }
    }
}
