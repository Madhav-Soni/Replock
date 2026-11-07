package com.fitness.squatcounter.blocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageMeterTest {

    @Test
    fun accruesWhileMetering() {
        val meter = UsageMeter(maxGapMs = 5_000L)
        meter.update(0L, true)
        meter.update(1_000L, true)
        meter.update(2_000L, true)

        assertEquals(2, meter.pendingSeconds)
    }

    @Test
    fun nothingAccruesBeforeMeteringStarts() {
        val meter = UsageMeter()
        meter.update(0L, false)
        meter.update(10_000L, false)

        assertEquals(0, meter.pendingSeconds)
    }

    @Test
    fun finalSliceIsChargedThenAccrualStops() {
        val meter = UsageMeter()
        meter.update(0L, true)
        meter.update(1_500L, false) // left the app: the last 1.5 s still counts

        assertEquals(1, meter.pendingSeconds)

        meter.update(4_000L, false) // not metering: nothing more is charged
        assertEquals(1, meter.pendingSeconds)
    }

    @Test
    fun takeKeepsTheFractionalRemainder() {
        val meter = UsageMeter()
        meter.update(0L, true)
        meter.update(1_500L, false)

        assertEquals(1, meter.takeWholeSeconds())
        assertEquals(0, meter.pendingSeconds)

        // The leftover 500 ms carries into the next stretch of use
        meter.update(10_000L, true)
        meter.update(10_500L, true)
        assertEquals(1, meter.pendingSeconds)
    }

    @Test
    fun longGapIsCapped() {
        val meter = UsageMeter(maxGapMs = 5_000L)
        meter.update(0L, true)
        meter.update(60_000L, true) // e.g. the service was frozen for a minute

        assertEquals(5, meter.pendingSeconds)
    }

    @Test
    fun backwardsTimeNeverChargesNegative() {
        val meter = UsageMeter()
        meter.update(5_000L, true)
        meter.update(1_000L, true)

        assertEquals(0, meter.pendingSeconds)
    }
}

class BlocklistStoreTest {

    private class FakeStorage(var data: BlocklistData = BlocklistData()) : BlocklistStorage {
        override fun load(): BlocklistData = data
        override fun save(data: BlocklistData) {
            this.data = data
        }
    }

    private val neverBlock = setOf("com.android.settings")
    private val app = "com.example.insta"

    private fun store(
        storage: FakeStorage = FakeStorage(),
        coolingOffMs: Long = 0L
    ) = BlocklistStore(storage, neverBlock, coolingOffMs)

    @Test
    fun addingIsInstant() {
        val store = store(coolingOffMs = 1_000L)

        assertTrue(store.add(app))
        assertTrue(app in store.effectiveBlocklist(0L))
    }

    @Test
    fun neverBlockAppsCannotBeAdded() {
        val store = store()

        assertFalse(store.add("com.android.settings"))
        assertTrue(store.effectiveBlocklist(0L).isEmpty())
    }

    @Test
    fun neverBlockAppsAreIgnoredEvenIfStorageIsTamperedWith() {
        val storage = FakeStorage(BlocklistData(blocked = setOf("com.android.settings", app)))
        val store = store(storage)

        assertEquals(setOf(app), store.effectiveBlocklist(0L))
    }

    @Test
    fun removalIsImmediateWhenCoolingOffIsZero() {
        val store = store(coolingOffMs = 0L)
        store.add(app)

        store.requestRemoval(app, nowMs = 0L)

        assertFalse(app in store.effectiveBlocklist(0L))
    }

    @Test
    fun removalWaitsForCoolingOff() {
        val store = store(coolingOffMs = 1_000L)
        store.add(app)

        store.requestRemoval(app, nowMs = 0L)

        assertTrue(app in store.effectiveBlocklist(999L)) // still blocked
        assertFalse(app in store.effectiveBlocklist(1_000L)) // wait is over
    }

    @Test
    fun addingAgainCancelsAPendingRemoval() {
        val store = store(coolingOffMs = 1_000L)
        store.add(app)
        store.requestRemoval(app, nowMs = 0L)

        store.add(app)

        assertTrue(app in store.effectiveBlocklist(5_000L))
        assertTrue(store.snapshot().pendingRemovals.isEmpty())
    }

    @Test
    fun cancelRemovalKeepsTheAppBlocked() {
        val store = store(coolingOffMs = 1_000L)
        store.add(app)
        store.requestRemoval(app, nowMs = 0L)

        store.cancelRemoval(app)

        assertTrue(app in store.effectiveBlocklist(5_000L))
    }

    @Test
    fun removingAnAppThatIsNotBlockedDoesNothing() {
        val store = store(coolingOffMs = 1_000L)

        store.requestRemoval(app, nowMs = 0L)

        assertTrue(store.snapshot().pendingRemovals.isEmpty())
        assertTrue(store.effectiveBlocklist(0L).isEmpty())
    }

    @Test
    fun secondRemovalRequestDoesNotExtendTheWait() {
        val store = store(coolingOffMs = 1_000L)
        store.add(app)
        store.requestRemoval(app, nowMs = 0L)

        store.requestRemoval(app, nowMs = 900L)

        assertFalse(app in store.effectiveBlocklist(1_000L))
    }
}