package me.owdding.skyocean.features.recipe.crafthelper

import com.mojang.blaze3d.platform.InputConstants
import me.owdding.ktmodules.Module
import me.owdding.lib.compat.REIRuntimeCompatability
import me.owdding.lib.events.ItemListEvent
import me.owdding.skyocean.ApiDebug
import me.owdding.skyocean.config.SkyOceanKeybind
import me.owdding.skyocean.config.features.misc.crafthelper.CraftHelperConfig
import me.owdding.skyocean.config.features.misc.crafthelper.CraftHelperNotificationType
import me.owdding.skyocean.data.profile.CraftHelperStorage
import me.owdding.skyocean.events.RegisterSkyOceanCommandEvent
import me.owdding.skyocean.data.profile.CraftHelperStorage.setSelected
import me.owdding.skyocean.features.item.search.highlight.ItemHighlighter
import me.owdding.skyocean.features.item.search.search.ReferenceItemFilter
import me.owdding.skyocean.features.item.sources.ItemSources
import me.owdding.skyocean.features.recipe.crafthelper.eval.CraftHelperEtaTracker
import me.owdding.skyocean.features.recipe.crafthelper.eval.ItemTracker
import me.owdding.skyocean.features.recipe.serialize
import me.owdding.skyocean.features.recipe.crafthelper.views.CraftHelperState
import me.owdding.skyocean.features.recipe.crafthelper.views.SimpleRecipeView
import me.owdding.skyocean.features.recipe.crafthelper.visitors.CompactedResourceCutoffTreeTransformer
import me.owdding.skyocean.utils.Utils.refreshScreen
import me.owdding.skyocean.utils.Utils.text
import me.owdding.skyocean.utils.chat.ChatUtils
import me.owdding.skyocean.utils.chat.ChatUtils.sendWithPrefix
import me.owdding.skyocean.utils.debug.DebugBuilder
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.world.item.ItemStack
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.TimePassed
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenKeyReleasedEvent
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId.Companion.getSkyBlockId
import tech.thatgravyboat.skyblockapi.helpers.McClient
import tech.thatgravyboat.skyblockapi.helpers.McScreen
import tech.thatgravyboat.skyblockapi.utils.extentions.getHoveredSlot
import tech.thatgravyboat.skyblockapi.utils.extentions.toFormattedString
import tech.thatgravyboat.skyblockapi.utils.text.Text
import tech.thatgravyboat.skyblockapi.utils.text.TextBuilder.append
import tech.thatgravyboat.skyblockapi.utils.text.TextColor
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.bold
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.color
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

@Module
object CraftHelperManager {
    var lastData: List<CraftHelperRecipe> = emptyList()
    var hasBeenNotified = false
    var lastEvaluatedRoots: AtomicReference<List<CraftHelperState>?> = AtomicReference()
    private val keybind = SkyOceanKeybind("crafthelper", InputConstants.KEY_V)

    fun clear() {
        CraftHelperStorage.clear()
        CraftHelperStorage.save()
    }

    fun resolveAll(resetLayout: () -> Unit, clear: () -> Unit): List<CraftHelperTree> {
        val items = CraftHelperStorage.activeItems
        if (items.isEmpty()) {
            resetLayout()
            return emptyList()
        }

        val indicesToRemove = mutableListOf<Int>()
        val trees = mutableListOf<CraftHelperTree>()

        items.forEachIndexed { index, recipe ->
            var shouldRemove = false
            val tree = recipe.resolve(
                resetLayout = {},
                clear = { shouldRemove = true },
            )
            if (shouldRemove) {
                indicesToRemove.add(index)
            } else if (tree != null) {
                trees.add(getTransformers().fold(tree) { t, op -> op.apply(t) })
            }
        }

        indicesToRemove.reversed().forEach { CraftHelperStorage.removeItem(it) }

        if (trees.isEmpty()) resetLayout()
        return trees
    }

    fun resolve(resetLayout: () -> Unit, clear: () -> Unit): CraftHelperTree? =
        resolveAll(resetLayout, clear).firstOrNull()

    fun getTransformers(): List<UnaryOperator<CraftHelperTree>> = buildList {
        CraftHelperConfig.compactedCutoffDegree.takeIf { it > 0 }?.let {
            add { tree -> CompactedResourceCutoffTreeTransformer.apply(it, tree) }
        }
    }

    @Subscription(TickEvent::class)
    @TimePassed("5t")
    fun onTick() {
        if (lastData != CraftHelperStorage.activeItems) {
            this.lastData = CraftHelperStorage.activeItems
            hasBeenNotified = false
            lastEvaluatedRoots.set(null)
        }

        val trees = resolveAll({}, ::clear)
        if (trees.isEmpty()) return

        val tracker = ItemTracker(ItemSources.craftHelperSources - CraftHelperConfig.disallowedSources.toSet())
        val roots = mutableListOf<CraftHelperState>()

        trees.forEach { tree ->
            SimpleRecipeView { state ->
                if (state.path != "root") return@SimpleRecipeView
                roots.add(state)
            }.visit(tree, tracker)
        }

        lastEvaluatedRoots.set(roots)

        CraftHelperEtaTracker.update(
            roots.flatMap { it.collect() }
                .filterNot { it.hasChildren }
                .groupBy { it.ingredient.serialize() }
                .mapValues { (_, states) -> states.sumOf { it.amount } },
        )

        val allChildrenDone = roots.isNotEmpty() && roots.all { it.childrenDone }
        if (!allChildrenDone) return
        if (hasBeenNotified) return
        hasBeenNotified = true

        CraftHelperConfig.doneNotificationConfig.doneTypes.forEach { doneNotification(it, roots.size) }
    }

