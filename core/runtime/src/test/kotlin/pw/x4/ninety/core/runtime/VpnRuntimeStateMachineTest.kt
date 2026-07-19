package pw.x4.ninety.core.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VpnRuntimeStateMachineTest {
    @Test
    fun `stop invalidates an in-flight start`() {
        val machine = VpnRuntimeStateMachine()
        val start = assertNotNull(machine.request(VpnRuntimeCommand.Start))
        val stop = assertNotNull(machine.request(VpnRuntimeCommand.Stop()))

        assertFalse(machine.completeConnected(start, "stale"))
        assertEquals(VpnRuntimePhase.Stopping, machine.snapshot().phase)

        assertTrue(machine.completeStopped(stop))
        assertEquals(VpnRuntimeState(generation = stop.generation), machine.snapshot())
    }

    @Test
    fun `reload during start supersedes the old configuration`() {
        val machine = VpnRuntimeStateMachine()
        val start = assertNotNull(machine.request(VpnRuntimeCommand.Start))
        val reload = assertNotNull(machine.request(VpnRuntimeCommand.Reload))

        assertEquals(VpnRuntimePhase.Starting, machine.snapshot().phase)
        assertFalse(machine.completeConnected(start, "old"))
        assertTrue(machine.completeConnected(reload, "new"))
        assertEquals(VpnRuntimePhase.Connected, machine.snapshot().phase)
        assertEquals("new", machine.snapshot().activeServer)
    }

    @Test
    fun `latest repeated stop owns the terminal state`() {
        val machine = VpnRuntimeStateMachine()
        assertNotNull(machine.request(VpnRuntimeCommand.Start))
        val first = assertNotNull(machine.request(VpnRuntimeCommand.Stop("first")))
        val second = assertNotNull(machine.request(VpnRuntimeCommand.Stop("revoked")))

        assertFalse(machine.completeStopped(first))
        assertTrue(machine.completeStopped(second))
        assertEquals(VpnRuntimePhase.Idle, machine.snapshot().phase)
        assertEquals("revoked", machine.snapshot().lastError)
    }

    @Test
    fun `duplicate start while active is ignored`() {
        val machine = VpnRuntimeStateMachine()
        val start = assertNotNull(machine.request(VpnRuntimeCommand.Start))

        assertNull(machine.request(VpnRuntimeCommand.Start))
        assertTrue(machine.completeConnected(start, "node"))
        assertNull(machine.request(VpnRuntimeCommand.Start))
    }

    @Test
    fun `stale failure cannot tear down a newer start`() {
        val machine = VpnRuntimeStateMachine()
        val firstStart = assertNotNull(machine.request(VpnRuntimeCommand.Start))
        val stop = assertNotNull(machine.request(VpnRuntimeCommand.Stop()))
        val secondStart = assertNotNull(machine.request(VpnRuntimeCommand.Start))

        assertFalse(machine.fail(firstStart, "old failure"))
        assertFalse(machine.completeStopped(stop))
        assertTrue(machine.completeConnected(secondStart, "fresh"))
        assertEquals(VpnRuntimePhase.Connected, machine.snapshot().phase)
        assertEquals("fresh", machine.snapshot().activeServer)
        assertNull(machine.snapshot().lastError)
    }
}
