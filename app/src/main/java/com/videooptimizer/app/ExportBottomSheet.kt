package com.videooptimizer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.videooptimizer.app.databinding.BottomSheetExportBinding
import java.io.File

class ExportBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetExportBinding? = null
    private val binding get() = _binding!!

    private var encodedFile: File? = null
    private var savedUri: Uri? = null
    private var selectedPlatform: Platform? = null

    companion object {
        private const val STEP_PLATFORM = 0
        private const val STEP_QUALITY = 1
        private const val STEP_PROGRESS = 2
        private const val STEP_SAVE = 3
        private const val STEP_DONE = 4

        fun newInstance(item: HistoryItem): ExportBottomSheet {
            return ExportBottomSheet().apply {
                arguments = Bundle().apply {
                    putString("name", item.name)
                    putString("uri", item.uriString)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val name = arguments?.getString("name") ?: ""
        val uri = Uri.parse(arguments?.getString("uri") ?: "")

        binding.tvTitle.text = name

        Platform.entries.forEach { platform ->
            val btn = com.google.android.material.button.MaterialButton(requireContext()).apply {
                text = platform.label
                setOnClickListener {
                    selectedPlatform = platform
                    binding.tvQualityHint.text = "Quality for ${platform.label}"
                    showStep(STEP_QUALITY)
                }
            }
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            binding.platformButtonsContainer.addView(btn, params)
        }

        binding.btnQualitySmall.setOnClickListener {
            selectedPlatform?.let { startExport(uri, name, it, qualityMultiplier = 0.4f) }
        }
        binding.btnQualityBalanced.setOnClickListener {
            selectedPlatform?.let { startExport(uri, name, it, qualityMultiplier = 1.0f) }
        }
        binding.btnQualityHigh.setOnClickListener {
            selectedPlatform?.let { startExport(uri, name, it, qualityMultiplier = 2.0f) }
        }
    }

    private fun startExport(uri: Uri, originalName: String, platform: Platform, qualityMultiplier: Float) {
        val profile = PlatformProfiles.all[platform] ?: return
        val originalSize = FileManager.getFileSize(requireContext(), uri)

        showStep(STEP_PROGRESS)
        binding.tvProgress.text = "Optimizing for ${platform.label}…"

        VideoOptimizer.optimize(
            context = requireContext(),
            inputUri = uri,
            profile = profile,
            qualityMultiplier = qualityMultiplier,
            onProgress = { progress ->
                activity?.runOnUiThread {
                    binding.progressBar.progress = progress
                    binding.tvProgress.text = "Optimizing for ${platform.label}… $progress%"
                }
            },
            onComplete = { result ->
                activity?.runOnUiThread {
                    when (result) {
                        is OptimizeResult.Success -> {
                            encodedFile = result.outputFile
                            val outputSize = result.outputFile.length()
                            val suggestedName = FileManager.buildOutputFilename(originalName, platform)

                            binding.tvSizeInfo.text =
                                "Original: ${FileManager.formatSize(originalSize)}  →  Output: ${FileManager.formatSize(outputSize)}"
                            binding.etFilename.setText(suggestedName)

                            binding.btnConfirmSave.setOnClickListener {
                                val filename = binding.etFilename.text?.toString()?.trim()
                                    ?.takeIf { it.isNotEmpty() } ?: suggestedName
                                saveFile(filename, originalName, uri, platform.label, originalSize, outputSize)
                            }
                            binding.btnCancelSave.setOnClickListener { dismiss() }

                            showStep(STEP_SAVE)
                        }
                        is OptimizeResult.Error -> {
                            binding.tvProgress.text = "Error: ${result.message}"
                        }
                    }
                }
            }
        )
    }

    private fun saveFile(
        filename: String,
        originalName: String,
        originalUri: Uri,
        platformLabel: String,
        originalSize: Long,
        outputSize: Long
    ) {
        val file = encodedFile ?: return
        val ctx = requireContext()
        val uri = FileManager.saveToVideoOptimizer(ctx, file, filename)
        file.delete()

        if (uri != null) {
            savedUri = uri
            ExportManager.add(ctx, ExportedItem(
                id = System.currentTimeMillis().toString(),
                originalName = originalName,
                originalUriString = originalUri.toString(),
                platform = platformLabel,
                uriString = uri.toString(),
                originalFileSize = originalSize,
                outputFileSize = outputSize,
                dateExported = System.currentTimeMillis()
            ))

            binding.tvSavedInfo.text =
                "✓ Saved as $filename\n" +
                "Original: ${FileManager.formatSize(originalSize)}  →  Output: ${FileManager.formatSize(outputSize)}"

            binding.btnShare.setOnClickListener { shareFile(uri) }
            showStep(STEP_DONE)
        } else {
            binding.tvProgress.text = "Failed to save file"
            showStep(STEP_PROGRESS)
        }
    }

    private fun shareFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/quicktime"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share to…"))
        dismiss()
    }

    private fun showStep(step: Int) {
        binding.platformButtonsContainer.visibility = if (step == STEP_PLATFORM) View.VISIBLE else View.GONE
        binding.qualityGroup.visibility = if (step == STEP_QUALITY) View.VISIBLE else View.GONE
        binding.progressGroup.visibility = if (step == STEP_PROGRESS) View.VISIBLE else View.GONE
        binding.saveGroup.visibility = if (step == STEP_SAVE) View.VISIBLE else View.GONE
        binding.doneGroup.visibility = if (step == STEP_DONE) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
