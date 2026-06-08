package me.owdding.skyocean.compat.rei

//? < 26.1 {
/*

import me.owdding.skyocean.data.profile.CraftHelperStorage
import me.owdding.skyocean.utils.Utils.refreshScreen
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.gui.widgets.Widget
import me.shedaniel.rei.api.client.gui.widgets.Widgets
import me.shedaniel.rei.api.client.plugins.REIClientPlugin
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.category.extension.CategoryExtensionProvider
import me.shedaniel.rei.api.client.registry.display.DisplayCategory
import me.shedaniel.rei.api.client.registry.display.DisplayCategoryView
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId
import tech.thatgravyboat.skyblockapi.helpers.McScreen

class SkyOceanReiPlugin : REIClientPlugin {

    companion object {
        private val FIRMAMENT_CRAFTING = CategoryIdentifier.of<Display>(Identifier.fromNamespaceAndPath("firmament", "crafting_recipe"))
        private val FIRMAMENT_FORGE = CategoryIdentifier.of<Display>(Identifier.fromNamespaceAndPath("firmament", "forge_recipe"))
        private val FIRMAMENT_ESSENCE = CategoryIdentifier.of<Display>(Identifier.fromNamespaceAndPath("firmament", "essence_upgrade"))
        private val FIRMAMENT_MOB_DROP = CategoryIdentifier.of<Display>(Identifier.fromNamespaceAndPath("firmament", "mob_drop_recipe"))
        private val FIRMAMENT_KAT = CategoryIdentifier.of<Display>(Identifier.fromNamespaceAndPath("firmament", "kat_recipe"))
        private val FIRMAMENT_SHOP = CategoryIdentifier.of<Display>(Identifier.fromNamespaceAndPath("firmament", "npc_shopping"))
        private val FIRMAMENT_REFORGE = CategoryIdentifier.of<Display>(Identifier.fromNamespaceAndPath("firmament", "reforge_recipe"))

        private val sbRecipeClass: Class<*>? by lazy {
            runCatching { Class.forName("moe.nea.firmament.compat.rei.recipes.SBRecipe") }.getOrNull()
        }

        private val sbReforgeRecipeClass: Class<*>? by lazy {
            runCatching { Class.forName("moe.nea.firmament.compat.rei.recipes.SBReforgeRecipe") }.getOrNull()
        }

        private val skyblockIdClass: Class<*>? by lazy {
            runCatching { Class.forName("moe.nea.firmament.util.SkyblockId") }.getOrNull()
        }

        private val sbItemStackClass: Class<*>? by lazy {
            runCatching { Class.forName("moe.nea.firmament.repo.SBItemStack") }.getOrNull()
        }

        private fun getOutputSkyBlockId(display: Display): SkyBlockId? {
            return runCatching {
                if (sbRecipeClass?.isInstance(display) == true) {
                    val neuRecipeMethod = sbRecipeClass?.getMethod("getNeuRecipe")
                    val neuRecipe = neuRecipeMethod?.invoke(display) ?: return null

                    val allOutputsMethod = neuRecipe.javaClass.getMethod("getAllOutputs")
                    val outputs = allOutputsMethod.invoke(neuRecipe) as? Collection<*> ?: return null

                    for (output in outputs) {
                        if (output == null) continue
                        val itemIdMethod = output.javaClass.getMethod("getItemId")
                        val itemId = itemIdMethod.invoke(output) as? String ?: continue
                        if (itemId == "SKYBLOCK_COIN" || itemId.isEmpty()) continue
                        return neuIdToSkyBlockId(itemId)
                    }
                }

                if (sbReforgeRecipeClass?.isInstance(display) == true) {
                    val limitToItemField = sbReforgeRecipeClass?.getField("limitToItem")
                    val limitToItem = limitToItemField?.get(display)
                    if (limitToItem != null && sbItemStackClass?.isInstance(limitToItem) == true) {
                        val skyblockIdMethod = sbItemStackClass?.getMethod("getSkyblockId")
                        val firmamentId = skyblockIdMethod?.invoke(limitToItem)
                        if (firmamentId != null && skyblockIdClass?.isInstance(firmamentId) == true) {
                            val neuItemMethod = skyblockIdClass?.getMethod("getNeuItem")
                            val neuItem = neuItemMethod?.invoke(firmamentId) as? String
                            if (neuItem != null) {
                                return neuIdToSkyBlockId(neuItem)
                            }
                        }
                    }
                }

                null
            }.getOrNull()
        }

        /**
         * Converts a NEU item id into a [SkyBlockId].
         *
         * NEU encodes pet (and rune) tiers in the id itself as `NAME;TIER` (e.g. `GOLDEN_DRAGON;4`).
         * [SkyBlockId.unknownType] cannot resolve that form because the repos are keyed by the bare
         * `NAME`, so crafted pets like the Golden/Rose/Jade Dragon would yield a null id and never get
         * a craft helper button. When the tiered form fails to resolve we retry with the suffix
         * stripped; [SimpleRecipeApi.getBestRecipe] then matches the recipe across rarities.
         */
        private fun neuIdToSkyBlockId(itemId: String): SkyBlockId? {
            SkyBlockId.unknownType(itemId)?.let { return it }
            if (itemId.contains(';')) {
                SkyBlockId.unknownType(itemId.substringBefore(';'))?.let { return it }
            }
            return null
        }
    }

    override fun registerCategories(registry: CategoryRegistry) {
        if (sbRecipeClass == null && sbReforgeRecipeClass == null) return

        val extension = CraftHelperExtensionProvider()

        listOf(
            FIRMAMENT_CRAFTING,
            FIRMAMENT_FORGE,
            FIRMAMENT_ESSENCE,
            FIRMAMENT_MOB_DROP,
            FIRMAMENT_KAT,
            FIRMAMENT_SHOP,
            FIRMAMENT_REFORGE
        ).forEach { categoryId ->
            runCatching {
                registry.configure(categoryId) { config ->
                    config.registerExtension(extension)
                }
            }
        }
    }

    private class CraftHelperExtensionProvider : CategoryExtensionProvider<Display> {
        override fun provide(display: Display, category: DisplayCategory<Display>, lastView: DisplayCategoryView<Display>): DisplayCategoryView<Display> {
            return object : DisplayCategoryView<Display> {
                override fun getDisplayRenderer(display: Display) = lastView.getDisplayRenderer(display)

                override fun setupDisplay(display: Display, bounds: Rectangle): List<Widget> {
                    val widgets = lastView.setupDisplay(display, bounds).toMutableList()

                    val skyBlockId = getOutputSkyBlockId(display) ?: return widgets

                    val buttonSize = 12
                    val buttonX = bounds.maxX - buttonSize - 4
                    val buttonY = bounds.maxY - buttonSize - 4

                    val button = Widgets.createButton(
                        Rectangle(buttonX, buttonY, buttonSize, buttonSize),
                        Component.literal("C")
                    ).tooltipLine(Component.literal("Set as Craft Helper recipe"))
                        .onClick {
                            CraftHelperStorage.setSelected(skyBlockId)
                            McScreen.refreshScreen()
                        }

                    widgets.add(button)
                    return widgets
                }
            }
        }
    }
}
*///? }
