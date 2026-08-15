package me.owdding.skyocean.features.recipe.mutation

import me.owdding.skyocean.features.recipe.Ingredient
import me.owdding.skyocean.features.recipe.ItemLikeIngredient
import me.owdding.skyocean.features.recipe.Recipe
import me.owdding.skyocean.features.recipe.RecipeType
import me.owdding.skyocean.features.recipe.SkyOceanItemIngredient
import me.owdding.skyocean.repo.garden.GreenhouseMutation
import me.owdding.skyocean.repo.garden.GreenhouseMutationRepoData
import me.owdding.skyocean.utils.extensions.sanitizeNeu
import net.minecraft.network.chat.Component
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId
import tech.thatgravyboat.skyblockapi.utils.extentions.toFormattedString
import tech.thatgravyboat.skyblockapi.utils.text.Text
import tech.thatgravyboat.skyblockapi.utils.text.TextBuilder.append
import tech.thatgravyboat.skyblockapi.utils.text.TextColor
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.color

/**
 * A Greenhouse mutation, modelled as a recipe so the craft helper can walk the setup like any other
 * tree: to grow one plot of [output] the crops in [inputs] have to be growing next to it, and those
 * are usually mutations themselves.
 *
 * The neighbours aren't consumed the way crafting ingredients are - they stay planted - so the tree
 * describes the layout that has to be built up, not a material cost. For the same reason the Crop
 * Analyzer fee is not part of [inputs]: it's a one-time cost per mutation, not per plot.
 */
data class MutationRecipe(
    val id: SkyBlockId,
    val mutation: GreenhouseMutation,
) : Recipe {
    override val recipeType: RecipeType = RecipeType.MUTATION
    override val output: ItemLikeIngredient = SkyOceanItemIngredient(id, 1)
    override val inputs: List<Ingredient> = mutation.ingredients
        .filter { it.amount > 0 }
        .map { SkyOceanItemIngredient(SkyBlockId.item(it.item).sanitizeNeu(), it.amount) }

    companion object {
        fun loadAll(): List<MutationRecipe> = GreenhouseMutationRepoData.mutations.map { (id, mutation) ->
            MutationRecipe(SkyBlockId.item(id).sanitizeNeu(), mutation)
        }

        fun find(id: String): MutationRecipe? {
            val key = id.substringAfterLast(':').uppercase()
            return GreenhouseMutationRepoData.mutations[key]?.let { MutationRecipe(SkyBlockId.item(key), it) }
        }

        /** Chat summary of a mutation's setup, since there is no in-game recipe screen to open. */
        fun describe(id: String): Component? {
            val recipe = find(id) ?: return null
            val mutation = recipe.mutation

            val lines = buildList<Component> {
                add(
                    Text.of {
                        append(recipe.output.itemName)
                        append(" needs ") { this.color = TextColor.GRAY }
                        append(mutation.surface) { this.color = TextColor.YELLOW }
                        if (mutation.water) {
                            append(" with water") { this.color = TextColor.AQUA }
                        }
                        if (mutation.stages > 0) {
                            append(", ") { this.color = TextColor.GRAY }
                            append("${mutation.stages} growth stages") { this.color = TextColor.YELLOW }
                        }
                    },
                )
                recipe.inputs.filterIsInstance<SkyOceanItemIngredient>().forEach { input ->
                    add(
                        Text.of {
                            append(" - ") { this.color = TextColor.DARK_GRAY }
                            append("${input.amount}x ") { this.color = TextColor.GREEN }
                            append(input.itemName)
                        },
                    )
                }
                mutation.notes.forEach { note ->
                    add(
                        Text.of {
                            append(" - ") { this.color = TextColor.DARK_GRAY }
                            append(note) { this.color = TextColor.YELLOW }
                        },
                    )
                }
                add(
                    Text.of {
                        append("Crop Analyzer: ") { this.color = TextColor.GRAY }
                        append("${mutation.analyzeCost.toFormattedString()} coins") { this.color = TextColor.GOLD }
                        append(" for ") { this.color = TextColor.GRAY }
                        append("${mutation.analyzeCopper.toFormattedString()} copper") { this.color = TextColor.RED }
                    },
                )
            }

            return Text.multiline(*lines.toTypedArray())
        }
    }
}
