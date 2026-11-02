package com.r2r.invoicescanner.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.r2r.invoicescanner.extract.AiExtractor
import com.r2r.invoicescanner.extract.AmountParser
import com.r2r.invoicescanner.extract.InvoiceProcessor
import com.r2r.invoicescanner.model.Business
import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.InvoiceRecord
import com.r2r.invoicescanner.model.RegisterFilter
import com.r2r.invoicescanner.model.ReviewDraft
import com.r2r.invoicescanner.security.AppLock
import com.r2r.invoicescanner.security.Base32
import com.r2r.invoicescanner.security.MfaStore
import com.r2r.invoicescanner.security.Totp
import com.r2r.invoicescanner.settings.AiProvider
import com.r2r.invoicescanner.settings.AiSettings
import com.r2r.invoicescanner.settings.AppPrefs
import com.r2r.invoicescanner.settings.BusinessStore
import com.r2r.invoicescanner.settings.CategoryStore
import com.r2r.invoicescanner.settings.ImageStyle
import com.r2r.invoicescanner.settings.SettingsStore
import com.r2r.invoicescanner.storage.CsvExporter
import com.r2r.invoicescanner.storage.FileNamer
import com.r2r.invoicescanner.storage.ImageStore
import com.r2r.invoicescanner.storage.InvoiceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface Screen {
        data object Home : Screen
        data class Processing(val step: String) : Screen
        data object Review : Screen
        data object Settings : Screen
        data object MfaSetup : Screen
    }

    data class Validation(
        val fileName: String?,
        val folder: String?,
        val errors: List<String>,
        val duplicateWarning: String?,
    )

    private val repo = InvoiceRepository(app)
    private val processor = InvoiceProcessor(app)

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var records by mutableStateOf<List<InvoiceRecord>>(emptyList())
        private set
    var draft by mutableStateOf<ReviewDraft?>(null)
        private set
    var settings by mutableStateOf(SettingsStore.load(app))
        private set
    var imageStyle by mutableStateOf(AppPrefs.imageStyle(app))
        private set
    var customCategories by mutableStateOf(CategoryStore.loadCustom(app))
        private set
    var hiddenBuiltIns by mutableStateOf(CategoryStore.loadHiddenBuiltIns(app))
        private set
    private var purgedBuiltIns by mutableStateOf(CategoryStore.loadPurgedBuiltIns(app))
    private var deletedCustom by mutableStateOf(CategoryStore.loadDeletedCustom(app))
    var businesses by mutableStateOf(BusinessStore.load(app))
        private set
    /** Business code preselected for new scans. */
    var defaultBusiness by mutableStateOf(BusinessStore.defaultCode(app)?.takeIf { c -> businesses.any { it.code == c } })
        private set
    var exporting by mutableStateOf(false)
        private set

    // App lock (biometrics and/or the authenticator app): locked at start-up and after a while in the background.
    var mfaEnabled by mutableStateOf(MfaStore.isEnabled(app))
        private set
    var biometricEnabled by mutableStateOf(AppLock.biometricEnabled(app))
        private set
    var relockMinutes by mutableStateOf(AppLock.relockMinutes(app))
        private set
    val lockEnabled: Boolean get() = mfaEnabled || biometricEnabled
    var locked by mutableStateOf(lockEnabled)
        private set
    private var backgroundSince = 0L

    /** The "read on the device only" tip: shown in the first session and after switching back from AI (dismissible). */
    var showAiBanner by mutableStateOf(!AppPrefs.aiBannerSeen(app))
        private set

    /** Result of the last "Test connection", shown under the button: (succeeded, text). */
    var connectionResult by mutableStateOf<Pair<Boolean, String>?>(null)
        private set
    var filter by mutableStateOf(RegisterFilter())
        private set
    /** Record id -> state of its image file, refreshed whenever the app comes to the front. */
    var imageStatus by mutableStateOf<Map<String, ImageStore.Status>>(emptyMap())
        private set
    var saving by mutableStateOf(false)
        private set
    var testingConnection by mutableStateOf(false)
        private set

    /** Built-in heads of expense (minus any the user deleted) followed by the user's regular ones. */
    val categories: List<ExpenseCategory>
        get() = ExpenseCategory.BUILT_IN.filter { it.name !in hiddenBuiltIns && it.name !in purgedBuiltIns } + customCategories

    /** Deleted categories that can still be restored (built-in and the user's own). */
    val deletedCategories: List<ExpenseCategory>
        get() = (ExpenseCategory.BUILT_IN.filter { it.name in hiddenBuiltIns && it.name !in purgedBuiltIns } + deletedCustom)
            .sortedBy { it.name.lowercase() }

    val filteredRecords: List<InvoiceRecord> get() = filter.apply(records)

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                InvoiceProcessor.cleanupPending(app)
                repo.load()
            }
            records = loaded.sortedByDescending { it.scannedAt }
            refreshImageStatus()
        }
        AppPrefs.setAiBannerSeen(app)
    }

    // ------------------------------------------------------------ messages

    /** Messages are toasts: they disappear on their own and never cover buttons. */
    fun show(text: String) = toast(text)

    fun dismissAiBanner() {
        showAiBanner = false
    }

    // ------------------------------------------------------------ image files

    /** Images can be deleted, trashed or moved in a file manager while the app is in the background. */
    fun refreshImageStatus() {
        val list = records
        if (list.isEmpty()) return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            imageStatus = withContext(Dispatchers.IO) {
                list.associate { it.id to ImageStore.status(ctx, Uri.parse(it.imageUri)) }
            }
        }
    }

    fun statusOf(recordId: String?): ImageStore.Status =
        recordId?.let { imageStatus[it] } ?: ImageStore.Status.PRESENT

    // ------------------------------------------------------------ scanning

    fun onScanned(uri: Uri) {
        screen = Screen.Processing("Preparing the cropped image…")
        viewModelScope.launch {
            try {
                val result = processor.process(uri, settings, categories, imageStyle) { step ->
                    screen = Screen.Processing(step)
                }
                draft = result.copy(businessCode = defaultBusiness.orEmpty())
                screen = Screen.Review
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                screen = Screen.Home
                show("Could not process the scan: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    fun updateDraft(transform: (ReviewDraft) -> ReviewDraft) {
        draft = draft?.let(transform)
    }

    fun discardDraft() {
        draft?.pendingFilePath?.let { path -> File(path).delete() }
        draft = null
        screen = Screen.Home
    }

    fun openRecord(r: InvoiceRecord) {
        draft = ReviewDraft(
            recordId = r.id,
            imageUri = r.imageUri,
            savedFileName = r.fileName,
            invoiceNo = r.invoiceNo,
            date = r.date,
            vendorName = r.vendorName,
            currency = r.currency,
            subtotal = AmountParser.plain(r.subtotal),
            tax = AmountParser.plain(r.tax),
            amount = AmountParser.plain(r.amount),
            read = r.read,
            category = r.category,
            aiCategory = r.aiCategory,
            categoryReason = r.categoryReason,
            newCategory = r.category.takeIf { it !in categories },
            amountConfidence = r.amountConfidence,
            findings = r.findings,
            comment = r.comment,
            engine = r.engine,
            scannedAt = r.scannedAt,
            updatedAt = r.updatedAt,
            businessCode = r.businessCode,
            narration = r.narration,
        )
        screen = Screen.Review
        refreshImageStatus()
    }

    var retrying by mutableStateOf(false)
        private set

    /**
     * Reads the current image again (OCR and, if set up, the AI), e.g. after the AI service was
     * unreachable or the result looked wrong. Replaces the values on the Review screen; nothing is
     * saved until the user taps Save / Update.
     */
    fun retryExtraction() {
        val d = draft ?: return
        if (retrying) return
        retrying = true
        viewModelScope.launch {
            var temp: File? = null
            try {
                val file = d.pendingFilePath?.let { File(it) }
                    ?: withContext(Dispatchers.IO) { processor.copyForRetry(Uri.parse(d.imageUri)) }.also { temp = it }
                val fresh = processor.analyse(file, settings, categories) { }
                draft = fresh.copy(
                    recordId = d.recordId,
                    imageUri = d.imageUri,
                    pendingFilePath = d.pendingFilePath,
                    savedFileName = d.savedFileName,
                    scannedAt = d.scannedAt,
                    updatedAt = d.updatedAt,
                    businessCode = d.businessCode,
                    narration = d.narration,
                )
                show("Read again — ${fresh.engine}, amount confidence ${fresh.amountConfidence}%")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show("Could not read the invoice again: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                temp?.delete()
                retrying = false
            }
        }
    }

    fun closeReview() {
        if (draft?.isNew == true) discardDraft() else {
            draft = null
            screen = Screen.Home
        }
    }

    // ------------------------------------------------------------ validation

    fun validate(d: ReviewDraft): Validation {
        val errors = mutableListOf<String>()
        val date = runCatching { LocalDate.parse(d.date.trim()) }.getOrNull()
        if (date == null) errors += "Enter the invoice date as YYYY-MM-DD (or use the calendar)"
        val amount = AmountParser.parse(d.amount)
        if (amount == null) errors += "Enter the amount"
        val category = d.category
        if (category == null) errors += "Choose an expense type"
        if (d.businessCode.isBlank() && businesses.isNotEmpty()) errors += "Choose the business"
        if (d.subtotal.isNotBlank() && AmountParser.parse(d.subtotal) == null) errors += "Subtotal is not a valid number"
        if (d.tax.isNotBlank() && AmountParser.parse(d.tax) == null) errors += "Tax is not a valid number"

        val others = records.filter { it.id != d.recordId }
        var fileName: String? = null
        var warning: String? = null
        if (date != null && amount != null && category != null) {
            val base = FileNamer.build(date, category, d.vendorName, amount, d.businessCode)
            fileName = FileNamer.uniquify(base, others.map { it.fileName }.toSet())
            if (fileName != base) warning = "A file named $base already exists — this may be a duplicate invoice."
        }
        val invNo = d.invoiceNo.trim()
        if (invNo.isNotEmpty()) {
            val dup = others.firstOrNull {
                it.invoiceNo.equals(invNo, ignoreCase = true) &&
                    it.vendorName.trim().equals(d.vendorName.trim(), ignoreCase = true)
            }
            if (dup != null) warning = "Invoice no. $invNo from this vendor was already saved as ${dup.fileName}."
        }
        return Validation(fileName, category?.let { ImageStore.folderFor(d.businessCode, it) }, errors, warning)
    }

    // ------------------------------------------------------------ save / delete

    fun save() {
        val d = draft ?: return
        val v = validate(d)
        val fileName = v.fileName
        if (v.errors.isNotEmpty() || fileName == null) {
            show(v.errors.firstOrNull() ?: "Please complete the required fields")
            return
        }
        saving = true
        viewModelScope.launch {
            try {
                val (record, note) = withContext(Dispatchers.IO) { persist(d, fileName) }
                val updated = (records.filterNot { it.id == record.id } + record).sortedByDescending { it.scannedAt }
                withContext(Dispatchers.IO) { repo.saveAll(updated) }
                records = updated
                val cat = record.category
                if (d.keepNewCategory && cat == d.newCategory && cat !in categories) {
                    storeCustomCategories(customCategories + cat)
                }
                draft = null
                screen = Screen.Home
                show(note)
                refreshImageStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show("Could not save: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                saving = false
            }
        }
    }

    /** Writes / renames / moves the image and builds the record. Returns the record and the message to show. */
    private fun persist(d: ReviewDraft, fileName: String): Pair<InvoiceRecord, String> {
        val ctx = getApplication<Application>()
        val amount = AmountParser.parse(d.amount) ?: error("Amount missing")
        val category = d.category ?: error("Expense type missing")
        val targetFolder = ImageStore.folderFor(d.businessCode, category)
        val note: String
        val uri: Uri
        val actualName: String
        if (d.isNew) {
            val src = File(d.pendingFilePath ?: error("The scanned image is missing"))
            uri = ImageStore.saveJpeg(ctx, src, fileName, targetFolder)
            val loc = ImageStore.location(ctx, uri)
            actualName = loc?.displayName ?: fileName
            src.delete()
            note = "Saved as $actualName"
        } else {
            uri = Uri.parse(d.imageUri)
            val old = d.savedFileName.orEmpty()
            val status = ImageStore.status(ctx, uri)
            val loc = if (status == ImageStore.Status.PRESENT) ImageStore.location(ctx, uri) else null
            when {
                status == ImageStore.Status.TRASHED -> {
                    actualName = old
                    note = "Updated. The image is in the phone's trash, so it was not renamed"
                }
                loc == null -> {
                    actualName = old
                    note = "Updated. The image file is no longer on the phone"
                }
                else -> {
                    // Files the user moved out of the app's folder are renamed where they are.
                    val folder = if (ImageStore.isInAppFolder(loc.folder)) targetFolder else loc.folder
                    if (loc.displayName == fileName && loc.folder.equals(folder, ignoreCase = true)) {
                        actualName = fileName
                        note = "Details updated"
                    } else if (ImageStore.move(ctx, uri, fileName, folder.takeIf { it != loc.folder })) {
                        val now = ImageStore.location(ctx, uri)
                        actualName = now?.displayName ?: fileName
                        note = if (now != null) "Updated — saved as ${now.displayName}" else "Details updated"
                    } else {
                        actualName = loc.displayName
                        note = "Updated, but the image could not be renamed"
                    }
                }
            }
        }
        val record = InvoiceRecord(
            id = d.recordId ?: UUID.randomUUID().toString(),
            fileName = actualName,
            imageUri = uri.toString(),
            invoiceNo = d.invoiceNo.trim(),
            date = d.date.trim(),
            vendorName = d.vendorName.trim(),
            currency = d.currency.trim().uppercase(),
            subtotal = AmountParser.parse(d.subtotal),
            tax = AmountParser.parse(d.tax),
            amount = amount,
            read = d.read,
            category = category,
            aiCategory = d.aiCategory,
            categoryReason = d.categoryReason,
            amountConfidence = d.amountConfidence,
            findings = d.findings,
            comment = d.comment,
            engine = d.engine,
            scannedAt = d.scannedAt,
            updatedAt = if (d.isNew) null else System.currentTimeMillis(),
            businessCode = d.businessCode,
            narration = d.narration.trim(),
        )
        return record to note
    }

    fun deleteCurrent() {
        val d = draft ?: return
        val id = d.recordId ?: return
        viewModelScope.launch {
            val updated = records.filterNot { it.id == id }
            val removed = withContext(Dispatchers.IO) {
                val ok = ImageStore.delete(getApplication<Application>(), Uri.parse(d.imageUri))
                repo.saveAll(updated)
                ok
            }
            records = updated
            imageStatus = imageStatus - id
            draft = null
            screen = Screen.Home
            show(if (removed) "Invoice deleted" else "Removed from the register (the image file was not found)")
        }
    }

    /** Bulk delete from the home screen: removes the rows and deletes their images. */
    fun deleteRecords(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val targets = records.filter { it.id in ids }
            val updated = records.filter { it.id !in ids }
            val notFound = withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                val missing = targets.count { !ImageStore.delete(ctx, Uri.parse(it.imageUri)) }
                repo.saveAll(updated)
                missing
            }
            records = updated
            imageStatus = imageStatus - ids
            val n = targets.size
            show(
                "Deleted $n invoice${if (n == 1) "" else "s"}" +
                    if (notFound > 0) " ($notFound image${if (notFound == 1) " was" else "s were"} already gone)" else ""
            )
        }
    }

    // ------------------------------------------------------------ register filters

    fun updateFilter(transform: (RegisterFilter) -> RegisterFilter) {
        filter = transform(filter)
    }

    // ------------------------------------------------------------ export

    /**
     * Exports the invoices currently shown (i.e. after the home-screen filters): either the CSV
     * alone, or a ZIP with the CSV and every invoice image in BusinessCode/ExpenseType folders.
     * [onReady] gets the file and its MIME type for sharing.
     */
    fun export(withImages: Boolean, onReady: (Uri, String) -> Unit) {
        val list = filteredRecords
        if (list.isEmpty()) {
            show(if (records.isEmpty()) "Nothing to export yet — scan an invoice first" else "No invoices match the filters")
            return
        }
        if (exporting) return
        val names = businesses.associate { it.code to it.name }
        val noun = if (list.size == 1) "invoice" else "invoices"
        val scope = if (filter.isActive) "${list.size} filtered $noun" else "${list.size} $noun"
        exporting = true
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                if (withImages) {
                    val r = withContext(Dispatchers.IO) { CsvExporter.exportZip(ctx, list, names) }
                    show(
                        "ZIP of $scope saved to Downloads/InvoiceScanner" +
                            if (r.missing > 0) " (${r.missing} image${if (r.missing == 1) "" else "s"} missing)" else ""
                    )
                    onReady(r.uri, "application/zip")
                } else {
                    val uri = withContext(Dispatchers.IO) { CsvExporter.export(ctx, list, names) }
                    show("CSV of $scope saved to Downloads/InvoiceScanner")
                    onReady(uri, "text/csv")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show("Export failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                exporting = false
            }
        }
    }

    // ------------------------------------------------------------ businesses

    /** Returns an error message, or null when the business was added. The first one becomes the default. */
    fun addBusiness(code: String, name: String): String? {
        Business.validate(code, name, businesses)?.let { return it }
        val b = Business(Business.cleanCode(code), name.trim().take(40))
        businesses = businesses + b
        BusinessStore.save(getApplication<Application>(), businesses)
        if (defaultBusiness == null) chooseDefaultBusiness(b.code)
        show("${b.label} added")
        return null
    }

    fun deleteBusiness(code: String) {
        businesses = businesses.filterNot { it.code == code }
        BusinessStore.save(getApplication<Application>(), businesses)
        if (defaultBusiness == code) chooseDefaultBusiness(businesses.firstOrNull()?.code)
        show("$code deleted (saved invoices keep it)")
    }

    fun chooseDefaultBusiness(code: String?) {
        defaultBusiness = code
        BusinessStore.setDefault(getApplication<Application>(), code)
    }

    // ------------------------------------------------------------ app lock (MFA)

    fun onAppBackground() {
        backgroundSince = System.currentTimeMillis()
    }

    fun onAppForeground() {
        if (lockEnabled && backgroundSince > 0 &&
            relockMinutes != AppLock.NEVER &&
            System.currentTimeMillis() - backgroundSince >= relockMinutes * 60_000L
        ) {
            locked = true
        }
        backgroundSince = 0L
    }

    /** Unlocks with the authenticator code or a recovery code. */
    fun unlock(input: String): Boolean {
        val ctx = getApplication<Application>()
        val ok = MfaStore.check(ctx, input)
        if (ok) {
            locked = false
            val left = MfaStore.recoveryCodesLeft(ctx)
            if (input.any { it.isLetter() }) show("Recovery code used — $left left")
        }
        return ok
    }

    /** Called after the system's fingerprint / face / screen-lock prompt succeeded. */
    fun unlockWithBiometric() {
        locked = false
    }

    /**
     * Biometric unlock was turned on, but the phone no longer has a fingerprint, face or screen lock.
     * Without the authenticator app there is no other way in, so the app opens (the phone itself is unprotected).
     */
    fun biometricUnavailableAtUnlock() {
        if (!mfaEnabled) {
            locked = false
            show("Biometric unlock skipped — this phone has no screen lock any more")
        }
    }

    /** Turning biometrics on happens after a successful prompt (done by the screen). */
    fun setBiometric(on: Boolean) {
        AppLock.setBiometricEnabled(getApplication(), on)
        biometricEnabled = on
        show(if (on) "Biometric unlock on" else "Biometric unlock off")
    }

    fun chooseRelockMinutes(minutes: Int) {
        AppLock.setRelockMinutes(getApplication(), minutes)
        relockMinutes = AppLock.relockMinutes(getApplication())
    }

    /** The authenticator secret as typed into an authenticator app, and its otpauth:// link (for the QR code). */
    fun mfaSetupKey(): Pair<String, String>? {
        val secret = MfaStore.secret(getApplication()) ?: return null
        return Base32.grouped(secret) to Totp.uri(secret, "App lock")
    }

    fun openMfaSetup() {
        screen = Screen.MfaSetup
    }

    fun closeMfaSetup() {
        screen = Screen.Settings
    }

    /** Called when the user has entered a correct code for the new secret. */
    fun enableMfa(secret: ByteArray, recoveryCodes: List<String>): Boolean {
        val ok = MfaStore.enable(getApplication<Application>(), secret, recoveryCodes)
        if (ok) mfaEnabled = true else toast("Could not store the secret securely on this phone")
        return ok
    }

    /**
     * Turned off from Settings, which can only be reached with the app unlocked, so no code is asked for
     * (the authenticator app may already have been removed).
     */
    fun disableMfa() {
        MfaStore.disable(getApplication())
        mfaEnabled = false
        show("Authenticator app lock off")
    }

    fun mfaRecoveryCodesLeft(): Int = MfaStore.recoveryCodesLeft(getApplication<Application>())

    // ------------------------------------------------------------ settings

    fun openSettings() {
        connectionResult = null
        screen = Screen.Settings
    }

    fun closeSettings() {
        screen = Screen.Home
    }

    fun saveSettings(s: AiSettings, style: ImageStyle) {
        val ctx = getApplication<Application>()
        SettingsStore.save(ctx, s)
        AppPrefs.setImageStyle(ctx, style)
        if (settings.provider != AiProvider.NONE && s.provider == AiProvider.NONE) showAiBanner = true
        settings = s
        imageStyle = style
        screen = Screen.Home
        show(if (s.isConfigured) "Settings saved" else "Settings saved — invoices will be read on the device")
    }

    fun removeCategory(cat: ExpenseCategory) {
        val ctx = getApplication<Application>()
        if (cat.builtIn) {
            hiddenBuiltIns = hiddenBuiltIns + cat.name
            CategoryStore.saveHiddenBuiltIns(ctx, hiddenBuiltIns)
        } else {
            storeCustomCategories(customCategories - cat)
            deletedCustom = (deletedCustom - cat) + cat
            CategoryStore.saveDeletedCustom(ctx, deletedCustom)
        }
        show("${cat.name} deleted (saved invoices keep it)")
    }

    fun restoreCategory(cat: ExpenseCategory) {
        val ctx = getApplication<Application>()
        if (cat.builtIn) {
            hiddenBuiltIns = hiddenBuiltIns - cat.name
            CategoryStore.saveHiddenBuiltIns(ctx, hiddenBuiltIns)
        } else {
            deletedCustom = deletedCustom - cat
            CategoryStore.saveDeletedCustom(ctx, deletedCustom)
            if (cat !in customCategories) storeCustomCategories(customCategories + cat)
        }
        show("${cat.name} restored")
    }

    /** Removes a deleted category from the restore list for good. Saved invoices still keep it. */
    fun purgeCategory(cat: ExpenseCategory) {
        val ctx = getApplication<Application>()
        if (cat.builtIn) {
            purgedBuiltIns = purgedBuiltIns + cat.name
            CategoryStore.savePurgedBuiltIns(ctx, purgedBuiltIns)
        } else {
            deletedCustom = deletedCustom - cat
            CategoryStore.saveDeletedCustom(ctx, deletedCustom)
        }
        show("${cat.name} permanently deleted")
    }

    private fun storeCustomCategories(list: List<ExpenseCategory>) {
        CategoryStore.saveCustom(getApplication<Application>(), list)
        customCategories = list
    }

    /** The result is shown as a toast (which doesn't block taps) and in full under the button. */
    fun testConnection(s: AiSettings) {
        if (!s.isConfigured) {
            toast("Fill in all the fields first")
            return
        }
        testingConnection = true
        connectionResult = null
        viewModelScope.launch {
            val result = try {
                true to AiExtractor(s).testConnection()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false to "Connection failed: ${AiExtractor.friendlyError(e)}"
            } finally {
                testingConnection = false
            }
            connectionResult = result
            toast(result.second)
        }
    }

    private fun toast(text: String) {
        Toast.makeText(getApplication(), text, if (text.length > 60) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
