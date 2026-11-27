package de.vocabscan

import android.app.Application
import de.vocabscan.data.VocabDatabase
import de.vocabscan.data.VocabRepository
import de.vocabscan.update.AndroidUpdates

class VocabScanApplication : Application() {
    private val database by lazy { VocabDatabase.open(this) }
    val repository by lazy { VocabRepository(database) }
    val updates by lazy { AndroidUpdates(this) }
}
