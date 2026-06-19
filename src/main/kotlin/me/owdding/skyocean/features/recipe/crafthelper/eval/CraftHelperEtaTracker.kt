package me.owdding.skyocean.features.recipe.crafthelper.eval

import me.owdding.ktmodules.Module
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.hypixel.ServerChangeEvent
import tech.thatgravyboat.skyblockapi.utils.extentions.currentInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Tracks the rate at which the player gathers craft helper ingredients and projects an ETA for
 * when an ingredient's requirement will be met.
 *
 * A tracker for an ingredient starts collecting data the moment its available amount increases
 * (e.g. mining gold fills the gold sack) and stops - dropping its history so no ETA is shown -
 * when the gathering goes stale ([TIMEOUT] without a gain), the amount drops (the ingredient was
 * consumed/crafted), or the player changes servers/worlds.
 */
@Module
object CraftHelperEtaTracker {

    private val TIMEOUT = 30.seconds
    private const val MAX_SAMPLES = 30

    private class Tracker(now: Instant, amount: Int) {
        val samples = ArrayDeque<Pair<Instant, Int>>().apply { addLast(now to amount) }
        var lastAmount: Int = amount
        var lastIncrease: Instant = now
    }

    private val trackers = mutableMapOf<String, Tracker>()

    /**
     * Feeds the current available amount for every tracked ingredient. Ingredients that are no
     * longer part of the craft helper are dropped.
     */
    fun update(amounts: Map<String, Int>) {
        val now = currentInstant()
        trackers.keys.retainAll(amounts.keys)

        for ((id, amount) in amounts) {
            val tracker = trackers.getOrPut(id) { Tracker(now, amount) }

            when {
                amount > tracker.lastAmount -> {
                    tracker.lastIncrease = now
                    tracker.samples.addLast(now to amount)
                    while (tracker.samples.size > MAX_SAMPLES) tracker.samples.removeFirst()
                }
                // Amount dropped (consumed, crafted, withdrawn): restart from the new baseline.
                amount < tracker.lastAmount -> tracker.restart(now, amount)
                // No gains for a while: drop stale history so we stop projecting an ETA.
                now - tracker.lastIncrease >= TIMEOUT -> tracker.restart(now, amount)
            }
            tracker.lastAmount = amount
        }
    }

    private fun Tracker.restart(now: Instant, amount: Int) {
        samples.clear()
        samples.addLast(now to amount)
        lastIncrease = now
    }

    /** Returns the projected time to gather [remaining] more of [id], or null if not estimable. */
    fun getEta(id: String, remaining: Int): Duration? {
        if (remaining <= 0) return null
        val tracker = trackers[id] ?: return null
        if (currentInstant() - tracker.lastIncrease >= TIMEOUT) return null
        val rate = tracker.samples.ratePerSecond() ?: return null
        if (rate <= 0.0) return null
        return (remaining / rate).seconds
    }

    /** Least squares slope of amount over time, in items per second. */
    private fun List<Pair<Instant, Int>>.ratePerSecond(): Double? {
        if (size < 2) return null
        val start = first().first
        val points = map { (time, amount) -> (time - start).inWholeMilliseconds / 1000.0 to amount.toDouble() }

        val avgX = points.map { it.first }.average()
        val avgY = points.map { it.second }.average()

        var above = 0.0
        var below = 0.0
        for ((x, y) in points) {
            above += (x - avgX) * (y - avgY)
            below += (x - avgX) * (x - avgX)
        }
        if (below == 0.0) return null
        return above / below
    }

    fun clear() = trackers.clear()

    @Subscription(ServerChangeEvent::class)
    fun onServerChange() = clear()
}
