package me.owdding.skyocean.data.profile

import com.mojang.serialization.Codec
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperCategory
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperRecipe
import me.owdding.skyocean.features.recipe.crafthelper.data.NormalCraftHelperRecipe
import me.owdding.skyocean.features.recipe.crafthelper.data.SkyShardsMethod
import me.owdding.skyocean.features.recipe.crafthelper.data.SkyShardsRecipe
import me.owdding.skyocean.generated.SkyOceanCodecs
import me.owdding.skyocean.utils.LateInitModule
import me.owdding.skyocean.utils.codecs.CodecHelpers
import me.owdding.skyocean.utils.storage.ProfileStorage
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId
import java.util.UUID

@LateInitModule
object CraftHelperStorage {
    private const val ALL_CATEGORY_KEY = "all"

    val defaultCategory = CraftHelperCategory(UUID(0, 0), "Uncategorized")

    var activeCategory: CraftHelperCategory?
        get() = activeCategoryStorage.get()?.let(::resolveCategoryKey)
        set(value) {
            activeCategoryStorage.set(value?.identifier?.toString() ?: ALL_CATEGORY_KEY)
            activeCategoryStorage.save()
        }

    private fun wrapInList(codec: Codec<CraftHelperRecipe>): Codec<List<CraftHelperRecipe>> =
        codec.xmap(
            { recipe -> if (recipe is NormalCraftHelperRecipe && recipe.item == null) emptyList() else listOf(recipe) },
            { it.firstOrNull() ?: NormalCraftHelperRecipe(null, group = null) },
        )

    private val storage = ProfileStorage<List<CraftHelperRecipe>>(
        3,
        { emptyList() },
        "craft_helper",
    ) { version ->
        when (version) {
            0 -> wrapInList(
                SkyOceanCodecs.NormalCraftHelperRecipeCodec.codec().xmap(
                    { recipe ->
                        NormalCraftHelperRecipe(
                            recipe.item?.id?.let { SkyBlockId.unknownType(it) },
                            recipe.amount,
                            group = null,
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

    private val categoryStorage = ProfileStorage<MutableList<CraftHelperCategory>>(
        0,
        { mutableListOf() },
        "craft_helper_categories",
    ) { version ->
        when (version) {
            0 -> SkyOceanCodecs.CraftHelperCategoryCodec.codec().listOf()
                .xmap({ it.toMutableList() }, { it.toList() })
            else -> CodecHelpers.unit { mutableListOf() }
        }
    }

    private val activeCategoryStorage = ProfileStorage<String>(
        0,
        { ALL_CATEGORY_KEY },
        "craft_helper_active_category",
    ) { version ->
        when (version) {
            0 -> Codec.STRING
            else -> CodecHelpers.unit { ALL_CATEGORY_KEY }
        }
    }

    val items: List<CraftHelperRecipe> get() = storage.get() ?: emptyList()
    val categories: List<CraftHelperCategory> get() = categoryStorage.get() ?: emptyList()

    val activeItems: List<CraftHelperRecipe>
        get() = when (val cat = activeCategory) {
            null -> items
            else -> if (cat.isDefault()) {
                items.filter { it.group == null }
            } else {
                items.filter { it.group == cat.identifier }
            }
        }

    val canModifyCount: Boolean get() = activeItems.any { it.canModifyCount }
    val recipeType get() = activeItems.firstOrNull()?.type

    val data get() = activeItems.firstOrNull()
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

    fun getAmountAt(index: Int): Int = when (val recipe = activeItems.getOrNull(index)) {
        is NormalCraftHelperRecipe -> recipe.amount
        is SkyShardsRecipe -> recipe.tree.quantity
        else -> 1
    }

    fun canModifyCountAt(index: Int): Boolean = activeItems.getOrNull(index)?.canModifyCount == true

    fun addItem(item: SkyBlockId?): Boolean {
        item ?: return false
        val current = items.toMutableList()
        if (current.any { it is NormalCraftHelperRecipe && it.item == item }) return false
        val group = activeCategory?.takeUnless { it.isDefault() }?.identifier
        current.add(NormalCraftHelperRecipe(item, group = group))
        storage.set(current)
        save()
        return true
    }

    fun removeItem(index: Int) {
        val activeRecipe = activeItems.getOrNull(index) ?: return
        val globalIndex = items.indexOfFirst { it === activeRecipe }
        if (globalIndex == -1) return
        val current = items.toMutableList()
        current.removeAt(globalIndex)
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
        val activeRecipe = activeItems.getOrNull(index) ?: return
        val globalIndex = items.indexOfFirst { it === activeRecipe }
        if (globalIndex == -1) return
        val current = items.toMutableList()
        when (val recipe = current[globalIndex]) {
            is NormalCraftHelperRecipe -> current[globalIndex] = recipe.copy(amount = coerced)
            else -> return
        }
        storage.set(current)
        save()
    }

    fun setSkyShards(recipe: SkyShardsMethod) {
        val current = items.toMutableList()
        if (current.isEmpty()) {
            current.add(SkyShardsRecipe(recipe, null))
        } else {
            current[0] = SkyShardsRecipe(recipe, null)
        }
        storage.set(current)
        save()
    }

    fun clear() {
        val active = activeCategory
        if (active == null) {
            storage.set(emptyList())
        } else {
            val toRemove = activeItems.toSet()
            val current = items.toMutableList()
            current.removeAll(toRemove)
            storage.set(current)
        }
        save()
    }

    fun save() {
        storage.save()
    }

    fun saveCategories() {
        categoryStorage.save()
    }

    fun createCategory(name: String): CraftHelperCategory {
        val category = CraftHelperCategory(UUID.randomUUID(), name)
        val current = categories.toMutableList()
        current.add(category)
        categoryStorage.set(current)
        saveCategories()
        return category
    }

    fun deleteCategory(category: CraftHelperCategory) {
        if (activeCategory?.identifier == category.identifier) {
            activeCategory = null
        }

        val cats = categories.toMutableList()
        cats.removeIf { it.identifier == category.identifier }
        categoryStorage.set(cats)
        val recipes = items.toMutableList()
        recipes.removeAll { it.group == category.identifier }
        storage.set(recipes)
        save()
        saveCategories()
    }

    private fun resolveCategoryKey(key: String): CraftHelperCategory? = when (key) {
        ALL_CATEGORY_KEY -> null
        defaultCategory.identifier.toString() -> defaultCategory
        else -> categories.firstOrNull { it.identifier.toString() == key }
    }
}
