package me.owdding.skyocean.features.recipe.crafthelper

import me.owdding.skyocean.features.recipe.crafthelper.data.CraftHelperRecipeType
import java.util.UUID

abstract class CraftHelperRecipe(val type: CraftHelperRecipeType, val canModifyCount: Boolean) {
    abstract val group: UUID?
    abstract fun resolve(resetLayout: () -> Unit, clear: () -> Unit): CraftHelperTree?
}
