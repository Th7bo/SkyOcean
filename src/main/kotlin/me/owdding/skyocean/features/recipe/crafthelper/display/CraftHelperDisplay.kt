package me.owdding.skyocean.features.recipe.crafthelper.display

import earth.terrarium.olympus.client.utils.ListenableState
import me.owdding.lib.builder.LayoutFactory
import me.owdding.lib.builder.MIDDLE
import me.owdding.lib.displays.Displays
import me.owdding.lib.displays.asButtonLeft
import me.owdding.lib.displays.withPadding
import me.owdding.lib.events.ItemListEvent
import me.owdding.lib.layouts.BackgroundWidget
import me.owdding.lib.layouts.asWidget
import me.owdding.lib.utils.MeowddingLogger
import me.owdding.lib.utils.MeowddingLogger.Companion.featureLogger
import me.owdding.skyocean.SkyOcean
import me.owdding.skyocean.config.features.misc.crafthelper.CraftHelperConfig
import me.owdding.skyocean.data.profile.CraftHelperStorage
import me.owdding.skyocean.features.item.sources.ItemSources
import me.owdding.skyocean.features.recipe.ItemLikeIngredient
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperTree
import me.owdding.skyocean.features.recipe.crafthelper.CraftHelperManager
import me.owdding.skyocean.features.recipe.crafthelper.eval.ItemTracker
import me.owdding.skyocean.features.recipe.crafthelper.views.CraftHelperState
import me.owdding.skyocean.features.recipe.crafthelper.views.SimpleRecipeView
import me.owdding.skyocean.features.recipe.crafthelper.views.WidgetBuilder
import me.owdding.skyocean.features.recipe.crafthelper.views.raw.RawFormatter
import me.owdding.skyocean.features.recipe.crafthelper.views.tree.TreeFormatter
import me.owdding.skyocean.features.recipe.serialize
import me.owdding.skyocean.utils.LateInitModule
import me.owdding.skyocean.utils.chat.Icons
import me.owdding.skyocean.utils.debugToggle
import me.owdding.skyocean.utils.extensions.asScrollable
import me.owdding.skyocean.utils.extensions.createIntInput
import me.owdding.skyocean.utils.extensions.tryClear
import me.owdding.skyocean.utils.extensions.withoutTooltipDelay
import me.owdding.skyocean.utils.rendering.ExtraDisplays
import me.owdding.skyocean.utils.setPosition
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.LayoutElement
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerCloseEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenInitializedEvent
import tech.thatgravyboat.skyblockapi.api.location.LocationAPI
import tech.thatgravyboat.skyblockapi.helpers.McFont
import tech.thatgravyboat.skyblockapi.helpers.McScreen
import tech.thatgravyboat.skyblockapi.utils.extentions.left
import tech.thatgravyboat.skyblockapi.utils.text.Text
import tech.thatgravyboat.skyblockapi.utils.text.TextColor
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.color
import kotlin.math.max

@LateInitModule
object CraftHelperDisplay : MeowddingLogger by SkyOcean.featureLogger() {

    private val ignoreChecks by debugToggle("cafthelper/ignore_checks")

    private var craftHelperLayout: LayoutElement? = null
    var currentPage = 0

    private const val BACKGROUND_PADDING = 14

