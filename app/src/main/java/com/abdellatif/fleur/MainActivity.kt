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
    private var importing by mutableStateOf(false)
    private val credential =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            unlocked = result.resultCode == RESULT_OK
        }
    private val notifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.preferences { it.copy(reminders = true) }
            else message("Allow notifications in Android settings to receive reminders.")
        }
    private val csv =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { writeExport(it) }
        }
    private val importCsv =
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
                                                "CSV is too large"
                                            }
                                            bytes.write(buffer, 0, count)
                                        }
                                        Charsets.UTF_8.newDecoder()
                                            .decode(java.nio.ByteBuffer.wrap(bytes.toByteArray()))
                                            .toString()
                                    } ?: error("Unable to read the selected file")
                                CsvImport.decode(
                                    text,
                                    vm.state.value?.preferences ?: error("Budget is still loading"),
                                )
                            }
                    } catch (e: Exception) {
                        message(e.message ?: "Unable to read CSV")
                    }
                }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(this)[BudgetViewModel::class.java]
        lifecycleScope.launch { vm.errors.collect { message(it) } }
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
                    onExport = { csv.launch("Fleur-transactions-${LocalDate.now()}.csv") },
                    onImport = {
                        importCsv.launch(
                            arrayOf(
                                "text/*",
                                "application/csv",
                                "application/vnd.ms-excel",
                                "application/octet-stream",
                            ),
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
                            title = "Import CSV?",
                            message =
                                "${candidate.accounts.size} account${if (candidate.accounts.size == 1) "" else "s"} and ${candidate.entries.size} transaction${if (candidate.entries.size == 1) "" else "s"}. Balance through today: ${formatMoney(candidate.accounts.sumOf { candidate.accountBalance(it.id) }, candidate.preferences.copy(decimals = true))}.\n\nThis fresh import replaces your accounts, transactions and budgets. Settings are kept, and a recovery copy is saved. CSV rows become individual transactions; repeat descriptions remain in notes.",
                            onClose = { if (!importing) restoring = null },
                            confirmLabel = if (importing) "Importing..." else "Import",
                            onConfirm = {
                                if (!importing) {
                                    importing = true
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
                                                onSuccess = {
                                                    restoring = null
                                                    importing = false
                                                    message("CSV imported")
                                                },
                                                onFailure = { importing = false },
                                            ) {
                                                candidate.copy(
                                                    preferences =
                                                        candidate.preferences.copy(decimals = true),
                                                )
                                            }
                                        } catch (e: Exception) {
                                            importing = false
                                            message(e.message ?: "Import failed")
                                        }
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

    private fun writeExport(uri: android.net.Uri) {
        val state = vm.state.value ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val data =
                        Exports.csv(state, DateRange(LocalDate.of(1900, 1, 1), LocalDate.now()))
                            .toByteArray(Charsets.UTF_8)
                    contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        output.write(data)
                    } ?: error("Unable to write the selected file")
                }
                message("CSV exported")
            } catch (e: Exception) {
                message(e.message ?: "Export failed")
            }
        }
    }

    override fun onPause() {
        if (!isChangingConfigurations) unlocked = false
        super.onPause()
    }
}
