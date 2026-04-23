package com.itsaky.androidide.fragments.sidebar

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.itsaky.androidide.databinding.FragmentClassResourceMonitorBinding
import com.itsaky.androidide.fragments.FragmentWithBinding
import com.itsaky.androidide.utils.ClassResourceMonitor
import java.util.Locale

/** Editor drawer fragment showing class/tag based CPU and memory usage. */
class ClassResourceMonitorFragment :
    FragmentWithBinding<FragmentClassResourceMonitorBinding>(
        FragmentClassResourceMonitorBinding::inflate
    ) {

  private val adapter = ClassResourceMonitorAdapter()
  private val ticker =
      object : Runnable {
        override fun run() {
          refresh()
          view?.postDelayed(this, REFRESH_INTERVAL_MS)
        }
      }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.usagesList.layoutManager = LinearLayoutManager(requireContext())
    binding.usagesList.adapter = adapter

    binding.searchInput.doAfterTextChanged { refresh() }
    binding.btnReset.setOnClickListener {
      ClassResourceMonitor.reset()
      refresh()
    }

    refresh()
    view.post(ticker)
  }

  override fun onDestroyView() {
    view?.removeCallbacks(ticker)
    super.onDestroyView()
  }

  private fun refresh() {
    val filter = binding.searchInput.text?.toString().orEmpty().trim().lowercase(Locale.ROOT)
    val usages =
        ClassResourceMonitor.snapshot()
            .asSequence()
            .filter { it.hits > 0 }
            .filter { filter.isEmpty() || it.tag.lowercase(Locale.ROOT).contains(filter) }
            .map {
              ClassUsageItem(
                  tag = it.tag,
                  hits = it.hits,
                  totalCpuMs = nanosToMillis(it.totalCpuNanos),
                  avgCpuMs = nanosToMillis(it.avgCpuNanos),
                  totalMem = formatBytes(it.totalMemoryDeltaBytes),
                  avgMem = formatBytes(it.avgMemoryDeltaBytes),
                  peakMem = formatBytes(it.peakMemoryDeltaBytes),
              )
            }
            .toList()

    adapter.submitList(usages)
    binding.emptyHint.visibility = if (usages.isEmpty()) View.VISIBLE else View.GONE
  }

  private fun nanosToMillis(nanos: Long): String {
    val ms = nanos / 1_000_000.0
    return String.format(Locale.US, "%.2f ms", ms)
  }

  private fun formatBytes(bytes: Long): String {
    val abs = kotlin.math.abs(bytes.toDouble())
    val sign = if (bytes < 0) "-" else ""
    return when {
      abs >= 1024 * 1024 -> String.format(Locale.US, "%s%.2f MB", sign, abs / (1024 * 1024.0))
      abs >= 1024 -> String.format(Locale.US, "%s%.2f KB", sign, abs / 1024.0)
      else -> String.format(Locale.US, "%s%d B", sign, abs.toLong())
    }
  }

  companion object {
    private const val REFRESH_INTERVAL_MS = 1000L
  }
}
