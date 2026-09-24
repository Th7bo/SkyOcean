package me.owdding.skyocean.data.profile

import com.mojang.serialization.Codec
import me.owdding.skyocean.SkyOcean
import me.owdding.skyocean.features.recipe.RepoApiRecipe
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperCategory
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperRecipe
import me.owdding.skyocean.features.recipe.crafthelper.data.NormalCraftHelperRecipe
import me.owdding.skyocean.features.recipe.crafthelper.data.RepoLibRecipeTree
import me.owdding.skyocean.features.recipe.crafthelper.data.SkyShardsMethod
import me.owdding.skyocean.features.recipe.crafthelper.data.SkyShardsRecipe
import me.owdding.skyocean.generated.SkyOceanCodecs
import me.owdding.skyocean.utils.LateInitModule
import me.owdding.skyocean.utils.codecs.CodecHelpers
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId
import java.util.UUID
import kotlin.math.ceil

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
            { recipe -> if (recipe is NormalCraftHelperRecipe && recipe.selectedItem == null) emptyList() else listOf(recipe) },
            { it.firstOrNull() ?: NormalCraftHelperRecipe(null, group = null) },
        )

    private val storage = SkyOcean.profileStorage<List<CraftHelperRecipe>>(
        fileName = "craft_helper",
        defaultData = { emptyList() },
        version = 3,
    ) { version ->
        when (version) {
            0 -> wrapInList(
                SkyOceanCodecs.NormalCraftHelperRecipeCodec.codec().xmap(
                    { recipe ->
                        NormalCraftHelperRecipe(
                            recipe.selectedItem?.id?.let { SkyBlockId.unknownType(it) },
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

    private val categoryStorage = SkyOcean.profileStorage<MutableList<CraftHelperCategory>>(
        fileName = "craft_helper_categories",
        defaultData = { mutableListOf() },
        version = 0,
    ) { version ->
        when (version) {
            0 -> SkyOceanCodecs.CraftHelperCategoryCodec.codec().listOf()
                .xmap({ it.toMutableList() }, { it.toList() })
            else -> CodecHelpers.unit { mutableListOf() }
        }
    }

    private val activeCategoryStorage = SkyOcean.profileStorage<String>(
        fileName = "craft_helper_active_category",
        defaultData = { ALL_CATEGORY_KEY },
        version = 0,
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

    val canModifyCount: Boolean get() = activeItems.any { it is CraftHelperRecipe.MutableCount }
    val recipeType get() = activeItems.firstOrNull()?.type

    val data get() = activeItems.firstOrNull()
    val selectedItem get() = data?.selectedItem
    val selectedAmount get() = data?.amount ?: 1

    fun getAmountAt(index: Int): Int = activeItems.getOrNull(index)?.amount ?: 1

    fun canModifyCountAt(index: Int): Boolean = activeItems.getOrNull(index) is CraftHelperRecipe.MutableCount

    fun addItem(item: SkyBlockId?): Boolean {
        item ?: return false
        val current = items.toMutableList()
        if (current.any { it is NormalCraftHelperRecipe && it.selectedItem == item }) return false
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

    /** Adds (or replaces the same-type/same-item entry) a fully-built recipe to the active list. */
    fun set(recipe: CraftHelperRecipe) {
        val current = items.toMutableList()
        val existing = recipe.selectedItem?.let { sel ->
            current.indexOfFirst { it.type == recipe.type && it.selectedItem == sel }
        } ?: -1
        if (existing != -1) current[existing] = recipe else current.add(recipe)
        storage.set(current)
        save()
    }

    fun setAmount(amount: Int) {
        setAmountAt(0, amount)
    }

    fun setAmountAt(index: Int, amount: Int) {
        var coerced = amount.coerceAtLeast(1)
        val activeRecipe = activeItems.getOrNull(index) ?: return
        val globalIndex = items.indexOfFirst { it === activeRecipe }
        if (globalIndex == -1) return
        val recipe = items[globalIndex]
        if (recipe !is CraftHelperRecipe.MutableCount) return
        if (recipe is CraftHelperRecipe.MultiplesOf) {
            coerced = ceil(coerced.toFloat() / recipe.multiples).toInt() * recipe.multiples
        }
        val current = items.toMutableList()
        current[globalIndex] = recipe.withAmount(coerced)
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

    fun setRepoLibRecipe(recipe: RepoApiRecipe) {
        val group = activeCategory?.takeUnless { it.isDefault() }?.identifier
        set(RepoLibRecipeTree(recipe, recipe.output?.amount ?: 1, group = group))
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
