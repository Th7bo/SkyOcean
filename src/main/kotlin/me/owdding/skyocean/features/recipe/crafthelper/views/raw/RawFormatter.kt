package me.owdding.skyocean.features.recipe.crafthelper.views.raw

import me.owdding.skyocean.config.features.misc.crafthelper.CraftHelperConfig
import me.owdding.skyocean.features.recipe.crafthelper.views.CraftHelperState
import me.owdding.skyocean.features.recipe.crafthelper.views.RecipeView
import me.owdding.skyocean.features.recipe.crafthelper.views.WidgetBuilder
import me.owdding.skyocean.features.recipe.serialize
import net.minecraft.client.gui.components.AbstractWidget

@ConsistentCopyVisibility
data class RawFormatter private constructor(val withPrefix: Boolean) : RecipeView {
    override fun create(
        state: CraftHelperState,
        widget: WidgetBuilder,
        widgetConsumer: (AbstractWidget) -> Unit,
    ) {
        val childLess = state.collect().filterNot { it.hasChildren }.groupBy { it.ingredient.serialize() }.values
        val merged = childLess.map(CraftHelperState::merge)
        widget.measureAmountColumn(merged)

        merged.forEachIndexed { index, state ->
            if (state.isDone() && CraftHelperConfig.rawFormatterHideCompleted) return@forEachIndexed
            context(state) {
                widgetConsumer(
                    if (withPrefix) {
                        widget.listEntry(if (index < childLess.size - 1) "├ " else "└ ")
                    } else {
                        widget.listEntry("")
                    },
                )
            }
        }
    }

    companion object : RecipeView by RawFormatter(true) {
        val DEFAULT = this
        val WITHOUT_PREFIX = RawFormatter(false)
    }
}
