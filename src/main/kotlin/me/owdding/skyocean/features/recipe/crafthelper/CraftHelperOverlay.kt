package me.owdding.skyocean.features.recipe.crafthelper

import earth.terrarium.olympus.client.ui.context.ContextMenu
import me.owdding.lib.builder.LayoutFactory
import me.owdding.lib.displays.Displays
import me.owdding.lib.displays.asWidget
import me.owdding.lib.overlays.Position
import me.owdding.skyocean.config.CachedValue
import me.owdding.skyocean.config.features.misc.CraftHelperConfig
import me.owdding.skyocean.config.hidden.OverlayPositions
import me.owdding.skyocean.features.recipe.crafthelper.display.CraftHelperDisplay
import me.owdding.skyocean.features.recipe.crafthelper.views.CraftHelperState
import me.owdding.skyocean.features.recipe.crafthelper.views.WidgetBuilder
import me.owdding.skyocean.features.recipe.crafthelper.views.raw.RawFormatter
import me.owdding.skyocean.features.recipe.serialize
import me.owdding.skyocean.utils.Utils.text
import me.owdding.skyocean.utils.Utils.unaryPlus
import me.owdding.skyocean.utils.rendering.OceanTextures
import me.owdding.skyocean.utils.rendering.Overlay
import me.owdding.skyocean.utils.rendering.SkyOceanOverlay
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import tech.thatgravyboat.skyblockapi.api.location.LocationAPI
import tech.thatgravyboat.skyblockapi.platform.drawSprite
import tech.thatgravyboat.skyblockapi.utils.extentions.toFormattedString
import tech.thatgravyboat.skyblockapi.utils.extentions.translated
import tech.thatgravyboat.skyblockapi.utils.text.Text
import tech.thatgravyboat.skyblockapi.utils.text.TextColor
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.color
import kotlin.time.Duration.Companion.milliseconds

@Overlay
object CraftHelperOverlay : SkyOceanOverlay() {
    private val layoutCache = CachedValue(250.milliseconds) {
        val roots = CraftHelperManager.lastEvaluatedRoots.get()?.takeIf { it.isNotEmpty() } ?: return@CachedValue null
        val builder = WidgetBuilder(true) {}
        val page = CraftHelperDisplay.currentPage

        val specificIndex = if (roots.size > 1 && page > 0) (page - 1).coerceAtMost(roots.size - 1) else -1

        if (specificIndex >= 0) {
            val state = roots[specificIndex]
            LayoutFactory.vertical {
                context(state) {
                    widget(
                        Displays.row(
                            Displays.text(
                                text {
                                    append(state.required.toFormattedString())
                                    append("x ")
                                    this.color = TextColor.GRAY
                                },
                            ),
                            Displays.text(builder.name()),
                        ).asWidget(),
                    ) { alignHorizontallyCenter() }
                }
                spacer(height = 2)
                RawFormatter.WITHOUT_PREFIX.create(state, builder, ::widget)
            }
        } else if (roots.size == 1) {
            val state = roots.first()
            LayoutFactory.vertical {
                context(state) {
                    widget(
                        Displays.row(
                            Displays.text(
                                text {
                                    append(state.required.toFormattedString())
                                    append("x ")
                                    this.color = TextColor.GRAY
                                },
                            ),
                            Displays.text(builder.name()),
                        ).asWidget(),
                    ) { alignHorizontallyCenter() }
                }
                spacer(height = 2)
                RawFormatter.WITHOUT_PREFIX.create(state, builder, ::widget)
            }
        } else {
            val allLeaves = roots.flatMap { root -> root.collect().filter { !it.hasChildren } }
            val mergedLeaves = allLeaves
                .groupBy { it.ingredient.serialize() }
                .values
                .map(CraftHelperState::merge)

            LayoutFactory.vertical {
                textDisplay("Total (${roots.size} items)", 0u, true) { this.color = TextColor.GREEN }
                spacer(height = 2)
                mergedLeaves.forEach { mergedState ->
                    if (mergedState.isDone() && CraftHelperConfig.rawFormatterHideCompleted) return@forEach
                    context(mergedState) {
                        widget(builder.listEntry(""))
                    }
                }
            }
        }
    }
    private val layout by layoutCache

    override val name: Component = +"overlays.crafthelper"
    override val position: Position = OverlayPositions.craftHelper
    private val background get() = CraftHelperConfig.overlayBackground
    val padding get() = if (background) 3 else 0
    override val bounds: Pair<Int, Int> get() = (layout?.width?.plus(padding * 2) ?: 0) to (layout?.height?.plus(padding * 2) ?: 0)

    override val enabled: Boolean
        get() = CraftHelperConfig.enableOverlay &&
            CraftHelperManager.lastEvaluatedRoots.get()?.isNotEmpty() == true &&
            LocationAPI.isOnSkyBlock

    override fun extract(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (padding != 0) {
            graphics.drawSprite(OceanTextures.overlayBackground, 0, 0, bounds.first, bounds.second)
        }

        graphics.translated(padding, padding) {
            //~ if >= 26.1 'render(' -> 'extractRenderState('
            layout?.visitWidgets { it.extractRenderState(graphics, -1, -1, partialTicks) }
        }
    }

    override fun onRightClick() = ContextMenu.open {
        it.button(+"overlays.background.${if (background) "disable" else "enable"}") {
            CraftHelperConfig.overlayBackground = !CraftHelperConfig.overlayBackground
        }
        it.divider()
        it.dangerButton(Text.translatable("mlib.overlay.edit.reset")) {
            position.resetPosition()
        }
    }
}
