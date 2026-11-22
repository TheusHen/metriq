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

    fun delete(id: Long) {
        val row = db.evolutionQueries.selectPhotos().executeAsList().firstOrNull { it.id == id }
        db.evolutionQueries.deletePhoto(id)
        row?.let { File(it.path).delete() }
        notifier.notify(DataType.PHOTO)
    }
}
