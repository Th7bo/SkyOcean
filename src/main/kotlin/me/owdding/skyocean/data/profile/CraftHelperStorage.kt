package me.owdding.skyocean.data.profile

import com.mojang.serialization.Codec
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperRecipe
import me.owdding.skyocean.features.recipe.crafthelper.data.NormalCraftHelperRecipe
import me.owdding.skyocean.features.recipe.crafthelper.data.SkyShardsMethod
import me.owdding.skyocean.features.recipe.crafthelper.data.SkyShardsRecipe
import me.owdding.skyocean.generated.SkyOceanCodecs
import me.owdding.skyocean.utils.LateInitModule
import me.owdding.skyocean.utils.codecs.CodecHelpers
import me.owdding.skyocean.utils.storage.ProfileStorage
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId

@LateInitModule
object CraftHelperStorage {
    private fun wrapInList(codec: Codec<CraftHelperRecipe>): Codec<List<CraftHelperRecipe>> =
        codec.xmap(
            { recipe -> if (recipe is NormalCraftHelperRecipe && recipe.item == null) emptyList() else listOf(recipe) },
            { it.firstOrNull() ?: NormalCraftHelperRecipe(null) },
        )

    private val storage = ProfileStorage<List<CraftHelperRecipe>>(
        3,
        { emptyList() },
        "craft_helper",
    ) { version ->
        when (version) {
            0 -> wrapInList(
                SkyOceanCodecs.NormalCraftHelperRecipeCodec.codec().xmap(
                    { (item, amount) ->
                        NormalCraftHelperRecipe(
                            item?.id?.let { SkyBlockId.unknownType(it) },
                            amount,
                        ) as CraftHelperRecipe
                    },
                    { it as NormalCraftHelperRecipe },
                ),
            )
            1 -> wrapInList(
                SkyOceanCodecs.NormalCraftHelperRecipeCodec.codec()
                    .xmap({ it as CraftHelperRecipe }, { it as NormalCraftHelperRecipe }),
            )
            2 -> wrapInList(SkyOceanCodecs.CraftHelperRecipeCodec.codec())
            3 -> SkyOceanCodecs.CraftHelperRecipeCodec.codec().listOf()
            else -> CodecHelpers.unit { emptyList<CraftHelperRecipe>() }
        }
    }

    val items: List<CraftHelperRecipe> get() = storage.get() ?: emptyList()

    val canModifyCount: Boolean get() = items.any { it.canModifyCount }
    val recipeType get() = items.firstOrNull()?.type

    val data get() = items.firstOrNull()
    val selectedItem
        get() = when (val data = data) {
            is NormalCraftHelperRecipe -> data.item
            is SkyShardsRecipe -> data.tree.shard
            else -> null
        }
    val selectedAmount
        get() = when (val data = data) {
            is NormalCraftHelperRecipe -> data.amount
            is SkyShardsRecipe -> data.tree.quantity
            else -> 1
        }

    fun getAmountAt(index: Int): Int = when (val recipe = items.getOrNull(index)) {
        is NormalCraftHelperRecipe -> recipe.amount
        is SkyShardsRecipe -> recipe.tree.quantity
        else -> 1
    }

    fun canModifyCountAt(index: Int): Boolean = items.getOrNull(index)?.canModifyCount == true

    fun addItem(item: SkyBlockId?): Boolean {
        item ?: return false
        val current = items.toMutableList()
        if (current.any { it is NormalCraftHelperRecipe && it.item == item }) return false
        current.add(NormalCraftHelperRecipe(item))
        storage.set(current)
        save()
        return true
    }

    fun removeItem(index: Int) {
        val current = items.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        storage.set(current)
        save()
    }

    fun setSelected(item: SkyBlockId?) {
        item?.let { addItem(it) }
    }

    fun setAmount(amount: Int) {
        setAmountAt(0, amount)
    }

    fun setAmountAt(index: Int, amount: Int) {
        val coerced = amount.coerceAtLeast(1)
        val current = items.toMutableList()
        when (val recipe = current.getOrNull(index)) {
            is NormalCraftHelperRecipe -> current[index] = recipe.copy(amount = coerced)
            else -> return
        }
        storage.set(current)
        save()
    }

    fun setSkyShards(recipe: SkyShardsMethod) {
        val current = items.toMutableList()
        if (current.isEmpty()) {
            current.add(SkyShardsRecipe(recipe))
        } else {
            current[0] = SkyShardsRecipe(recipe)
        }
        storage.set(current)
        save()
    }

    fun clear() {
        storage.set(emptyList())
        save()
    }

    fun save() {
        storage.save()
    }
}
