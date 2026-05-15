package com.videooptimizer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.videooptimizer.app.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HistoryAdapter

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { addToHistory(it) }
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) pickVideo.launch("video/*")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = HistoryAdapter(
            items = mutableListOf(),
            onPlay = { item ->
                startActivity(
                    Intent(requireContext(), PlayerActivity::class.java)
                        .putExtra("uri", item.uriString)
                )
            },
            onExport = { item ->
                ExportBottomSheet.newInstance(item).show(parentFragmentManager, "export")
            },
            onDelete = { item ->
                HistoryManager.remove(requireContext(), item.id)
                loadHistory()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.fabAdd.setOnClickListener { pickVideoWithPermission() }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    fun addSharedVideo(uri: Uri) {
        if (!isAdded) return
        addToHistory(uri)
    }

    private fun pickVideoWithPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            pickVideo.launch("video/*")
        } else {
            requestPermission.launch(arrayOf(permission))
        }
    }

    private fun addToHistory(uri: Uri) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) { }

        val item = HistoryItem(
            id = System.currentTimeMillis().toString(),
            name = getFileName(uri),
            uriString = uri.toString(),
            dateAdded = System.currentTimeMillis()
        )
        HistoryManager.add(requireContext(), item)
        loadHistory()
    }

    private fun loadHistory() {
        val items = HistoryManager.getAll(requireContext())
        adapter.updateItems(items)
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun getFileName(uri: Uri): String {
        var name = "video_${System.currentTimeMillis()}.mp4"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0) name = cursor.getString(col)
            }
        }
        return name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
