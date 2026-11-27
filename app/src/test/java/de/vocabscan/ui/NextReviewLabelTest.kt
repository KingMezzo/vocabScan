package de.vocabscan.ui

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NextReviewLabelTest {
    @Test fun labelsUseLocalDatesAcrossMidnightAndYearBoundary() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 12, 31, 23, 0, 0, 0, zone)
        fun label(at: ZonedDateTime) = nextReviewLabel(at.toInstant().toEpochMilli(), now.toInstant().toEpochMilli(), zone)
        assertEquals("Nächste Wiederholung heute um 23:30 Uhr.", label(now.plusMinutes(30)))
        assertEquals("Nächste Wiederholung morgen um 01:00 Uhr.", label(now.plusHours(2)))
        assertEquals("Nächste Wiederholung am 02.01.2027 um 23:00 Uhr.", label(now.plusDays(2)))
    }
}
