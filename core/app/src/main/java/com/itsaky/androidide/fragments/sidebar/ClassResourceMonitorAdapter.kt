package com.itsaky.androidide.fragments.sidebar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.itsaky.androidide.databinding.LayoutClassResourceUsageItemBinding

data class ClassUsageItem(
    val tag: String,
    val hits: Long,
    val totalCpuMs: String,
    val avgCpuMs: String,
    val totalMem: String,
    val avgMem: String,
    val peakMem: String,
)

class ClassResourceMonitorAdapter :
    ListAdapter<ClassUsageItem, ClassResourceMonitorAdapter.ViewHolder>(DIFF) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding =
        LayoutClassResourceUsageItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class ViewHolder(private val binding: LayoutClassResourceUsageItemBinding) :
      RecyclerView.ViewHolder(binding.root) {
    fun bind(item: ClassUsageItem) {
      binding.tagName.text = item.tag
      binding.statsLine1.text = "calls=${item.hits}  totalCPU=${item.totalCpuMs}  avgCPU=${item.avgCpuMs}"
      binding.statsLine2.text = "totalMem=${item.totalMem}  avgMem=${item.avgMem}  peakΔ=${item.peakMem}"
    }
  }

  companion object {
    private val DIFF =
        object : DiffUtil.ItemCallback<ClassUsageItem>() {
          override fun areItemsTheSame(oldItem: ClassUsageItem, newItem: ClassUsageItem): Boolean {
            return oldItem.tag == newItem.tag
          }

          override fun areContentsTheSame(
              oldItem: ClassUsageItem,
              newItem: ClassUsageItem,
          ): Boolean {
            return oldItem == newItem
          }
        }
  }
}
