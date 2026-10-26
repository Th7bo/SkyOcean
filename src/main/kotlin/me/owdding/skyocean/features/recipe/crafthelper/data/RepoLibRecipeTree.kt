package me.owdding.skyocean.features.recipe.crafthelper.data

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.owdding.ktcodecs.GenerateCodec
import me.owdding.ktcodecs.Inline
import me.owdding.ktcodecs.OptionalNullable
import me.owdding.skyocean.features.recipe.Ingredient
import me.owdding.skyocean.features.recipe.ItemLikeIngredient
import me.owdding.skyocean.features.recipe.RepoApiRecipe
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperRecipe
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperTree
import me.owdding.skyocean.features.recipe.crafthelper.resolver.RepoLibTreeResolver
import me.owdding.skyocean.generated.SkyOceanCodecs
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId
import java.util.UUID

@GenerateCodec
data class RepoLibRecipeTree(
    @Inline var recipe: RepoApiRecipe,
    override var amount: Int,
    @OptionalNullable override val group: UUID? = null,
) : CraftHelperRecipe(CraftHelperRecipeType.REPO_LIB_RECIPE), CraftHelperRecipe.MutableCount, CraftHelperRecipe.MultiplesOf, CraftHelperRecipe.Ingredients {
    override val selectedItem: SkyBlockId? get() = recipe.output?.id

    companion object {
        val CODEC: MapCodec<RepoLibRecipeTree> = MapCodec.assumeMapUnsafe(Codec.withAlternative(SkyOceanCodecs.RepoLibRecipeTreeCodec.codec(), RecordCodecBuilder.create {
            it.group(
                SkyOceanCodecs.RepoApiRecipeCodec.fieldOf("recipe").forGetter(RepoLibRecipeTree::recipe),
                Codec.INT.fieldOf("amount").forGetter(RepoLibRecipeTree::amount)
            ).apply(it, ::RepoLibRecipeTree)
        }))
    }

    override fun resolve(resetLayout: () -> Unit, clear: () -> Unit): CraftHelperTree? {
        return RepoLibTreeResolver.resolve(this, resetLayout, clear)
    }

    override fun withAmount(amount: Int): RepoLibRecipeTree = copy(amount = amount)
    override fun withGroup(group: UUID?): RepoLibRecipeTree = copy(group = group)
    override val multiples: Int get() = recipe.output?.amount ?: 1
    override val inputs: List<Ingredient> = recipe.inputs
    override val output: ItemLikeIngredient? = recipe.output
}
