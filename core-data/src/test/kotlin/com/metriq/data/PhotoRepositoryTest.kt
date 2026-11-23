package com.metriq.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoRepositoryTest {

    @Test
    fun `delete removes the photo row and its file`() {
        val store = newTestStore()
        val source = File.createTempFile("photo-source", ".jpg")
        source.writeText("image")
        val path = store.photo.add("2026-06-01", source.absolutePath, null, null)
        val id = store.photo.all().single().id

        assertTrue(store.photo.delete(id))
        assertTrue(store.photo.all().isEmpty())
        assertFalse(File(path).exists())
    }

    @Test
    fun `delete keeps the row when the file cannot be removed so no orphan is left`() {
        val store = newTestStore()
        // A non-empty directory cannot be deleted by File.delete() -> simulate a
        // failing file deletion without depending on OS file locks.
        val dir = File(System.getProperty("java.io.tmpdir"), "metriq-photo-${System.nanoTime()}")
        dir.mkdirs()
        File(dir, "child.txt").writeText("x")
        store.db.evolutionQueries.insertPhoto("2026-06-02", dir.absolutePath, null, null)
        val id = store.photo.all().single().id

        assertFalse(store.photo.delete(id))
        assertEquals(1, store.photo.all().size)
        assertTrue(dir.exists())
        dir.deleteRecursively()
    }
}
