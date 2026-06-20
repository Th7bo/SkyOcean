package me.owdding.skyocean.features.recipe.crafthelper.eval

import me.owdding.ktmodules.Module
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.hypixel.ServerChangeEvent
import tech.thatgravyboat.skyblockapi.utils.extentions.currentInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant

/**
 * Tracks how fast the player gathers craft helper ingredients and projects an ETA for when an
 * ingredient's requirement will be met.
 *
 * The rate is an average - cumulative gains divided by the elapsed time - rather than an
 * instantaneous slope, which makes it robust to two quirks:
 *  - Sack update notifications can lag 3-30 seconds, so gains arrive in bursts rather than smoothly.
 *    Averaging over the whole window means the burst timing doesn't matter.
 *  - Autocompactors (and crafting/spending) consume a resource the moment it is gathered, so the
 *    raw amount swings up and down. Decreases are ignored entirely, so they never drag the rate
 *    negative or reset an otherwise healthy window.
 *
 * The window starts on the first increase and ends (no ETA shown) when gathering goes stale
 * ([TIMEOUT] without a gain) or the player changes servers/worlds.
 */
@Module
object CraftHelperEtaTracker {

    // Must comfortably exceed the largest expected gap between two sack updates (~30s), otherwise
    // we'd disarm mid-gathering while waiting on a delayed notification.
    private val TIMEOUT = 45.seconds
    private val MIN_WINDOW = 2.seconds

    private class Tracker(now: Instant, amount: Int) {
        var lastAmount: Int = amount
        var armed: Boolean = false
        var windowStart: Instant = now
        var gathered: Long = 0
        var lastIncrease: Instant = now

        fun disarm() {
            armed = false
            gathered = 0
        }
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
            val diff = amount - tracker.lastAmount

            when {
                // The first increase arms the tracker and starts the clock; only later increases
                // count toward the rate. The first delta is dropped because the resource it
                // represents was gathered before it landed (the sack update can be seconds late),
                // so counting it would have no honest elapsed time behind it.
                diff > 0 && !tracker.armed -> {
                    tracker.armed = true
                    tracker.windowStart = now
                    tracker.gathered = 0
                    tracker.lastIncrease = now
                }
                diff > 0 -> {
                    tracker.gathered += diff
                    tracker.lastIncrease = now
                }
                // Decreases are consumption (autocompactor, crafting, spending), not gathering.
                diff < 0 -> Unit
            }

            // No gains for a while: the player stopped gathering, so drop the stale window.
            if (tracker.armed && now - tracker.lastIncrease >= TIMEOUT) tracker.disarm()

            tracker.lastAmount = amount
        }
    }

    /** Returns the projected time to gather [remaining] more of [id], or null if not estimable. */
    fun getEta(id: String, remaining: Int): Duration? {
        if (remaining <= 0) return null
        val tracker = trackers[id]?.takeIf { it.armed && it.gathered > 0 } ?: return null
        val elapsed = currentInstant() - tracker.windowStart
        if (elapsed < MIN_WINDOW) return null
        val rate = tracker.gathered / elapsed.toDouble(DurationUnit.SECONDS) // items per second
        if (rate <= 0.0) return null
        return (remaining / rate).seconds
    }

    fun clear() = trackers.clear()

    @Subscription(ServerChangeEvent::class)
    fun onServerChange() = clear()
}
