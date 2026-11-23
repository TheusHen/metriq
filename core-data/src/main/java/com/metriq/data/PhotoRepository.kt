package com.metriq.data

import com.metriq.data.db.MetriqDatabase
import java.io.File

class PhotoRepository(
    private val db: MetriqDatabase,
    private val directory: File,
    private val notifier: DataChangeNotifier,
) {
    init { directory.mkdirs() }

    fun all() = db.evolutionQueries.selectPhotos().executeAsList()

    fun forDate(date: String) = db.evolutionQueries.selectPhotosByDate(date).executeAsList()

    fun add(date: String, sourcePath: String, pose: String?, note: String?): String {
        val target = File(directory, "${date}_${System.currentTimeMillis()}.jpg")
        File(sourcePath).copyTo(target, overwrite = true)
        db.evolutionQueries.insertPhoto(date, target.absolutePath, pose, note)
        notifier.notify(DataType.PHOTO)
        return target.absolutePath
    }

    /**
     * Deletes a photo and its file. Returns true when both the row and the file are
     * gone. If the file cannot be removed the row is kept, so the disk file never
     * becomes an unreferenced orphan; the false result reports the failure to callers.
     */
    fun delete(id: Long): Boolean {
        val row = db.evolutionQueries.selectPhotoById(id).executeAsOneOrNull()
            ?: return true
        val file = File(row.path)
        val fileRemoved = !file.exists() || runCatching { file.delete() }.getOrDefault(false)
        if (!fileRemoved) return false
        db.evolutionQueries.deletePhoto(id)
        notifier.notify(DataType.PHOTO)
        return true
    }
}
