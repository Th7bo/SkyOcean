package me.owdding.skyocean.features.recipe.crafthelper

import com.teamresourceful.resourcefullib.common.color.Color
import earth.terrarium.olympus.client.ui.UIIcons
import me.owdding.lib.builder.LayoutFactory
import me.owdding.skyocean.SkyOcean
import me.owdding.skyocean.data.profile.CraftHelperStorage
import me.owdding.skyocean.features.recipe.crafthelper.data.NormalCraftHelperRecipe
import me.owdding.skyocean.features.recipe.crafthelper.data.SkyShardsRecipe
import me.owdding.skyocean.utils.SkyOceanScreen
import me.owdding.skyocean.utils.chat.CatppuccinColors
import me.owdding.skyocean.utils.chat.ChatUtils
import me.owdding.skyocean.utils.extensions.asScrollableWidget
import me.owdding.skyocean.utils.extensions.asWidget
import me.owdding.skyocean.utils.extensions.bottomCenter
import me.owdding.skyocean.utils.extensions.createButton
import me.owdding.skyocean.utils.extensions.createSeparator
import me.owdding.skyocean.utils.extensions.createText
import me.owdding.skyocean.utils.extensions.framed
import me.owdding.skyocean.utils.extensions.middleCenter
import me.owdding.skyocean.utils.extensions.middleLeft
import me.owdding.skyocean.utils.extensions.middleRight
import me.owdding.skyocean.utils.extensions.setScreen
import me.owdding.skyocean.utils.extensions.string
import me.owdding.skyocean.utils.extensions.topCenter
import me.owdding.skyocean.utils.extensions.withPadding
import me.owdding.skyocean.utils.extensions.withTexturedBackground
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.gui.layouts.LayoutElement
import tech.thatgravyboat.skyblockapi.helpers.McFont
import tech.thatgravyboat.skyblockapi.utils.text.Text
import tech.thatgravyboat.skyblockapi.utils.text.TextBuilder.append
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.color
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.underlined

object CraftHelperScreen : SkyOceanScreen("Craft Helper") {

    private val unhovered = CatppuccinColors.Mocha.subtext0Color
    private val hovered = CatppuccinColors.Mocha.lavenderColor

    val headerSprite = WidgetSprites(
        SkyOcean.id("hotkey/header"),
        SkyOcean.id("hotkey/header_hovered"),
    )

    var currentCategoryScroll = { 0 }
    var currentMainScroll = { 0 }
    const val SPACER = 5

    var currentCategory: CraftHelperCategory? = null
        set(value) {
            field = value
            CraftHelperStorage.activeCategory = value
        }

    private val currentRecipes: List<CraftHelperRecipe>
        get() = when (val cat = currentCategory) {
            null -> CraftHelperStorage.items
            else -> if (cat.isDefault()) {
                CraftHelperStorage.items.filter { it.group == null }
            } else {
                CraftHelperStorage.items.filter { it.group == cat.identifier }
            }
        }

    fun createLeftPanel(sliceWidth: Int, height: Int): LayoutElement {
        val header = LayoutFactory.frame(sliceWidth, SPACER * 4) {
            string("Categories", CatppuccinColors.Mocha.text, middleCenter)
        }.withTexturedBackground("hotkey/header")

        val categoriesContent = LayoutFactory.frame(height = height - header.height - SPACER) {
            LayoutFactory.vertical {
                createCategoryEntry(null, sliceWidth, 15).add()
                createCategoryEntry(CraftHelperStorage.defaultCategory, sliceWidth, 15).add()
                CraftHelperStorage.categories.forEach { category ->
                    createCategoryEntry(category, sliceWidth, 15).add()
                }
                createButton(
                    texture = null,
                    icon = UIIcons.PLUS,
                    color = unhovered,
                    hoveredColor = hovered,
                    hover = Text.of("Add category", CatppuccinColors.Mocha.text),
                    leftClick = setScreen {
                        EditCraftHelperCategoryModal(this@CraftHelperScreen, sliceWidth) { name ->
                            currentCategory = CraftHelperStorage.createCategory(name)
                        }
                    },
                ).withPadding(bottom = SPACER, top = SPACER).add { alignHorizontallyCenter() }
            }.add(topCenter)
        }

        return LayoutFactory.frame(sliceWidth, height) {
            header.add { alignVerticallyTop() }
            categoriesContent.asScrollableWidget(
                width = sliceWidth,
                height = height - header.height - SPACER,
                alwaysShowScrollBar = true,
            ).apply {
                withScrollY(currentCategoryScroll())
                currentCategoryScroll = { yScroll }
            }.withPadding(bottom = SPACER).add(bottomCenter)
        }
    }

