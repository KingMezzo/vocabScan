package de.vocabscan.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import de.vocabscan.MainActivity
import de.vocabscan.update.AndroidUpdates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UpdateAppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val updates = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val onboarding = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    private lateinit var oldUpdates: Map<String, *>
    private lateinit var oldOnboarding: Map<String, *>
    private var activity: ActivityScenario<MainActivity>? = null

    @Before fun prepare() {
        oldUpdates = updates.all
        oldOnboarding = onboarding.all
        // Skip the network in app-flow tests; checker timing has independent tests.
        updates.edit().clear().putLong("last_attempt", System.currentTimeMillis()).commit()
        onboarding.edit().putBoolean("completed", true).commit()
    }

    @After fun restore() {
        activity?.close()
        restorePreferences(updates, oldUpdates)
        restorePreferences(onboarding, oldOnboarding)
    }

    @Test fun upgradeNotesSurviveRotationThenStayAcknowledgedAfterRestart() {
        updates.edit().putLong("seen_version", AndroidUpdates(context).installedCode - 1).commit()
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Was ist neu").fetchSemanticsNodes().isNotEmpty() }
        activity!!.recreate()
        compose.onNodeWithText("Was ist neu").assertIsDisplayed()
        compose.onNodeWithText("Alles klar").performClick()
        compose.waitUntil(5_000) { updates.getLong("seen_version", 0) == AndroidUpdates(context).installedCode }
        activity!!.close()
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Was ist neu").assertDoesNotExist()
    }

    @Test fun devSettingsDisplayInstalledVersionAndExplainDisabledUpdates() {
        val installed = AndroidUpdates(context)
        updates.edit().putLong("seen_version", installed.installedCode).commit()
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Updateprüfung ist in der DEV-Version deaktiviert."))
        compose.onNodeWithText("Updateprüfung ist in der DEV-Version deaktiviert.").assertIsDisplayed()
        compose.onNodeWithText("Nach Updates suchen").assertDoesNotExist()
        compose.onNodeWithContentDescription("Automatisch nach Updates suchen").assertDoesNotExist()
        compose.onNodeWithText("Installiert: ${installed.installedName} (${installed.installedCode})").assertIsDisplayed()
    }

    @Test fun devBuildDisablesBothChecksEvenWithAutomaticPreferenceEnabled() = runBlocking {
        updates.edit().putBoolean("automatic_enabled", true).putLong("last_attempt", 0L).commit()
        val installed = AndroidUpdates(context)
        assertFalse(installed.enabled)
        assertFalse(installed.automaticEnabled)
        installed.automaticEnabled = true
        assertFalse(installed.automaticEnabled)
        assertNull(installed.checker.check())
        assertNull(installed.checker.check(manual = true))
        assertEquals(0L, updates.getLong("last_attempt", -1))
    }

    private fun restorePreferences(preferences: SharedPreferences, values: Map<String, *>) {
        preferences.edit().clear().apply {
            values.forEach { (key, value) ->
                when (value) {
                    is Long -> putLong(key, value)
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                }
            }
        }.commit()
    }
}
