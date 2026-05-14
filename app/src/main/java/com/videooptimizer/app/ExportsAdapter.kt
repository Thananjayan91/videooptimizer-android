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
import com.videooptimizer.app.databinding.ItemExportBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class ExportsAdapter(
    private val items: MutableList<ExportedItem>,
    private val onPlay: (ExportedItem) -> Unit,
    private val onCompare: (ExportedItem) -> Unit,
    private val onDelete: (ExportedItem) -> Unit
) : RecyclerView.Adapter<ExportsAdapter.ViewHolder>() {

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    inner class ViewHolder(val binding: ItemExportBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val name = item.originalName.substringBeforeLast(".")
        holder.binding.tvName.text = "${name}_${item.platform.lowercase()}.mov"

        val sizeInfo = if (item.originalFileSize > 0)
            "${FileManager.formatSize(item.originalFileSize)} → ${FileManager.formatSize(item.outputFileSize)}"
        else FileManager.formatSize(item.outputFileSize)

        holder.binding.tvMeta.text = "${item.platform} · $sizeInfo · " +
            SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(item.dateExported))

        holder.binding.btnPlay.setOnClickListener { onPlay(item) }
        holder.binding.btnCompare.setOnClickListener { onCompare(item) }
        holder.binding.btnCompare.alpha = if (item.originalUriString.isNotEmpty()) 1.0f else 0.4f
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }

        // Load thumbnail from exported video URI
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return context.contentResolver.loadThumbnail(uri, Size(320, 240), null)
            } catch (_: Exception) {}
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (_: Exception) { null } finally { retriever.release() }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ExportedItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
