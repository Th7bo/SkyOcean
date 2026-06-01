package me.owdding.skyocean.features.recipe.crafthelper.data

import me.owdding.ktcodecs.GenerateCodec
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperRecipe
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperTree
import me.owdding.skyocean.features.recipe.crafthelper.resolver.DefaultTreeResolver
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId
import java.util.UUID

@GenerateCodec
data class NormalCraftHelperRecipe(
    var item: SkyBlockId?,
    var amount: Int = 1,
    override val group: UUID?,
) : CraftHelperRecipe(CraftHelperRecipeType.NORMAL, true) {
    override fun resolve(
        resetLayout: () -> Unit,
        clear: () -> Unit,
    ): CraftHelperTree? {
        return DefaultTreeResolver.resolve(this, resetLayout, clear)
    }
}
