package de.bgghome.webtrees.nativ

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Auf der JVM gibt es Dispatchers.Main nur mit kotlinx-coroutines-swing. Fehlt die
 * Abhaengigkeit, startet die App noch, aber der erste viewModelScope.launch stirbt
 * mit "Module with the Main dispatcher is missing" (so gesehen bei mpd-app).
 */
class MainDispatcherTest {
    @Test fun mainDispatcherVorhanden() = runBlocking {
        val thread = withContext(Dispatchers.Main) { Thread.currentThread().name }
        assertTrue(thread.contains("AWT-EventQueue"), "Main laeuft nicht auf dem AWT-Thread: $thread")
    }
}