    @Subscription
    fun onScreenInit(event: ScreenInitializedEvent) {
        if (!CraftHelperConfig.enabled && !ignoreChecks) return
        if (!LocationAPI.isOnSkyBlock && !ignoreChecks) return

        val screen = event.screen as? AbstractContainerScreen<*> ?: return

        val layout = LayoutFactory.empty() as FrameLayout
        lateinit var callback: (save: Boolean) -> Unit

        // - CraftHelperConfig.margin * 2 (customizable left/right margin)
        // - BACKGROUND_PADDING * 2 (Background padding)
        val maxAvailableWidth = max(50, screen.left - (CraftHelperConfig.margin * 2) - (BACKGROUND_PADDING * 2))

        fun resetLayout() {
            layout.visitWidgets { event.widgets.remove(it) }
        }
        callback = callback@{ save ->
            val trees = CraftHelperManager.resolveAll(::resetLayout, CraftHelperManager::clear)
            if (trees.isEmpty()) return@callback

            val multiItem = trees.size > 1
            currentPage = currentPage.coerceIn(0, if (multiItem) trees.size else 0)

            resetLayout()
            layout.tryClear()

            val widget = when {
                multiItem && currentPage == 0 -> visualizeTotal(
                    trees, maxAvailableWidth, callback,
                    onNext = { currentPage = 1; callback(false) },
                    onClearAll = { CraftHelperStorage.clear(); callback(false) },
                )
                multiItem -> {
                    val idx = currentPage - 1
                    visualize(
                        trees[idx], trees[idx].output, idx, maxAvailableWidth, callback,
                        onPrev = { currentPage--; callback(false) },
                        onNext = if (currentPage < trees.size) { { currentPage++; callback(false) } } else null,
                        onRemove = { CraftHelperStorage.removeItem(idx); currentPage = 0; callback(false) },
                    )
                }
                else -> visualize(
                    trees[0], trees[0].output, 0, maxAvailableWidth, callback,
                    onPrev = null,
                    onNext = null,
                    onRemove = { CraftHelperStorage.clear(); callback(false) },
                )
            }

            layout.addChild(widget)
            layout.arrangeElements()
            layout.setPosition(CraftHelperConfig.position.position(layout.width, layout.height))
            layout.visitWidgets { event.widgets.add(it) }
            this.craftHelperLayout = layout
            if (save) CraftHelperStorage.save()
        }
        callback(false)
    }

    @Subscription
    fun onItemListRender(event: ItemListEvent.RegisterExclusionZones) {
        craftHelperLayout?.let {
            event.register(it.x, it.y, it.width, it.height)
        }
    }

    @Subscription(ContainerCloseEvent::class)
    fun onScreenClose() {
        craftHelperLayout = null
    }

    @Suppress("LongMethod")
    private fun visualizeTotal(
        trees: List<CraftHelperTree>,
        maxWidth: Int,
        refreshCallback: (save: Boolean) -> Unit,
        onNext: () -> Unit,
        onClearAll: () -> Unit,
    ): AbstractWidget {
        val sources = ItemSources.craftHelperSources - CraftHelperConfig.disallowedSources.toSet()
        val tracker = ItemTracker(sources)
        val builder = WidgetBuilder(refreshCallback = refreshCallback)

        val allLeafStates = mutableListOf<CraftHelperState>()
        trees.forEach { tree ->
            var root: CraftHelperState? = null
            SimpleRecipeView { root = it }.visit(tree, tracker)
            root?.collect()?.filter { !it.hasChildren }?.let { allLeafStates.addAll(it) }
        }
        val mergedStates = allLeafStates
            .groupBy { it.ingredient.serialize() }
            .values
            .map(CraftHelperState::merge)

        return LayoutFactory.vertical(2) {
            var maxLine = 0
            var lines = 0
            val body = LayoutFactory.vertical {
                val list = mutableListOf<AbstractWidget>()
                if (mergedStates.isEmpty()) {
                    lines = 1
                    textDisplay("All materials ready!") { this.color = TextColor.GREEN }
                } else {
                    mergedStates.forEachIndexed { index, mergedState ->
                        if (mergedState.isDone() && CraftHelperConfig.rawFormatterHideCompleted) return@forEachIndexed
                        lines++
                        val prefix = if (index < mergedStates.size - 1) "├ " else "└ "
                        context(mergedState) {
                            val w = builder.listEntry(prefix)
                            maxLine = maxOf(maxLine, w.width + 10)
                            list.add(w)
                        }
                    }
                    list.forEach(::widget)
                }
            }.apply { visitChildren { child -> maxLine = maxOf(maxLine, child.width + 10) } }

            val contentWidth = minOf(maxLine, maxWidth)

            horizontal(5, MIDDLE) {
                vertical(alignment = MIDDLE) {
                    spacer(max(0, contentWidth - 10))
                    display(Displays.component(Text.of("Total") { this.color = TextColor.GREEN }))
                    display(Displays.component(Text.of("${trees.size} items") { this.color = TextColor.GRAY }))
                }
                vertical(alignment = MIDDLE) {
                    widget(
                        Displays.component(Text.of(Icons.CROSS) { this.color = TextColor.RED }).asButtonLeft {
                            onClearAll()
                        }.withoutTooltipDelay().withTooltip(Text.of("Clear all") { this.color = TextColor.RED }),
                    )
                    string("")
                }
            }

            widget(body.asScrollable(contentWidth, McFont.height * 20.coerceAtMost(lines)))
        }.asWidget().let { innerWidget ->
            val navW = 10
            val h = innerWidget.height
            val withNav = LayoutFactory.horizontal(0, MIDDLE) {
                widget(
                    Displays.center(navW, h, Displays.component(component = Text.of("<") { this.color = TextColor.DARK_GRAY }, shadow = true))
                        .asButtonLeft { }.withoutTooltipDelay(),
                )
                widget(innerWidget)
                widget(
                    Displays.center(navW, h, Displays.component(component = Text.of(">") { this.color = TextColor.GREEN }, shadow = true))
                        .asButtonLeft { onNext() }.withoutTooltipDelay(),
                )
            }.asWidget()
            val background = BackgroundWidget(
                SkyOcean.minecraft("tooltip/background"), SkyOcean.minecraft("tooltip/frame"),
                widget = withNav, padding = BACKGROUND_PADDING,
            )
            background.setPosition(CraftHelperConfig.margin, (McScreen.self?.height?.div(2) ?: 0) - (withNav.height / 2))
            background
        }
    }

