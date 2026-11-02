package me.owdding.skyocean.mixins.compat.skysoft;

import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import com.skysoft.data.skyblock.ItemListEntryKey;
import com.skysoft.data.skyblock.SkyBlockRecipe;
import com.skysoft.features.inventory.itemlist.ItemListRecipeView;
import com.skysoft.features.inventory.itemlist.ItemListViewerLayout;
import com.skysoft.features.inventory.itemlist.ViewerCraftingLayout;
import com.skysoft.features.inventory.itemlist.ViewerInputResult;
import com.skysoft.features.inventory.itemlist.ViewerProcessLayout;
import com.skysoft.utils.gui.Rect;
import kotlin.Pair;
import me.owdding.skyocean.compat.skysoft.SkyOceanSkysoftCraftHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Skysoft used to render the recipe grid straight from {@code ItemListViewerScreen}; it now does so
 * from {@code ItemListRecipeView}, which owns the render, click and bounds state. The injected
 * method signatures survived that move unchanged, so only the target class and the two
 * screen-level hooks (clear/click) had to follow it here.
 */
@IfModLoaded("skysoft")
@Mixin(ItemListRecipeView.class)
public class ItemListRecipeViewMixin {

    // The viewer redraws from scratch every frame, so the previous frame's button bounds are dropped here.
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void skyocean$clearCraftHelperButtons(GuiGraphicsExtractor context, ItemListViewerLayout layout, int mouseX, int mouseY, CallbackInfo ci) {
        SkyOceanSkysoftCraftHelper.INSTANCE.clearButtons();
    }

    // TAIL so the button draws over the result item that was just rendered.
    @Inject(method = "renderCrafting", at = @At("TAIL"), require = 0)
    private void skyocean$addCraftingCraftHelperButton(
        GuiGraphicsExtractor context,
        Rect tile,
        SkyBlockRecipe.Crafting recipe,
        int mouseX,
        int mouseY,
        CallbackInfoReturnable<List<Pair<Rect, ItemListEntryKey>>> cir,
        @Local ViewerCraftingLayout crafting
    ) {
        SkyOceanSkysoftCraftHelper.INSTANCE.addButton(context, crafting.getResult(), recipe, mouseX, mouseY);
    }

    @Inject(method = "renderProcess", at = @At("TAIL"), require = 0)
    private void skyocean$addProcessCraftHelperButton(
        GuiGraphicsExtractor context,
        Rect tile,
        SkyBlockRecipe.Process recipe,
        int mouseX,
        int mouseY,
        CallbackInfoReturnable<List<Pair<Rect, ItemListEntryKey>>> cir,
        @Local ViewerProcessLayout process
    ) {
        SkyOceanSkysoftCraftHelper.INSTANCE.addButton(context, process.getResult(), recipe, mouseX, mouseY);
    }

    // HEAD so the button wins over the result slot's own navigation click. Skysoft routes every
    // left click in the recipe grid through click(), which ends in navigateIngredient.
    @Inject(method = "click", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyocean$clickCraftHelperButton(ItemListViewerLayout layout, int mouseX, int mouseY, CallbackInfoReturnable<ViewerInputResult> cir) {
        if (SkyOceanSkysoftCraftHelper.INSTANCE.clickButton(mouseX, mouseY)) {
            cir.setReturnValue(ViewerInputResult.HANDLED);
        }
    }
}
