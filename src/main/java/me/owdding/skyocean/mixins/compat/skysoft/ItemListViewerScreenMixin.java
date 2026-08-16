package me.owdding.skyocean.mixins.compat.skysoft;

import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import com.skysoft.data.skyblock.ItemListEntryKey;
import com.skysoft.data.skyblock.SkyBlockRecipe;
import com.skysoft.features.inventory.itemlist.ItemListViewerScreen;
import com.skysoft.features.inventory.itemlist.ViewerCraftingLayout;
import com.skysoft.features.inventory.itemlist.ViewerProcessLayout;
import com.skysoft.utils.gui.Rect;
import kotlin.Pair;
import me.owdding.skyocean.compat.skysoft.SkyOceanSkysoftCraftHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@IfModLoaded("skysoft")
@Mixin(ItemListViewerScreen.class)
public class ItemListViewerScreenMixin {

    // The viewer redraws from scratch every frame, so the previous frame's button bounds are dropped here.
    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void skyocean$clearCraftHelperButtons(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
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

    // HEAD so the button wins over the result slot's own navigation click.
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyocean$clickCraftHelperButton(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (SkyOceanSkysoftCraftHelper.INSTANCE.clickButton((int) click.x(), (int) click.y())) {
            cir.setReturnValue(true);
        }
    }
}
