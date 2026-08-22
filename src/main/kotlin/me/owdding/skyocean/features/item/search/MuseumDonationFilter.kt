package me.owdding.skyocean.features.item.search

import me.owdding.skyocean.features.item.sources.ItemSources
import me.owdding.skyocean.features.item.sources.system.SimpleTrackedItem
import tech.thatgravyboat.skyblockapi.api.profile.items.museum.MuseumAPI
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId.Companion.getSkyBlockId

/**
 * Reduces tracked items to the ones that can still be donated to the museum.
 *
 * Sources that only hold items which don't exist yet, like the forge, are skipped,
 * since those can't be donated before they are finished.
 */
object MuseumDonationFilter {

    private val ignoredSources = setOf(ItemSources.FORGE)

    /**
     * Whether the museum has been opened on this profile, without that there is nothing to compare against.
     */
    val hasData: Boolean get() = MuseumAPI.getAllItems().isNotEmpty()

    fun filter(items: Iterable<SimpleTrackedItem>): List<SimpleTrackedItem> {
        val cache = mutableMapOf<SkyBlockId, Boolean>()
        return items.filter { item ->
            if (item.context.source in ignoredSources) return@filter false
            val id = item.itemStack.getSkyBlockId() ?: return@filter false
            cache.getOrPut(id) { MuseumAPI.isMuseumItem(id) && !MuseumAPI.isDonated(id) }
        }
    }
}
