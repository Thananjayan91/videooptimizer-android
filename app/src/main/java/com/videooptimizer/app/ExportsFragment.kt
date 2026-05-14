package com.videooptimizer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.videooptimizer.app.databinding.FragmentExportsBinding

class ExportsFragment : Fragment() {

    private var _binding: FragmentExportsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ExportsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ExportsAdapter(
            items = mutableListOf(),
            onPlay = { item ->
                startActivity(
                    Intent(requireContext(), PlayerActivity::class.java)
                        .putExtra("uri", item.uriString)
                )
            },
            onCompare = { item ->
                if (item.originalUriString.isNotEmpty()) {
                    startActivity(
                        Intent(requireContext(), CompareActivity::class.java)
                            .putExtra("original_uri", item.originalUriString)
                            .putExtra("exported_uri", item.uriString)
                            .putExtra("platform", item.platform)
                            .putExtra("original_size", item.originalFileSize)
                            .putExtra("output_size", item.outputFileSize)
                    )
                } else {
                    Toast.makeText(requireContext(),
                        "Re-export this video to enable Compare", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = { item ->
                deleteExportedFile(item)
                ExportManager.remove(requireContext(), item.id)
                loadExports()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadExports()
    }

    private fun loadExports() {
        val items = ExportManager.getAll(requireContext())
        adapter.updateItems(items)
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun deleteExportedFile(item: ExportedItem) {
        try {
            val uri = Uri.parse(item.uriString)
            requireContext().contentResolver.delete(uri, null, null)
        } catch (e: Exception) { }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