    fun doneNotification(type: CraftHelperNotificationType, rootCount: Int) {
        when (type) {
            CraftHelperNotificationType.DONE_MESSAGE -> {
                if (rootCount == 1) {
                    text("You have all materials to craft ") {
                        CraftHelperStorage.selectedItem?.toItem()?.hoverName?.let { item ->
                            append("${CraftHelperStorage.selectedAmount}x ") { color = TextColor.GREEN }
                            append(item)
                        } ?: append("your selected craft helper tree")
                        append("!")
                    }.sendWithPrefix()
                } else {
                    text("You have all materials for all ") {
                        append("$rootCount") { color = TextColor.GREEN }
                        append(" craft helper items!")
                    }.sendWithPrefix()
                }
            }
            CraftHelperNotificationType.DONE_TITLE -> {
                val title = if (rootCount == 1) {
                    CraftHelperStorage.selectedItem?.let {
                        Text.of {
                            append(ChatUtils.ICON_WITH_SPACE)
                            append("${CraftHelperStorage.selectedAmount}x ") { color = TextColor.GREEN }
                            append(it.toItem().hoverName)
                            append(" Craftable!") { color = TextColor.GREEN }
                        }
                    } ?: Text.of {
                        append("CraftHelper Item Craftable!") { this.color = TextColor.GREEN }
                    }
                } else {
                    Text.of {
                        append(ChatUtils.ICON_WITH_SPACE)
                        append("$rootCount") { color = TextColor.GREEN }
                        append(" CraftHelper Items Craftable!") { color = TextColor.GREEN }
                    }
                }
                McClient.setTitle(title, null, 0f, 3f, 0.5f)
            }
            CraftHelperNotificationType.DONE_SOUND -> {
                McClient.playSound(CraftHelperConfig.doneNotificationConfig.soundEvent)
            }
        }
    }

    @Subscription
    fun registerCommand(event: RegisterSkyOceanCommandEvent) {
        event.registerWithCallback("crafthelper") {
            McClient.setScreenAsync { CraftHelperScreen }
        }
    }

    @Subscription
    @OnlyOnSkyBlock
    fun onItemListKeybind(event: ScreenKeyReleasedEvent.Pre) {
        if (!keybind.matches(event)) return
        highlight(McScreen.asMenu?.getHoveredSlot()?.item)
    }

    @Subscription
    @OnlyOnSkyBlock
    fun onItemListKeybind(event: ItemListEvent.HoveredItemKeyPress) {
        if (!keybind.key.matches(event.event)) return
        setItem(event.stack)
    }

    @Subscription
    @OnlyOnSkyBlock
    fun onItemListWidget(event: ItemListEvent.RecipeButtonAdd) {
        event.itemStack.getSkyBlockId() ?: return
        event.register(
            Button.builder(Text.of("\uD83E\uDE93")) {
                // TODO: set actual recipe instead of this
                setItem(event.itemStack)
            }.apply {
                tooltip(Tooltip.create(Text.of("Set as SkyOcean CraftHelper Item")))
                size(12, 12)
            }.build(),
        )
    }

    private fun highlight(stack: ItemStack?) {
        setItem(stack?.takeUnless { it.isEmpty })
    }

    fun setItem(item: ItemStack?) {
        val item = item?.takeUnless { it.isEmpty } ?: return

        if (item.getSkyBlockId() == null) {
            Text.of("Item ") {
                append(item.hoverName)
                append(" does not have a SkyBlockId, cannot be selected as CraftHelper item")
                color = TextColor.RED
            }.sendWithPrefix("crafthelper_no_id")
            return
        }

        val skyBlockId = SkyBlockId.fromItem(item)
        val added = CraftHelperStorage.addItem(skyBlockId)
        McScreen.refreshScreen()

        if (added) {
            Text.of("Added ") {
                append(item.hoverName) { this.bold = true }
                append(" to the craft helper")
            }.sendWithPrefix()
        } else {
            Text.of {
                append(item.hoverName) { this.bold = true }
                append(" is already in the craft helper")
                color = TextColor.YELLOW
            }.sendWithPrefix()
        }
    }

    @ApiDebug("Craft Helper")
    internal fun debug(builder: DebugBuilder) = with(builder) {
        field("Items", CraftHelperStorage.items.size)
        CraftHelperStorage.items.forEachIndexed { i, recipe ->
            when (recipe) {
                is me.owdding.skyocean.features.recipe.crafthelper.data.NormalCraftHelperRecipe ->
                    field("Item $i", "${recipe.item} x${recipe.amount}")
                else -> field("Item $i", recipe.type.name)
            }
        }
        iterable("Allowed Sources", ItemSources.craftHelperSources - CraftHelperConfig.disallowedSources.toSet()) {
            literal(it.name)
        }
        val itemTracker = ItemTracker(ItemSources.craftHelperSources - CraftHelperConfig.disallowedSources.toSet())
        field("Total Items Tracked", itemTracker.items.values.flatten().sumOf { it.amount }, copyValue = buildString {
            appendLine("Currencies")
            appendLine()
            itemTracker.currencies.entries.sortedByDescending { (_, amount) -> amount }.forEach { (type, amount) ->
                appendLine("- $type: ${amount.toFormattedString()}")
            }
            appendLine()
            appendLine("Items")
            itemTracker.items.entries.sortedByDescending { (_, value) -> value.sumOf { it.amount } }.forEach { (id, sources) ->
                append("- ")
                append(id)
                append(": ")
                append(sources.sumOf { it.amount }.toFormattedString())
                append(" (")
                append(sources.map { 1 shl it.source.ordinal }.reduce(Int::or).toString(32))
                append(")")
                appendLine()
            }
        })
    }
}
