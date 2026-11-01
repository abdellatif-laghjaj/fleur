package com.abdellatif.fleur

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.abdellatif.fleur.data.*
import com.abdellatif.fleur.platform.BudgetWidget
import com.abdellatif.fleur.platform.Reminders
import com.abdellatif.fleur.ui.*
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var vm: BudgetViewModel
    private var unlocked by mutableStateOf(false)
    private var restoring by mutableStateOf<BudgetState?>(null)
    private var pendingRange = DateRange(LocalDate.now().withDayOfMonth(1), LocalDate.now())
    private val credential =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            unlocked = result.resultCode == RESULT_OK
        }
    private val notifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.preferences { it.copy(reminders = true) }
            else message("Allow notifications in Android settings to receive reminders.")
        }
    private val backup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri
            ->
            uri?.let { writeExport(it, "backup") }
        }
    private val csv =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { writeExport(it, "csv") }
        }
    private val pdf =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri
            ->
            uri?.let { writeExport(it, "pdf") }
        }
    private val restore =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                lifecycleScope.launch {
                    try {
                        restoring =
                            withContext(Dispatchers.IO) {
                                val text =
                                    contentResolver.openInputStream(uri)?.use { input ->
                                        val bytes = ByteArrayOutputStream()
                                        val buffer = ByteArray(8192)
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            require(bytes.size() + count <= 25_000_000) {
                                                "Backup is too large"
                                            }
                                            bytes.write(buffer, 0, count)
                                        }
                                        bytes.toString(Charsets.UTF_8.name())
                                    } ?: error("Unable to read the selected file")
                                StateCodec.decode(text)
                            }
                    } catch (e: Exception) {
                        message(e.message ?: "Unable to read backup")
                    }
                }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(this)[BudgetViewModel::class.java]
        lifecycleScope.launch { vm.errors.collect { message(it) } }
        savedInstanceState?.getString("exportStart")?.let { start ->
            pendingRange =
                DateRange(
                    LocalDate.parse(start),
                    LocalDate.parse(savedInstanceState.getString("exportEnd")),
                )
        }
        setContent {
            val state by vm.state.collectAsState()
            val preferences = state?.preferences ?: Preferences()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            LaunchedEffect(preferences.darkMode, systemDark) {
                val dark =
                    when (preferences.darkMode) {
                        "Dark" -> true
                        "Light" -> false
                        else -> systemDark
                    }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            LaunchedEffect(
                preferences.reminders,
                preferences.reminderHour,
                preferences.reminderMinute,
            ) {
                Reminders.schedule(this@MainActivity, preferences)
            }
            LaunchedEffect(preferences.appLock) {
                if (preferences.appLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            LaunchedEffect(state) {
                state?.let {
                    withContext(Dispatchers.IO) { BudgetWidget.update(this@MainActivity, it) }
                }
            }
            if (preferences.appLock && !unlocked)
                FleurDesign(preferences) {
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ScriptHeading("Fleur", size = 80)
                        Text("Your budget, just for you.")
                        Button(onClick = ::unlock, modifier = Modifier.padding(top = 28.dp)) {
                            Text("Unlock Fleur")
                        }
                    }
                }
            else
                FleurApp(
                    vm,
                    onExport = ::export,
                    onRestore = {
                        restore.launch(
                            arrayOf("application/json", "application/octet-stream", "text/plain"),
                        )
                    },
                    onLock = { enabled ->
                        if (!enabled) vm.preferences { it.copy(appLock = false) }
                        else if (getSystemService(KeyguardManager::class.java).isDeviceSecure) {
                            unlocked = true
                            vm.preferences { it.copy(appLock = true) }
                        } else message("Set up a screen lock on your device to enable App Lock.")
                    },
                    onReminders = { enabled ->
                        if (!enabled) vm.preferences { it.copy(reminders = false) }
                        else if (
                            Build.VERSION.SDK_INT >= 33 &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                        ) {
                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else vm.preferences { it.copy(reminders = true) }
                    },
                )
            restoring
                ?.takeIf { !preferences.appLock || unlocked }
                ?.let { candidate ->
                    FleurTheme(preferences) {
                        FleurAlert(
                            title = "Restore your budget?",
                            message =
                                "This replaces the current budget with ${candidate.accounts.size} accounts and ${candidate.entries.size} transactions. A recovery copy of the current budget will be kept on this device.",
                            onClose = { restoring = null },
                            confirmLabel = "Restore",
                            onConfirm = {
                                val candidateSafe =
                                    candidate.copy(
                                        preferences =
                                            candidate.preferences.copy(
                                                appLock =
                                                    candidate.preferences.appLock &&
                                                        getSystemService(
                                                                KeyguardManager::class.java,
                                                            )
                                                            .isDeviceSecure,
                                            ),
                                    )
                                lifecycleScope.launch {
                                    try {
                                        val before =
                                            vm.state.value ?: error("Budget is still loading")
                                        withContext(Dispatchers.IO) {
                                            val file =
                                                android.util.AtomicFile(
                                                    java.io.File(
                                                        filesDir,
                                                        "before-restore.json",
                                                    ),
                                                )
                                            val output = file.startWrite()
                                            try {
                                                output.write(
                                                    StateCodec.encode(before).toByteArray(),
                                                )
                                                file.finishWrite(output)
                                            } catch (e: Exception) {
                                                file.failWrite(output)
                                                throw e
                                            }
                                        }
                                        vm.change(
                                            {
                                                restoring = null
                                                message("Backup restored")
                                            },
                                        ) {
                                            candidateSafe
                                        }
                                    } catch (e: Exception) {
                                        message(e.message ?: "Restore failed")
                                    }
                                }
                            },
                        )
                    }
                }
        }
    }

    private fun message(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun unlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) {
            unlocked = true
            vm.preferences { it.copy(appLock = false) }
            return
        }
        @Suppress("DEPRECATION")
        val intent =
            keyguard.createConfirmDeviceCredentialIntent(
                "Unlock Fleur",
                "Use your device screen lock to open your budget",
            )
        if (intent != null) {
            credential.launch(intent)
        }
    }

    private fun export(type: String, range: DateRange) {
        pendingRange = range
        val stamp = LocalDate.now().toString()
        when (type) {
            "backup" -> backup.launch("Fleur-backup-$stamp.json")
            "csv" -> csv.launch("Fleur-transactions-$stamp.csv")
            "pdf" -> pdf.launch("Fleur-report-$stamp.pdf")
        }
    }

    private fun writeExport(uri: android.net.Uri, type: String) {
        val state = vm.state.value ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        when (type) {
                            "backup" ->
                                output.write(StateCodec.encode(state).toByteArray(Charsets.UTF_8))
                            "csv" ->
                                output.write(
                                    Exports.csv(state, pendingRange).toByteArray(Charsets.UTF_8),
                                )
                            "pdf" -> Exports.pdf(state, pendingRange, output)
                        }
                    } ?: error("Unable to write the selected file")
                }
                message("${if(type=="backup") "Backup" else "Export"} saved")
            } catch (e: Exception) {
                message(e.message ?: "Export failed")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportStart", pendingRange.start.toString())
        outState.putString("exportEnd", pendingRange.end.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        if (!isChangingConfigurations) unlocked = false
        super.onPause()
    }
}
