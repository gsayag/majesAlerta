package com.example.majes_alerta

import com.example.majes_alerta.data.ApiClient
import com.example.majes_alerta.domain.Report
import com.example.majes_alerta.domain.ReportsDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionRulesTest {

    private fun report(type: String, value: String?, weapon: Boolean = false, injury: Boolean = false) = Report(
        id = "T",
        type = type,
        description = "Caso ficticio de prueba.",
        occurredAt = ReportsDomain.formatIso(System.currentTimeMillis() - 3_600_000L),
        createdAt = ReportsDomain.formatIso(System.currentTimeMillis()),
        location = ReportsDomain.CENTER,
        weapon = weapon,
        injury = injury,
        status = ReportsDomain.STATES[0],
        valueBand = value
    )

    @Test
    fun carTheftAlertsAloneButGlassesDoNot() {
        assertTrue(ReportsDomain.criticalReasons(report("Robo a persona", "bajo")).isEmpty())
        assertEquals(listOf("Robo de vehículo"), ReportsDomain.criticalReasons(report("Robo de vehículo", "muy_alto")))
    }

    @Test
    fun harmMatchesWebRules() {
        assertEquals(1, ReportsDomain.harm(report("Robo a persona", "bajo")))
        assertEquals(6, ReportsDomain.harm(report("Robo de vehículo", "muy_alto")))
        assertEquals(11, ReportsDomain.harm(report("Robo a negocio", "alto", weapon = true, injury = true)))
    }

    @Test
    fun oldReportsWithoutValueStillWork() {
        assertEquals("sin_dato", ReportsDomain.valueBand("Robo a persona", null))
        assertEquals("alto", ReportsDomain.valueBand("Robo de vehículo", null))
    }

    @Test
    fun valueRaisesPriority() {
        val cheap = ReportsDomain.priority(report("Robo a persona", "bajo")).score
        val costly = ReportsDomain.priority(report("Robo a persona", "muy_alto")).score
        assertEquals(15, costly - cheap)
    }

    @Test
    fun datesConvertBetweenAppAndServer() {
        assertEquals("2026-10-28T08:00:00-05:00", ApiClient.toLimaIso("2026-10-28T13:00:00.000Z"))
        assertEquals("2026-10-28T22:31:18.000Z", ApiClient.toUtcIso("2026-10-28T17:31:18.246697-05:00"))
    }
}
