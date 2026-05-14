package com.videooptimizer.app

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.videooptimizer.app.databinding.BottomSheetPlayerBinding

class PlayerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPlayerBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    private val updateSeekBar = object : Runnable {
        override fun run() {
            val vv = _binding?.videoView ?: return
            if (vv.isPlaying) {
                binding.seekBar.progress = vv.currentPosition
                binding.tvTime.text = formatTime(vv.currentPosition) + " / " + formatTime(vv.duration)
                binding.btnPlayPause.text = "⏸"
            } else {
                binding.btnPlayPause.text = "▶"
            }
            handler.postDelayed(this, 300)
        }
    }

    companion object {
        fun newInstance(uriString: String, title: String): PlayerBottomSheet {
            return PlayerBottomSheet().apply {
                arguments = Bundle().apply {
                    putString("uri", uriString)
                    putString("title", title)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uri = Uri.parse(arguments?.getString("uri"))
        binding.tvTitle.text = arguments?.getString("title") ?: ""

        binding.videoView.setVideoURI(uri)
        binding.videoView.setOnPreparedListener { mp ->
            binding.seekBar.max = mp.duration
            binding.videoView.start()
            handler.post(updateSeekBar)
        }
        binding.videoView.setOnCompletionListener {
            binding.btnPlayPause.text = "▶"
        }

        binding.btnPlayPause.setOnClickListener {
            if (binding.videoView.isPlaying) binding.videoView.pause()
            else binding.videoView.start()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.videoView.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun formatTime(ms: Int): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(updateSeekBar)
        _binding?.videoView?.stopPlayback()
        super.onDestroyView()
        _binding = null
    }
}
