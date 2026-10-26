package me.owdding.skyocean.data.profile

import com.mojang.serialization.Codec
import me.owdding.skyocean.SkyOcean
import me.owdding.skyocean.features.recipe.RepoApiRecipe
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperCategory
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperRecipe
import me.owdding.skyocean.features.recipe.crafthelper.data.IngredientCraftHelperRecipe
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
        "craft_helper",
        defaultData = { emptyList() },
        version = 3,
        codec = { version ->
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
    )

    private val categoryStorage = SkyOcean.profileStorage(
        fileName = "craft_helper_categories",
        defaultData = { mutableListOf<CraftHelperCategory>() },
        version = 0,
        codec = { version ->
            when (version) {
                0 -> SkyOceanCodecs.CraftHelperCategoryCodec.codec().listOf()
                    .xmap({ it.toMutableList() }, { it.toList() })

                else -> CodecHelpers.unit { mutableListOf() }
            }
        },
    )

    private val activeCategoryStorage = SkyOcean.profileStorage(
        fileName = "craft_helper_active_category",
        defaultData = { ALL_CATEGORY_KEY },
        version = 0,
        codec = { version ->
            when (version) {
                0 -> Codec.STRING
                else -> CodecHelpers.unit { ALL_CATEGORY_KEY }
            }
        },
    )

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
        current.add(NormalCraftHelperRecipe(item, group = activeGroup))
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

    private val activeGroup: UUID? get() = activeCategory?.takeUnless { it.isDefault() }?.identifier

    /**
     * Adds (or replaces the same-type/same-item entry) a fully-built recipe to the active list.
     *
     * Recipes built without a group (the in-GUI craft helper buttons) are filed under the active
     * category, otherwise they would be stored but filtered out of [activeItems] and look like
     * nothing happened.
     */
    fun set(recipe: CraftHelperRecipe) {
        val grouped = if (recipe.group == null) recipe.withGroup(activeGroup) else recipe
        val current = items.toMutableList()
        val existing = grouped.selectedItem?.let { sel ->
            current.indexOfFirst { it.type == grouped.type && it.selectedItem == sel }
        } ?: -1
        if (existing != -1) current[existing] = grouped else current.add(grouped)
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
            current.add(SkyShardsRecipe(recipe, activeGroup))
        } else {
            current[0] = SkyShardsRecipe(recipe, activeGroup)
        }
        storage.set(current)
        save()
    }

    fun setRepoLibRecipe(recipe: RepoApiRecipe) {
        set(RepoLibRecipeTree(recipe, recipe.output?.amount ?: 1, group = activeGroup))
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

    /**
     * Drops only the active category's custom ingredient recipe. Upstream could call [clear] for
     * this because it stored a single recipe; here [clear] would take the whole category with it.
     */
    fun removeIngredientRecipe() {
        val index = activeItems.indexOfFirst { it is IngredientCraftHelperRecipe }
        if (index != -1) removeItem(index)
    }

    fun <T> addToIngredientRecipe(recipe: T) where T : CraftHelperRecipe, T : CraftHelperRecipe.Ingredients {
        getOrCreateIngredientRecipe().add(recipe.entriesForAddition.map { it.withAmount(it.amount * recipe.amount) })
        save()
    }

    /**
     * Upstream keeps a single stored recipe, so it could swap the one slot out. Here the active
     * category holds a list, so reuse its ingredient recipe if it already has one and otherwise
     * seed a new one from the first entry, filed under the active category.
     */
    fun getOrCreateIngredientRecipe(): IngredientCraftHelperRecipe {
        activeItems.filterIsInstance<IngredientCraftHelperRecipe>().firstOrNull()?.let { return it }

        val seed = data
        val newRecipe = IngredientCraftHelperRecipe(group = activeGroup).apply {
            val inputs = (seed as? CraftHelperRecipe.Ingredients)?.entriesForAddition ?: return@apply
            add(inputs.map { it.withAmount(it.amount * seed.amount) })
        }
        set(newRecipe)
        // set() may store a withGroup copy, and callers mutate what they get back, so hand out
        // the instance that actually landed in storage rather than the local one.
        return activeItems.filterIsInstance<IngredientCraftHelperRecipe>().firstOrNull() ?: newRecipe
    }

}