    @Suppress("LongMethod")
    private fun visualize(
        tree: CraftHelperTree,
        output: ItemLikeIngredient,
        itemIndex: Int,
        maxWidth: Int,
        refreshCallback: (save: Boolean) -> Unit,
        onPrev: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onRemove: () -> Unit,
    ): AbstractWidget {
        val sources = ItemSources.craftHelperSources - CraftHelperConfig.disallowedSources.toSet()
        val tracker = ItemTracker(sources)

        val canModify = CraftHelperStorage.canModifyCountAt(itemIndex)
        val selectedAmount = CraftHelperStorage.getAmountAt(itemIndex)

        return LayoutFactory.vertical(2) {
            var maxLine = 0
            var lines = 0
            val body = LayoutFactory.vertical {
                val list = mutableListOf<AbstractWidget>()
                runCatching {
                    val formatter = when (CraftHelperConfig.formatter) {
                        CraftHelperFormat.RAW -> RawFormatter
                        CraftHelperFormat.TREE -> TreeFormatter
                    }

                    formatter.format(tree, tracker, WidgetBuilder(refreshCallback = refreshCallback)) {
                        lines++
                        maxLine = maxOf(maxLine, it.width + 10)
                        list.add(it)
                    }
                }.onSuccess { list.forEach(::widget) }
                    .onFailure {
                        lines = 2
                        textDisplay {
                            append("An error occurred while displaying the recipe!")
                            this.color = TextColor.RED
                        }
                        textDisplay {
                            append("Error: ${it.message}")
                            this.color = TextColor.RED
                        }
                    }
            }.apply { visitChildren { child -> maxLine = maxOf(maxLine, child.width + 10) } }

            val contentWidth = minOf(maxLine, maxWidth)

            horizontal(5, MIDDLE) {
                val item = ExtraDisplays.inventoryBackground(1, 1, Displays.item(output.item, showTooltip = true).withPadding(2))
                val titleWidth = max(0, contentWidth - item.getWidth() - 10)
                display(item)
                vertical(alignment = MIDDLE) {
                    spacer(titleWidth)
                    display(Displays.fixed(titleWidth, McFont.height, Displays.component(output.itemName)))
                    horizontal {
                        widget(
                            Displays.component(
                                Text.of {
                                    append("-")
                                    this.color = if (canModify) TextColor.RED else TextColor.GRAY
                                },
                            ).asButtonLeft {
                                if (!canModify) return@asButtonLeft

                                val value = selectedAmount / (tree.amountPerCraft)
                                val newValue = when {
                                    McScreen.isControlDown -> value - 64
                                    McScreen.isShiftDown -> value - 10
                                    else -> value - 1
                                }
                                CraftHelperStorage.setAmountAt(itemIndex, max(1, newValue) * tree.amountPerCraft)
                                refreshCallback(true)
                            }.withTooltip(
                                Text.multiline(
                                    "§eClick§r to decrease by §c1",
                                    "§eShift + Click§r to decrease by §c10",
                                    "§eCtrl + Click§r to decrease by §c64",
                                ).apply { this.color = TextColor.GRAY },
                            ).withoutTooltipDelay(),
                        )
                        if (canModify) {
                            val amountState = ListenableState.of(selectedAmount)
                            amountState.registerListener { value ->
                                val crafts = max(1, value / tree.amountPerCraft)
                                CraftHelperStorage.setAmountAt(itemIndex, crafts * tree.amountPerCraft)
                            }
                            widget(createIntInput(state = amountState, width = 55, height = McFont.height + 2) as AbstractWidget)
                            widget(
                                Displays.component(
                                    Text.of(Icons.CHECKMARK) { this.color = TextColor.GREEN },
                                ).asButtonLeft {
                                    refreshCallback(true)
                                }.withTooltip(
                                    Text.of("Apply amount") { this.color = TextColor.GRAY },
                                ).withoutTooltipDelay(),
                            )
                        } else {
                            textDisplay(" $selectedAmount ", shadow = true) {
                                this.color = TextColor.DARK_GRAY
                            }
                        }
                        widget(
                            Displays.component(
                                Text.of {
                                    append("+")
                                    this.color = if (canModify) TextColor.GREEN else TextColor.GRAY
                                },
                            ).asButtonLeft {
                                if (!canModify) return@asButtonLeft
                                val value = selectedAmount / tree.amountPerCraft
                                val newValue = when {
                                    McScreen.isControlDown -> value + 64
                                    McScreen.isShiftDown -> value + 10
                                    else -> value + 1
                                }
                                CraftHelperStorage.setAmountAt(itemIndex, newValue * tree.amountPerCraft)
                                refreshCallback(true)
                            }.withTooltip(
                                Text.multiline(
                                    "§eClick§r to increase by §a1",
                                    "§eShift + Click§r to increase by §a10",
                                    "§eCtrl + Click§r to increase by §a64",
                                ).apply { this.color = TextColor.GRAY },
                            ).withoutTooltipDelay(),
                        )
                    }
                }
                vertical(alignment = MIDDLE) {
                    widget(
                        Displays.component(Text.of(Icons.CROSS) { this.color = TextColor.RED }).asButtonLeft {
                            onRemove()
                        }.withoutTooltipDelay().withTooltip(Text.of("Remove") { this.color = TextColor.RED }),
                    )
                    string("")
                }
            }

            widget(body.asScrollable(contentWidth, McFont.height * 20.coerceAtMost(lines)))
        }.asWidget().let { innerWidget ->
            val withNav = if (onPrev != null || onNext != null) {
                val navW = 10
                val h = innerWidget.height
                LayoutFactory.horizontal(0, MIDDLE) {
                    widget(
                        Displays.center(navW, h, Displays.component(component = Text.of("<") { this.color = if (onPrev != null) TextColor.GREEN else TextColor.DARK_GRAY }, shadow = true))
                            .asButtonLeft { onPrev?.invoke() }.withoutTooltipDelay(),
                    )
                    widget(innerWidget)
                    widget(
                        Displays.center(navW, h, Displays.component(component = Text.of(">") { this.color = if (onNext != null) TextColor.GREEN else TextColor.DARK_GRAY }, shadow = true))
                            .asButtonLeft { onNext?.invoke() }.withoutTooltipDelay(),
                    )
                }.asWidget()
            } else {
                innerWidget
            }
            val background = BackgroundWidget(
                SkyOcean.minecraft("tooltip/background"), SkyOcean.minecraft("tooltip/frame"),
                widget = withNav, padding = BACKGROUND_PADDING,
            )
            background.setPosition(CraftHelperConfig.margin, (McScreen.self?.height?.div(2) ?: 0) - (withNav.height / 2))
            background
        }
    }
}
