package com.r2r.invoicescanner.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.r2r.invoicescanner.model.ExpenseCategory
import java.io.File
import java.io.IOException

/**
 * Saves invoice images to phone storage, one sub-folder per expense type:
 * Pictures/InvoiceScanner/<ExpenseType>/ (visible in Gallery and Files).
 */
object ImageStore {
    const val RELATIVE_PATH = "Pictures/InvoiceScanner"

    enum class Status { PRESENT, TRASHED, MISSING }

    /** Where an image currently is, as reported by the phone's media store. */
    data class Location(val folder: String, val displayName: String) {
        val path: String get() = "$folder/$displayName"
    }

    /** "XYZ/Fuel_Transport" (or just "Fuel_Transport" without a business): used on the phone and in exports. */
    fun subFolder(businessCode: String, category: ExpenseCategory): String =
        listOfNotNull(businessCode.ifBlank { null }, category.name).joinToString("/")

    fun folderFor(businessCode: String, category: ExpenseCategory): String = "$RELATIVE_PATH/${subFolder(businessCode, category)}"

    fun saveJpeg(context: Context, source: File, displayName: String, folder: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "$folder/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("could not create the file in phone storage")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("could not write to phone storage")
            out.use { o -> source.inputStream().use { it.copyTo(o) } }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /**
     * Whether the image file is still there. Deleting in a file manager usually moves it to the
     * phone's trash / recycle bin first, where the app could otherwise still open (and rename) it.
     */
    fun status(context: Context, uri: Uri): Status {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return if (uri.path?.let { File(it).exists() } == true) Status.PRESENT else Status.MISSING
        }
        val resolver = context.contentResolver
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
                val trashed = resolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_TRASHED), args, null)
                    ?.use { c -> if (c.moveToFirst()) c.getInt(0) == 1 else null }
                    ?: return Status.MISSING
                if (trashed) return Status.TRASHED
            } else {
                val found = resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                    ?.use { it.moveToFirst() } ?: false
                if (!found) return Status.MISSING
            }
            // The media-store row can outlive the file when another app deletes the file directly.
            resolver.openFileDescriptor(uri, "r")?.use { Status.PRESENT } ?: Status.MISSING
        } catch (e: SecurityException) {
            Status.PRESENT // can't tell; don't raise a false alarm
        } catch (e: Exception) {
            Status.MISSING
        }
    }

    fun location(context: Context, uri: Uri): Location? = try {
        val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) Location(c.getString(0).orEmpty().trimEnd('/'), c.getString(1).orEmpty()) else null
        }
    } catch (e: Exception) {
        null
    }

    /** Renames the image and, if [folder] is given, moves it there. */
    fun move(context: Context, uri: Uri, newName: String, folder: String?): Boolean = try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
            if (folder != null) put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/")
        }
        context.contentResolver.update(uri, values, null, null) > 0
    } catch (e: Exception) {
        false
    }

    /** Deletes the image, including one that sits in the phone's trash. */
    fun delete(context: Context, uri: Uri): Boolean = try {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
            resolver.delete(uri, args) > 0
        } else {
            resolver.delete(uri, null, null) > 0
        }
    } catch (e: Exception) {
        false
    }

    fun isInAppFolder(folder: String): Boolean =
        folder.equals(RELATIVE_PATH, ignoreCase = true) || folder.startsWith("$RELATIVE_PATH/", ignoreCase = true)
}
