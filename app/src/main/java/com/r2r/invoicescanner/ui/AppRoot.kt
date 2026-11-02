package com.r2r.invoicescanner.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.r2r.invoicescanner.extract.DocumentScanner
import com.r2r.invoicescanner.storage.ImageStore
import com.r2r.invoicescanner.ui.screens.HomeScreen
import com.r2r.invoicescanner.ui.screens.LockScreen
import com.r2r.invoicescanner.ui.screens.MfaSetupScreen
import com.r2r.invoicescanner.ui.screens.ProcessingScreen
import com.r2r.invoicescanner.ui.screens.ReviewScreen
import com.r2r.invoicescanner.ui.screens.SettingsScreen

@Composable
fun InvoiceScannerApp(vm: MainViewModel) {
    val context = LocalContext.current
    val activity = context as Activity

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri
            if (uri != null) vm.onScanned(uri) else vm.show("No page was captured — please try again.")
        }
    }

    val startScan: () -> Unit = {
        DocumentScanner.client(vm.imageStyle).getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                vm.show("The scanner could not start (${e.localizedMessage ?: "Google Play services is required"}).")
            }
    }

    // App lock: everything above (the scanner result) keeps working underneath.
    if (vm.locked) {
        LockScreen(
            biometric = vm.biometricEnabled,
            authenticator = vm.mfaEnabled,
            onBiometricUnlocked = { vm.unlockWithBiometric() },
            onBiometricUnavailable = { vm.biometricUnavailableAtUnlock() },
            onUnlock = { vm.unlock(it) },
        )
        return
    }

    when (val screen = vm.screen) {
        is MainViewModel.Screen.Home -> HomeScreen(
            records = vm.records,
            shown = vm.filteredRecords,
            filter = vm.filter,
            categories = vm.categories,
            businesses = vm.businesses,
            imageStatus = vm.imageStatus,
            showAiBanner = !vm.settings.isConfigured && vm.showAiBanner,
            exporting = vm.exporting,
            onDismissAiBanner = { vm.dismissAiBanner() },
            onScan = startScan,
            onOpen = { vm.openRecord(it) },
            onExport = { withImages ->
                vm.export(withImages) { uri, mime ->
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, "Share invoice register"))
                }
            },
            onSettings = { vm.openSettings() },
            onFilterChange = { transform -> vm.updateFilter(transform) },
            onDelete = { ids -> vm.deleteRecords(ids) },
        )

        is MainViewModel.Screen.Processing -> ProcessingScreen(screen.step)

        is MainViewModel.Screen.Review -> {
            val draft = vm.draft
            if (draft == null) {
                LaunchedEffect(Unit) { vm.closeReview() }
            } else {
                ReviewScreen(
                    draft = draft,
                    validation = vm.validate(draft),
                    categories = vm.categories,
                    businesses = vm.businesses,
                    imageStatus = if (draft.isNew) ImageStore.Status.PRESENT else vm.statusOf(draft.recordId),
                    saving = vm.saving,
                    retrying = vm.retrying,
                    onChange = { transform -> vm.updateDraft(transform) },
                    onSave = { vm.save() },
                    onRetake = {
                        vm.discardDraft()
                        startScan()
                    },
                    onClose = { vm.closeReview() },
                    onDelete = { vm.deleteCurrent() },
                    onRetry = { vm.retryExtraction() },
                )
            }
        }

        is MainViewModel.Screen.Settings -> SettingsScreen(
            current = vm.settings,
            currentStyle = vm.imageStyle,
            categories = vm.categories,
            deletedCategories = vm.deletedCategories,
            testing = vm.testingConnection,
            connectionResult = vm.connectionResult,
            onSave = { ai, style -> vm.saveSettings(ai, style) },
            onTest = { vm.testConnection(it) },
            onRemoveCategory = { vm.removeCategory(it) },
            onRestoreCategory = { vm.restoreCategory(it) },
            onPurgeCategory = { vm.purgeCategory(it) },
            businesses = vm.businesses,
            defaultBusiness = vm.defaultBusiness,
            onAddBusiness = { code, name -> vm.addBusiness(code, name) },
            onDeleteBusiness = { vm.deleteBusiness(it) },
            onSetDefaultBusiness = { vm.chooseDefaultBusiness(it) },
            biometricEnabled = vm.biometricEnabled,
            onSetBiometric = { vm.setBiometric(it) },
            mfaEnabled = vm.mfaEnabled,
            recoveryCodesLeft = if (vm.mfaEnabled) vm.mfaRecoveryCodesLeft() else 0,
            onSetupMfa = { vm.openMfaSetup() },
            onDisableMfa = { vm.disableMfa() },
            mfaSetupKey = { vm.mfaSetupKey() },
            relockMinutes = vm.relockMinutes,
            onRelockMinutes = { vm.chooseRelockMinutes(it) },
            onBack = { vm.closeSettings() },
        )

        is MainViewModel.Screen.MfaSetup -> MfaSetupScreen(
            onEnable = { secret, codes -> vm.enableMfa(secret, codes) },
            onDone = { vm.closeMfaSetup() },
        )
    }
}
