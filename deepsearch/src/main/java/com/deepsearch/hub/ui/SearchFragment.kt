package com.deepsearch.hub.ui

import android.app.Activity
import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.deepsearch.hub.Prefs
import com.deepsearch.hub.R
import com.deepsearch.hub.databinding.FragmentSearchBinding
import com.deepsearch.hub.editor.EditorActivity
import com.deepsearch.hub.search.SearchEngines
import com.deepsearch.hub.search.SearchResultsActivity
import com.deepsearch.hub.search.SearchViewModel
import com.deepsearch.hub.util.MediaUtils
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class SearchFragment : Fragment(R.layout.fragment_search) {

    private var _b: FragmentSearchBinding? = null
    private val b get() = _b!!
    private val vm: SearchViewModel by activityViewModels()

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.load(it) }
    }

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringExtra(EditorActivity.EXTRA_OUTPUT)?.let { vm.applyEdited(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentSearchBinding.bind(view)

        b.dropZone.setOnClickListener { pick() }
        b.btnPick.setOnClickListener { pick() }
        b.btnEdit.setOnClickListener {
            if (!vm.hasImage) toast(R.string.pick_first)
            else editLauncher.launch(EditorActivity.intent(requireContext(), vm.workFile))
        }
        b.btnSearch.setOnClickListener { startSearch() }
        setupEngineChips()
        setupVideoSlider()
        setupDragAndDrop()
        observe()
    }

    private fun pick() = pickMedia.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
    )

    private fun setupEngineChips() {
        val selected = Prefs.selectedEngines
        SearchEngines.all().forEach { e ->
            val chip = Chip(requireContext()).apply {
                text = e.name
                tag = e.id
                isCheckable = true
                isChecked = e.id in selected
                setOnCheckedChangeListener { _, _ -> Prefs.selectedEngines = selectedEngineIds() }
            }
            b.engineChips.addView(chip)
        }
    }

    private fun selectedEngineIds(): Set<String> =
        b.engineChips.children.filterIsInstance<Chip>().filter { it.isChecked }.map { it.tag as String }.toSet()

    private fun setupVideoSlider() {
        b.frameSlider.setLabelFormatter { MediaUtils.formatTime(it.toLong()) }
        b.frameSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                vm.seekFrame(slider.value.toLong())
            }
        })
    }

    /** سحب وإفلات صورة/فيديو من تطبيق آخر (وضع الشاشة المقسومة أو النوافذ الحرة). */
    private fun setupDragAndDrop() {
        b.dropZone.setOnDragListener { v, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED ->
                    e.clipDescription?.let { it.hasMimeType("image/*") || it.hasMimeType("video/*") } ?: false
                DragEvent.ACTION_DRAG_ENTERED -> { v.alpha = 0.6f; true }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 1f; true }
                DragEvent.ACTION_DROP -> {
                    requireActivity().requestDragAndDropPermissions(e)
                    val uri = e.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                    uri?.let { vm.load(it) }
                    uri != null
                }
                else -> true
            }
        }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.preview.collect { bmp ->
                        b.preview.setImageBitmap(bmp)
                        b.placeholder.isVisible = bmp == null
                        b.btnEdit.isEnabled = bmp != null
                        b.btnSearch.isEnabled = bmp != null
                    }
                }
                launch {
                    vm.source.collect { src ->
                        val video = src?.isVideo == true
                        b.videoPanel.isVisible = video
                        if (video && src != null) {
                            b.frameSlider.valueTo = src.durationMs.coerceAtLeast(1).toFloat()
                            b.frameSlider.value = src.frameMs.toFloat().coerceIn(0f, b.frameSlider.valueTo)
                            b.frameLabel.text = getString(
                                R.string.frame_at, MediaUtils.formatTime(src.frameMs), MediaUtils.formatTime(src.durationMs)
                            )
                        }
                    }
                }
                launch { vm.busy.collect { b.progress.isVisible = it } }
                launch { vm.errors.collect { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() } }
            }
        }
    }

    private fun startSearch() {
        val ids = selectedEngineIds()
        if (ids.isEmpty()) return toast(R.string.pick_engine)
        if (!vm.hasImage) return toast(R.string.pick_first)
        Prefs.selectedEngines = ids
        SearchResultsActivity.start(requireContext(), vm.workFile, ids)
    }

    private fun toast(res: Int) = Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
