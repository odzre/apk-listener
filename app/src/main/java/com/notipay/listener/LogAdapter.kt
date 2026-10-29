package com.notipay.listener

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.notipay.listener.databinding.ItemLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter(
    private val onClick: (NotificationLogEntry) -> Unit,
) : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    private val items = mutableListOf<NotificationLogEntry>()
    private val timeFormat = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())

    inner class ViewHolder(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        val binding = holder.binding
        val context = binding.root.context

        binding.logApp.text = entry.appLabel.ifBlank { entry.packageName }
        binding.logPackage.text = entry.packageName
        binding.logTime.text = timeFormat.format(Date(entry.timestamp))
        binding.logTitle.text = entry.title.ifBlank { "(tanpa judul)" }
        binding.logText.text = entry.text.ifBlank { "(tanpa teks)" }
        binding.logStatus.text = entry.status

        val color = when {
            entry.status.startsWith("Sukses") -> R.color.np_success
            entry.status.startsWith("Gagal") || entry.status.startsWith("Belum") -> R.color.np_danger
            entry.status.startsWith("Retry") || entry.status.startsWith("Antre") -> R.color.np_warning
            else -> R.color.np_muted
        }
        binding.logStatus.setTextColor(ContextCompat.getColor(context, color))
        binding.root.setOnClickListener { onClick(entry) }
    }

    override fun getItemCount(): Int = items.size

    fun submit(entries: List<NotificationLogEntry>) {
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }
}
