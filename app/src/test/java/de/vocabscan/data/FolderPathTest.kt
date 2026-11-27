package de.vocabscan.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPathTest {
    @Test fun resolvesNestedPathIndependentlyOfFolderOrderAndScope() {
        val root = Folder("library", "Schule")
        val child = Folder("child", "Englisch", parentId = root.id)
        val archive = Folder("archive", "Schule", isArchived = true)
        val folders = listOf(child, archive, root)
        assertEquals(listOf(root, child), folderPath(folders, child.id))
        assertEquals(listOf(archive), folderPath(folders, archive.id))
        assertEquals(emptyList<Folder>(), folderPath(folders, null))
        assertEquals(emptyList<Folder>(), folderPath(folders, "deleted"))
    }
}
