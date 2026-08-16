package me.owdding.skyocean.compat.skysoft

import com.skysoft.data.skyblock.RecipeIngredient
import com.skysoft.data.skyblock.RecipeIngredientKind
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import me.owdding.skyocean.data.profile.CraftHelperStorage
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.sounds.SoundEvents
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId
import tech.thatgravyboat.skyblockapi.helpers.McClient
import tech.thatgravyboat.skyblockapi.helpers.McFont

/**
 * Adds the craft-helper button to Skysoft's item list viewer, mirroring the RRV support in
 * [me.owdding.skyocean.compat.rrv.SkyOceanRrvCraftHelper].
 *
 * Skysoft's viewer draws everything itself instead of using widgets, so the button is drawn with
 * Skysoft's own [PixelButtonRenderer] and clicks are resolved against the bounds collected while
 * rendering. It sits on the top-right corner of the result slot, opposite Skysoft's own quick-craft
 * `+` button.
 *
 * Called from [me.owdding.skyocean.mixins.compat.skysoft.ItemListViewerScreenMixin]; the bounds are
 * cleared at the start of every frame so stale buttons never stay clickable.
 */
object SkyOceanSkysoftCraftHelper {

    private const val BUTTON_SIZE = 10
    private const val BUTTON_OVERLAP = BUTTON_SIZE / 2

    private val buttons = mutableListOf<Pair<Rect, SkyBlockId>>()

    fun clearButtons() {
        buttons.clear()
    }

    fun addButton(
        context: GuiGraphicsExtractor,
        resultSlot: Rect,
        recipe: SkyBlockRecipe,
        mouseX: Int,
        mouseY: Int,
    ) {
        val skyBlockId = recipe.result.resolveSkyBlockId() ?: return

        val bounds = Rect(
            resultSlot.x + resultSlot.width - BUTTON_OVERLAP,
            resultSlot.y - BUTTON_OVERLAP,
            BUTTON_SIZE,
            BUTTON_SIZE,
        )
        buttons += bounds to skyBlockId

        val isHovered = bounds.contains(mouseX, mouseY)
        PixelButtonRenderer.draw(context, McFont.self, bounds, "C", false, isHovered, true)
        if (isHovered) {
            SkysoftNativeTooltip.setForNextFrame(context, listOf("§eSet as Craft Helper recipe"), mouseX, mouseY)
        }
    }

    fun clickButton(mouseX: Int, mouseY: Int): Boolean {
        val skyBlockId = buttons.firstOrNull { (bounds, _) -> bounds.contains(mouseX, mouseY) }?.second ?: return false

        CraftHelperStorage.setSelected(skyBlockId)
        McClient.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f)
        return true
    }

    // Currencies, essence and vanilla registry items are never craft-helper targets.
    private fun RecipeIngredient.resolveSkyBlockId(): SkyBlockId? = when (kind) {
        RecipeIngredientKind.ITEM, RecipeIngredientKind.PET -> id.toSkyBlockId()
        else -> null
    }

    private fun String.toSkyBlockId(): SkyBlockId? {
        if (isBlank() || this == "SKYBLOCK_COIN") return null

        SkyBlockId.unknownType(this)?.let { return it }
        // Skysoft encodes pet rarity as "NAME;RARITY"; fall back to the plain name.
        return takeIf { ';' in it }
            ?.substringBefore(';')
            ?.let(SkyBlockId::unknownType)
    }
}
