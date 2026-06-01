package me.owdding.skyocean.features.recipe.crafthelper

import me.owdding.ktcodecs.GenerateCodec
import me.owdding.skyocean.data.profile.CraftHelperStorage
import java.util.UUID

@GenerateCodec
data class CraftHelperCategory(
    val identifier: UUID,
    var name: String,
) {
    fun isDefault(): Boolean = this === CraftHelperStorage.defaultCategory

    fun getRecipesInCategory(): List<CraftHelperRecipe> = if (isDefault()) {
        CraftHelperStorage.items.filter { it.group == null }
    } else {
        CraftHelperStorage.items.filter { it.group == identifier }
    }
}
