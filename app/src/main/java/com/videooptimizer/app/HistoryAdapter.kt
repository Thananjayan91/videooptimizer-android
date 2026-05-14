package com.videooptimizer.app

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.videooptimizer.app.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class HistoryAdapter(
    private val items: MutableList<HistoryItem>,
    private val onPlay: (HistoryItem) -> Unit,
    private val onExport: (HistoryItem) -> Unit,
    private val onDelete: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    inner class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvName.text = item.name
        holder.binding.tvDate.text = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())
            .format(Date(item.dateAdded))
        holder.binding.btnExport.setOnClickListener { onExport(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }

        // Tap thumbnail to play original
        holder.binding.ivThumbnail.setOnClickListener { onPlay(item) }

        // Reset thumbnail before async load to avoid stale image on recycled views
        holder.binding.ivThumbnail.setImageResource(android.R.drawable.ic_media_play)
        val tag = item.id
        holder.binding.ivThumbnail.tag = tag

        executor.execute {
            val bitmap = loadThumbnail(holder.itemView.context, Uri.parse(item.uriString))
            mainHandler.post {
                if (holder.binding.ivThumbnail.tag == tag) {
                    bitmap?.let { holder.binding.ivThumbnail.setImageBitmap(it) }
                }
            }
        }
    }

    private fun loadThumbnail(context: android.content.Context, uri: Uri): Bitmap? {
        // API 29+: ContentResolver.loadThumbnail handles HEVC/MOV natively
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return context.contentResolver.loadThumbnail(uri, Size(320, 240), null)
            } catch (_: Exception) {}
        }
        // Fallback: try 1s offset first (keyframe more likely), then any frame
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (_: Exception) { null } finally { retriever.release() }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<HistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
