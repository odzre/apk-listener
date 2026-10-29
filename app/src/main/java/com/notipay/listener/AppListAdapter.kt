package com.notipay.listener

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.notipay.listener.databinding.ItemAppBinding

class AppListAdapter(
    private val selected: MutableSet<String>,
    private val onSelectionChanged: (Int) -> Unit,
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private val allItems = mutableListOf<InstalledApp>()
    private val visibleItems = mutableListOf<InstalledApp>()

    inner class ViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = visibleItems[position]
        val binding = holder.binding
        binding.appIcon.setImageDrawable(item.icon)
        binding.appName.text = item.label
        binding.appPackage.text = item.packageName
        binding.appCheck.setOnCheckedChangeListener(null)
        binding.appCheck.isChecked = selected.contains(item.packageName)
        binding.appCheck.setOnCheckedChangeListener { _, checked ->
            if (checked) selected.add(item.packageName) else selected.remove(item.packageName)
            onSelectionChanged(selected.size)
        }
        binding.root.setOnClickListener {
            binding.appCheck.isChecked = !binding.appCheck.isChecked
        }
    }

    override fun getItemCount(): Int = visibleItems.size

    fun submit(items: List<InstalledApp>) {
        allItems.clear()
        allItems.addAll(items)
        visibleItems.clear()
        visibleItems.addAll(items)
        notifyDataSetChanged()
        onSelectionChanged(selected.size)
    }

    fun filter(query: String) {
        val needle = query.trim().lowercase()
        visibleItems.clear()
        if (needle.isEmpty()) {
            visibleItems.addAll(allItems)
        } else {
            visibleItems.addAll(allItems.filter {
                it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
            })
        }
        notifyDataSetChanged()
    }
}
