package com.flowtap.data.transfer

import com.flowtap.data.SampleFlows
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.FlowColor
import com.flowtap.domain.model.FlowIcon
import com.flowtap.domain.model.ToggleState
import com.flowtap.domain.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FlowTransferCodecTest {

    @Test
    fun `round trip keeps flows, actions and schedules`() {
        val json = FlowTransferCodec.encode(SampleFlows.all, exportedAt = "2026-11-03T10:00:00Z")
        val decoded = FlowTransferCodec.decode(json)

        assertEquals(0, decoded.skippedFlows)
        assertEquals(SampleFlows.all.map { it.name }, decoded.flows.map { it.flow.name })
        decoded.flows.zip(SampleFlows.all).forEach { (got, original) ->
            assertEquals(original.actions, got.flow.actions)
            assertEquals(original.icon, got.flow.icon)
            assertEquals(original.color, got.flow.color)
            assertEquals(original.triggers.size, got.flow.triggers.size)
            assertEquals(0L, got.flow.id)
        }
    }

    @Test
    fun `schedules get fresh ids but keep time, days and enabled state`() {
        val json = FlowTransferCodec.encode(listOf(SampleFlows.bedtime), exportedAt = "x")
        val original = SampleFlows.bedtime.triggers.single() as Trigger.TimeOfDay
        val imported = FlowTransferCodec.decode(json).flows.single().flow.triggers.single() as Trigger.TimeOfDay

        assertNotEquals(original.id, imported.id)
        assertEquals(original.copy(id = imported.id), imported)
    }

    @Test
    fun `unknown action types are skipped without dropping the flow`() {
        val json = """
            {"format":"flowtap","version":2,"flows":[{
              "name":"Mixed","icon":"Moon","color":"Teal",
              "actions":[
                {"type":"delay","seconds":3},
                {"type":"teleport","destination":"Mars"},
                {"type":"wifi","state":"Off"}
              ],
              "triggers":[{"type":"geofence","lat":1.0}]
            }]}
        """.trimIndent()

        val flow = FlowTransferCodec.decode(json).flows.single()

        assertEquals(listOf(FlowAction.Delay(3), FlowAction.SetWifi(ToggleState.Off)), flow.flow.actions)
        assertEquals(1, flow.skippedActions)
        assertEquals(1, flow.skippedTriggers)
    }

    @Test
    fun `unknown icon and color fall back to defaults`() {
        val json = """{"format":"flowtap","flows":[{"name":"X","icon":"Unicorn","color":"Ultraviolet"}]}"""
        val flow = FlowTransferCodec.decode(json).flows.single().flow

        assertEquals(FlowIcon.Bolt, flow.icon)
        assertEquals(FlowColor.Violet, flow.color)
        assertEquals(emptyList<FlowAction>(), flow.actions)
    }

    @Test
    fun `flows without a name are counted as skipped`() {
        val json = """{"format":"flowtap","flows":[{"name":"  "},{"icon":"Moon"},{"name":"Ok"},42]}"""
        val decoded = FlowTransferCodec.decode(json)

        assertEquals(listOf("Ok"), decoded.flows.map { it.flow.name })
        assertEquals(3, decoded.skippedFlows)
    }

    @Test
    fun `non-FlowTap files are rejected`() {
        assertThrows(ImportFormatException::class.java) { FlowTransferCodec.decode("not json") }
        assertThrows(ImportFormatException::class.java) { FlowTransferCodec.decode("""{"flows":[]}""") }
        assertThrows(ImportFormatException::class.java) { FlowTransferCodec.decode("""[1,2,3]""") }
        assertThrows(ImportFormatException::class.java) { FlowTransferCodec.decode("""{"format":"flowtap"}""") }
    }

    @Test
    fun `bluetooth triggers round trip with a fresh id`() {
        val trigger = Trigger.BluetoothDevice(
            id = "bt-1",
            address = "00:11:22:AA:BB:CC",
            deviceName = "Car Audio",
            event = Trigger.ConnectionEvent.Disconnected,
            enabled = false,
        )
        val json = FlowTransferCodec.encode(listOf(SampleFlows.driving.copy(triggers = listOf(trigger))), "x")
        val imported = FlowTransferCodec.decode(json).flows.single().flow.triggers.single() as Trigger.BluetoothDevice

        assertNotEquals(trigger.id, imported.id)
        assertEquals(trigger.copy(id = imported.id), imported)
    }
}
