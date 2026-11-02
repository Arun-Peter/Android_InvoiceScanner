package com.r2r.invoicescanner.settings

import android.content.Context
import com.r2r.invoicescanner.model.Business
import com.r2r.invoicescanner.model.ExpenseCategory
import org.json.JSONObject
import org.json.JSONArray

enum class ImageStyle(val label: String, val hint: String) {
    COLOUR(
        "Colour",
        "Saved as photographed (cropped and straightened). Best to keep stamps, logos and highlights.",
    ),
    BLACK_WHITE(
        "Black & white",
        "Converted to grayscale with a little extra contrast. Slightly smaller files; can help faded thermal receipts. " +
            "The scanner also offers its clean-up tools (remove stains, fingers).",
    );

    companion object {
        fun parse(value: String?): ImageStyle = entries.firstOrNull { it.name == value } ?: COLOUR
    }
}

/** General app preferences. */
object AppPrefs {
    private const val PREFS = "app_prefs"

    fun imageStyle(context: Context): ImageStyle =
        ImageStyle.parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("imageStyle", null))

    fun setImageStyle(context: Context, style: ImageStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("imageStyle", style.name).apply()
    }

    /** The "read on the device only" tip on the home screen is shown in the first session only. */
    fun aiBannerSeen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("aiBannerSeen", false)

    fun setAiBannerSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("aiBannerSeen", true).apply()
    }
}

/** The user's own "regular" expense categories, offered for every new scan and used for classification. */
object CategoryStore {
    private const val PREFS = "categories"

    fun loadCustom(context: Context): List<ExpenseCategory> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("custom", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ExpenseCategory(arr.getString(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Built-in heads the user deleted (by name); they can be restored from Settings. */
    fun loadHiddenBuiltIns(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("hiddenBuiltIns", emptySet()).orEmpty()

    fun saveHiddenBuiltIns(context: Context, names: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("hiddenBuiltIns", names).apply()
    }

    /** Built-in heads deleted permanently: not offered, and not listed for restoring. */
    fun loadPurgedBuiltIns(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("purgedBuiltIns", emptySet()).orEmpty()

    fun savePurgedBuiltIns(context: Context, names: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("purgedBuiltIns", names).apply()
    }

    /** User categories that were deleted but can still be restored. */
    fun loadDeletedCustom(context: Context): List<ExpenseCategory> = readList(context, "deletedCustom")

    fun saveDeletedCustom(context: Context, categories: List<ExpenseCategory>) = writeList(context, "deletedCustom", categories)

    private fun readList(context: Context, key: String): List<ExpenseCategory> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ExpenseCategory(arr.getString(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeList(context: Context, key: String, categories: List<ExpenseCategory>) {
        val arr = JSONArray()
        categories.filter { !it.builtIn }.distinct().forEach { arr.put(it.name) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, arr.toString()).apply()
    }

    fun saveCustom(context: Context, categories: List<ExpenseCategory>) {
        val arr = JSONArray()
        categories.filter { !it.builtIn }.distinct().forEach { arr.put(it.name) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("custom", arr.toString()).apply()
    }
}

/** The businesses invoices can be processed for, and the default one for new scans. */
object BusinessStore {
    private const val PREFS = "businesses"

    fun load(context: Context): List<Business> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Business(o.getString("code"), o.optString("name")) } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, list: List<Business>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("code", it.code).put("name", it.name)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", arr.toString()).apply()
    }

    fun defaultCode(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("default", null)

    fun setDefault(context: Context, code: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("default", code).apply()
    }
}
