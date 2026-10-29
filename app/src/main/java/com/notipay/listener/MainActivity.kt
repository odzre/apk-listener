package com.notipay.listener

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.notipay.listener.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppListAdapter
    private lateinit var logAdapter: LogAdapter
    private val selectedPackages = mutableSetOf<String>()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        selectedPackages.clear()
        selectedPackages.addAll(Prefs.selectedPackages(this))

        adapter = AppListAdapter(selectedPackages) { count ->
            updateSelectedCount(count)
        }
        binding.appRecycler.layoutManager = LinearLayoutManager(this)
        binding.appRecycler.adapter = adapter

        logAdapter = LogAdapter { showLogDetail(it) }
        binding.logRecycler.layoutManager = LinearLayoutManager(this)
        binding.logRecycler.adapter = logAdapter
        binding.btnClearLog.setOnClickListener { confirmClearLogs() }
        binding.btnCopyLog.setOnClickListener { copyAllLogs() }
        refreshLogs()

        binding.urlInput.setText(Prefs.callbackUrl(this))
        binding.apiKeyInput.setText(Prefs.apiKey(this))
        binding.deviceInput.setText(Prefs.deviceId(this))
        binding.switchService.isChecked = Prefs.isServiceEnabled(this)
        if (Prefs.isServiceEnabled(this) && Prefs.hasNotificationAccess(this)) {
            KeeperService.start(this)
        }

        binding.appSearch.doAfterTextChanged { editable ->
            adapter.filter(editable?.toString().orEmpty())
        }

        binding.btnOpenSettings.setOnClickListener { openNotificationAccessSettings() }
        binding.btnBattery.setOnClickListener { requestBatteryOptimizationExemption() }
        binding.btnTest.setOnClickListener { sendTestCallback() }
        binding.btnSave.setOnClickListener { saveAndStart() }

        binding.switchService.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!Prefs.hasNotificationAccess(this)) {
                    binding.switchService.isChecked = false
                    Toast.makeText(
                        this,
                        "Aktifkan akses notifikasi terlebih dahulu.",
                        Toast.LENGTH_LONG,
                    ).show()
                    openNotificationAccessSettings()
                    return@setOnCheckedChangeListener
                }
                if (!validateConfig()) {
                    binding.switchService.isChecked = false
                    return@setOnCheckedChangeListener
                }
                Prefs.setServiceEnabled(this, true)
                requestNotificationPermission()
                KeeperService.start(this)
                Toast.makeText(this, "Listener background dijalankan.", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setServiceEnabled(this, false)
                KeeperService.stop(this)
                Toast.makeText(this, "Listener background dihentikan.", Toast.LENGTH_SHORT).show()
            }
        }

        loadInstalledApps()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            logReceiver,
            IntentFilter(LogStore.ACTION_LOGS_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        runCatching { unregisterReceiver(logReceiver) }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
        binding.lastStatus.text = Prefs.lastStatus(this)
        refreshLogs()
    }

    private fun updatePermissionUi() {
        val hasAccess = Prefs.hasNotificationAccess(this)
        if (hasAccess) {
            binding.permissionIcon.setImageResource(R.drawable.ic_check)
            binding.permissionStatus.text = if (Prefs.isListenerConnected(this)) {
                "Aktif · listener terhubung ke sistem"
            } else {
                "Akses aktif · menunggu koneksi sistem"
            }
            binding.permissionStatus.setTextColor(ContextCompat.getColor(this, R.color.np_success))
            binding.btnOpenSettings.text = "Kelola"
        } else {
            binding.permissionIcon.setImageResource(R.drawable.ic_warning)
            binding.permissionStatus.text = "Belum diaktifkan · buka pengaturan"
            binding.permissionStatus.setTextColor(ContextCompat.getColor(this, R.color.np_warning))
            binding.btnOpenSettings.text = "Buka"
        }
    }

    private fun updateSelectedCount(count: Int) {
        binding.selectedCount.text = "$count dipilih"
    }

    private fun loadInstalledApps() {
        binding.appSearch.isEnabled = false
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val manager = packageManager
                manager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .asSequence()
                    .filter { it.packageName != packageName }
                    .filter { manager.getLaunchIntentForPackage(it.packageName) != null }
                    .mapNotNull { appInfo ->
                        runCatching {
                            InstalledApp(
                                packageName = appInfo.packageName,
                                label = manager.getApplicationLabel(appInfo).toString(),
                                icon = manager.getApplicationIcon(appInfo.packageName),
                            )
                        }.getOrNull()
                    }
                    .sortedBy { it.label.lowercase(Locale.getDefault()) }
                    .toList()
            }
            adapter.submit(apps)
            binding.appSearch.isEnabled = true
        }
    }

    private fun validateConfig(): Boolean {
        var valid = true
        val url = binding.urlInput.text?.toString()?.trim().orEmpty()
        val apiKey = binding.apiKeyInput.text?.toString()?.trim().orEmpty()

        val urlValid = (url.startsWith("http://") || url.startsWith("https://")) &&
            !Uri.parse(url).host.isNullOrBlank()
        if (!urlValid) {
            binding.urlInputLayout.error = "URL harus valid dan diawali http:// atau https://"
            valid = false
        } else {
            binding.urlInputLayout.error = null
        }

        if (apiKey.isBlank()) {
            binding.apiKeyInputLayout.error = "API key wajib diisi"
            valid = false
        } else {
            binding.apiKeyInputLayout.error = null
        }

        return valid
    }

    private fun saveAndStart() {
        if (!validateConfig()) return
        val url = binding.urlInput.text?.toString()?.trim().orEmpty()
        val apiKey = binding.apiKeyInput.text?.toString()?.trim().orEmpty()
        val device = binding.deviceInput.text?.toString()?.trim().orEmpty()
            .ifBlank { Prefs.deviceId(this) }

        binding.deviceInput.setText(device)
        Prefs.saveConfig(this, url, apiKey, device, selectedPackages)

        val shouldRun = binding.switchService.isChecked
        Prefs.setServiceEnabled(this, shouldRun)
        if (shouldRun) {
            if (!Prefs.hasNotificationAccess(this)) {
                Toast.makeText(
                    this,
                    "Konfigurasi disimpan. Aktifkan akses notifikasi untuk mulai.",
                    Toast.LENGTH_LONG,
                ).show()
                openNotificationAccessSettings()
                return
            }
            requestNotificationPermission()
            KeeperService.start(this)
        } else {
            KeeperService.stop(this)
        }
        Toast.makeText(this, "Konfigurasi tersimpan.", Toast.LENGTH_SHORT).show()
    }

    private fun sendTestCallback() {
        if (!validateConfig()) return
        val url = binding.urlInput.text?.toString()?.trim().orEmpty()
        val apiKey = binding.apiKeyInput.text?.toString()?.trim().orEmpty()
        val device = binding.deviceInput.text?.toString()?.trim().orEmpty()
            .ifBlank { Prefs.deviceId(this) }

        val logId = UUID.randomUUID().toString()
        LogStore.add(
            this,
            NotificationLogEntry(
                id = logId,
                timestamp = System.currentTimeMillis(),
                packageName = "com.notipay.listener.test",
                appLabel = "Tes koneksi",
                title = "Tes koneksi",
                text = "Tes callback dari NotiPay Listener.",
                status = "Mengirim tes",
            ),
        )
        val queued = CallbackSender.enqueueTest(this, url, apiKey, device, logId)
        LogStore.updateStatus(
            this,
            logId,
            if (queued) "Antre callback" else "Gagal antre callback",
        )
        Toast.makeText(
            this,
            if (queued) "Tes callback dikirim lewat WorkManager." else "Lengkapi URL dan API key.",
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun refreshLogs() {
        val logs = LogStore.list(this)
        logAdapter.submit(logs)
        binding.logCount.text = "${logs.size} log"
        binding.logEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        binding.logRecycler.visibility = if (logs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun copyAllLogs() {
        val logs = LogStore.list(this)
        if (logs.isEmpty()) {
            Toast.makeText(this, "Belum ada log notifikasi.", Toast.LENGTH_SHORT).show()
            return
        }
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val text = logs.joinToString("\n\n") { entry ->
            buildString {
                append(formatter.format(Date(entry.timestamp)))
                append(" | ")
                append(entry.appLabel)
                append(" (")
                append(entry.packageName)
                append(")\n")
                if (entry.title.isNotBlank()) {
                    append(entry.title)
                    append(": ")
                }
                append(entry.text)
                append("\nStatus: ")
                append(entry.status)
            }
        }
        copyToClipboard("NotiPay logs", text)
        Toast.makeText(this, "Semua log disalin.", Toast.LENGTH_SHORT).show()
    }

    private fun confirmClearLogs() {
        if (LogStore.list(this).isEmpty()) {
            Toast.makeText(this, "Belum ada log.", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus semua log?")
            .setMessage("Riwayat notifikasi di console akan dihapus dari perangkat ini.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                LogStore.clear(this)
                Toast.makeText(this, "Log dibersihkan.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showLogDetail(entry: NotificationLogEntry) {
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val message = buildString {
            append(entry.text)
            append("\n\nAplikasi: ")
            append(entry.appLabel)
            append(" (")
            append(entry.packageName)
            append(")\nWaktu: ")
            append(formatter.format(Date(entry.timestamp)))
            append("\nStatus: ")
            append(entry.status)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.title.ifBlank { "Detail notifikasi" })
            .setMessage(message)
            .setNegativeButton("Tutup", null)
            .setNeutralButton("Salin") { _, _ ->
                copyToClipboard("NotiPay log", message)
                Toast.makeText(this, "Log disalin.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun copyToClipboard(label: String, value: String) {
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    private fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val manager = getSystemService(PowerManager::class.java)
        if (manager.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Optimasi baterai sudah dinonaktifkan.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
