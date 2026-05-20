package me.owdding.skyocean.features.recipe.crafthelper.resolver

import me.owdding.skyocean.features.recipe.SimpleRecipeApi.getBestRecipe
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperTree
import me.owdding.skyocean.features.recipe.crafthelper.data.CraftHelperRecipeType
import me.owdding.skyocean.features.recipe.crafthelper.data.NormalCraftHelperRecipe
import me.owdding.skyocean.utils.chat.ChatUtils.sendWithPrefix
import tech.thatgravyboat.skyblockapi.utils.text.Text
import tech.thatgravyboat.skyblockapi.utils.text.TextColor
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.color

object DefaultTreeResolver : TreeResolver<NormalCraftHelperRecipe> {
    override val type: CraftHelperRecipeType get() = CraftHelperRecipeType.NORMAL

    override fun resolve(recipe: NormalCraftHelperRecipe, resetLayout: () -> Unit, clear: () -> Unit): CraftHelperTree? {
        val item = recipe.item ?: run {
            resetLayout()
            return null
        }

        val bestRecipe = getBestRecipe(item) ?: run {
            Text.of("No recipe found for $item!") { this.color = TextColor.RED }.sendWithPrefix()
            resetLayout()
            clear()
            return null
        }
        val output = bestRecipe.output ?: run {
            Text.of("Recipe output is null!") { this.color = TextColor.RED }.sendWithPrefix()
            resetLayout()
            clear()
            return null
        }

        return CraftHelperTree(bestRecipe, output, recipe.amount.coerceAtLeast(1))
    }
}
