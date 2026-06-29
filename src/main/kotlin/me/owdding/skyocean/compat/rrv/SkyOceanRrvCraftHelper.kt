package me.owdding.skyocean.compat.rrv

//? 26.1 {
/*
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen
import me.owdding.skyocean.data.profile.CraftHelperStorage
import me.owdding.skyocean.utils.Utils.refreshScreen
import cc.cassian.rrv.api.recipe.ReliableClientRecipe
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId.Companion.getSkyBlockId as getSkyBlockApiId
import tech.thatgravyboat.skyblockapi.helpers.McScreen

/**
 * Re-adds the craft-helper button to recipe screens, now driven by Reliable Recipe Viewer (RRV)
 * instead of the old Firmament -> REI path.
 *
 * skyrecipes marks its recipes as visual-only, so we intentionally inspect all displayed recipes
 * instead of filtering with `ReliableClientRecipe#isVisualOnly`.
 *
 * Called from [me.owdding.skyocean.mixins.compat.rrv.RecipeViewScreenMixin] at the tail of
 * `RecipeViewScreen#checkGui`, which RRV invokes whenever the displayed recipes change. The buttons
 * are registered through `addRecipeWidget`, which RRV clears at the start of every `checkGui`, so we
 * never duplicate or leak them.
 */
object SkyOceanRrvCraftHelper {

    private const val BUTTON_SIZE = 12
    private const val BUTTON_PADDING = 2

    private val skyRecipesIdExtractor by lazy {
        runCatching {
            Class.forName("com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor")
                .getMethod("extract", ItemStack::class.java)
        }.getOrNull()
    }

    fun addButtons(screen: RecipeViewScreen) {
        val menu = screen.menu ?: return
        val displays = menu.currentDisplay ?: return

        val left = screen.leftPos
        val top = screen.topPos
        val displayLeft = left + menu.guiOffsetLeft()

        for (i in displays.indices) {
            val recipe = displays[i] ?: continue

            val skyBlockId = recipe.resolveSkyBlockId() ?: continue

            val x = displayLeft + recipe.type.displayWidth - BUTTON_SIZE - BUTTON_PADDING
            val y = top + menu.guiOffsetTop(i) + BUTTON_PADDING

            val button = Button.builder(Component.literal("C")) {
                CraftHelperStorage.setSelected(skyBlockId)
                McScreen.refreshScreen()
            }
                .bounds(x, y, BUTTON_SIZE, BUTTON_SIZE)
                .tooltip(Tooltip.create(Component.literal("Set as Craft Helper recipe")))
                .build()

            screen.addRecipeWidget(button)
        }
    }

    private fun ReliableClientRecipe.resolveSkyBlockId(): SkyBlockId? {
        results.firstNotNullOfOrNull { slot ->
            slot.validContents.firstOrNull { !it.isEmpty }?.resolveSkyBlockId()
        }?.let { return it }

        return id.path.toSkyBlockId()
    }

    private fun ItemStack.resolveSkyBlockId(): SkyBlockId? {
        getSkyBlockApiId()?.let { return it }

        return runCatching {
            (skyRecipesIdExtractor?.invoke(null, this) as? String)?.toSkyBlockId()
        }.getOrNull()
    }

    private fun String.toSkyBlockId(): SkyBlockId? {
        val normalized = substringAfterLast('/').substringAfterLast(':')
        if (normalized.isBlank() || normalized == "SKYBLOCK_COIN") return null

        SkyBlockId.unknownType(normalized)?.let { return it }
        return normalized
            .takeIf { ';' in it }
            ?.substringBefore(';')
            ?.let(SkyBlockId::unknownType)
    }
}
*///? }