    fun createRightPanel(sliceWidth: Int, height: Int): LayoutElement {
        val panelWidth = sliceWidth * 2 + SPACER
        val recipes = currentRecipes

        val isUserCategory = currentCategory != null && currentCategory?.isDefault() == false

        val header = LayoutFactory.frame(panelWidth, SPACER * 6) {
            LayoutFactory.vertical {
                val cat = currentCategory
                createText(if (cat == null) "All" else cat.name, CatppuccinColors.Mocha.sky) {
                    append(" Recipes") { color = CatppuccinColors.Mocha.text }
                }.withPadding(left = SPACER).add()
            }.add(middleLeft)

            if (isUserCategory) {
                LayoutFactory.horizontal {
                    createButton(
                        texture = null,
                        icon = UIIcons.PENCIL,
                        color = unhovered,
                        hoveredColor = hovered,
                        hover = Text.of("Edit category", CatppuccinColors.Mocha.text),
                        leftClick = setScreen {
                            val cat = currentCategory ?: return@setScreen null
                            EditCraftHelperCategoryModal(this@CraftHelperScreen, sliceWidth, cat) { name ->
                                cat.name = name
                                CraftHelperStorage.saveCategories()
                            }
                        },
                    ).withPadding(right = SPACER).add()
                    createButton(
                        texture = null,
                        icon = UIIcons.TRASH,
                        color = unhovered,
                        hoveredColor = hovered,
                        hover = Text.of("Delete category", CatppuccinColors.Mocha.text),
                        leftClick = setScreen {
                            val cat = currentCategory ?: return@setScreen null
                            DeleteCraftHelperCategoryModal(this@CraftHelperScreen, sliceWidth, cat) {
                                currentCategory = null
                            }
                        },
                    ).withPadding(right = SPACER).add()
                }.add(middleRight)
            }
        }.withTexturedBackground("hotkey/header")

        val mainSectionHeight = height - header.height - SPACER
        val mainSection = LayoutFactory.vertical {
            if (recipes.isEmpty()) {
                createText("No items in this category.", CatppuccinColors.Mocha.subtext0)
                    .withPadding(SPACER).add { alignHorizontallyCenter() }
            } else {
                recipes.forEachIndexed { idx, recipe ->
                    createRecipeEntry(recipe, idx, panelWidth, SPACER * 7).add()
                    createSeparator(panelWidth - SPACER * 2).add { alignHorizontallyCenter() }
                }
            }
        }.framed(height = mainSectionHeight) {
            alignVerticallyTop()
        }.asScrollableWidget(
            panelWidth,
            mainSectionHeight,
            alwaysShowScrollBar = true,
        ).apply {
            withScrollY(currentMainScroll())
            currentMainScroll = { yScroll }
        }.withPadding(bottom = SPACER)

        return LayoutFactory.frame(panelWidth, height) {
            header.add(topCenter)
            mainSection.add(bottomCenter)
        }
    }

    override fun init() {
        val height = height - height / 3
        val sliceWidth = width / 6

        val leftPanel = createLeftPanel(sliceWidth, height)
        val rightPanel = createRightPanel(sliceWidth, height)

        LayoutFactory.frame {
            vertical(SPACER) {
                LayoutFactory.frame {
                    spacer(sliceWidth * 3 + SPACER * 2, SPACER * 4)
                    Text.of {
                        append(ChatUtils.prefix)
                    }.asWidget().withPadding(left = SPACER).add(middleCenter)
                }.withTexturedBackground("hotkey/background").add()
                horizontal(SPACER) {
                    leftPanel.withTexturedBackground("hotkey/background").add()
                    rightPanel.withTexturedBackground("hotkey/background").add()
                }
            }
        }.center().applyLayout()

        super.init()
    }

    override fun onClose() {
        currentCategoryScroll = { 0 }
        currentMainScroll = { 0 }
        super.onClose()
    }

    private fun createCategoryEntry(category: CraftHelperCategory?, width: Int, height: Int): LayoutElement = LayoutFactory.frame(width, height) {
        val isSelected = currentCategory == category
        val displayColor: Color = if (isSelected) hovered else unhovered
        val displayName = category?.name ?: "All"
        spacer(width, height)
        createButton(
            texture = null,
            text = Text.of(displayName) { underlined = isSelected },
            color = displayColor,
            hoveredColor = hovered,
            width = width,
            height = height,
            click = withRebuild {
                currentCategory = category
            },
        ).add(middleCenter)
    }

    private fun createRecipeEntry(recipe: CraftHelperRecipe, idx: Int, width: Int, height: Int): LayoutElement = LayoutFactory.frame(width, height) {
        val name = when (recipe) {
            is NormalCraftHelperRecipe -> recipe.item?.toItem()?.hoverName ?: Text.of(recipe.item?.id ?: "Unknown")
            is SkyShardsRecipe -> Text.of("Sky Shards: ${recipe.tree.shard.id}")
            else -> Text.of("Unknown")
        }

        LayoutFactory.vertical {
            createText(name).withPadding(left = 2).add()
            if (recipe is NormalCraftHelperRecipe) {
                createText("x${recipe.amount}", CatppuccinColors.Mocha.subtext0)
                    .withPadding(left = 2, top = 2).add()
            }
        }.withPadding(left = SPACER).add(middleLeft)

        val removeText = Text.of("Remove", CatppuccinColors.Mocha.red)
        createButton(
            texture = headerSprite,
            text = removeText,
            width = McFont.width(removeText) + SPACER * 2,
            height = SPACER * 3,
            click = withRebuild {
                CraftHelperStorage.removeItem(idx)
            },
        ).withPadding(right = SPACER).add(middleRight)
    }

    private fun withRebuild(action: () -> Unit): () -> Unit = {
        action()
        rebuildWidgets()
    }
}
