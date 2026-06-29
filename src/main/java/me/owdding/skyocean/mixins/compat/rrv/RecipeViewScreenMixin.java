package me.owdding.skyocean.mixins.compat.rrv;

//? 26.1 {
/*
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import me.owdding.skyocean.compat.rrv.SkyOceanRrvCraftHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@IfModLoaded("rrv")
@Mixin(RecipeViewScreen.class)
public class RecipeViewScreenMixin {

    // checkGui() is RRV's recipe-layout rebuild; it runs on open and on every recipe/type/page change,
    // and clears previously added recipe widgets first, so we re-add the craft-helper buttons here.
    @Inject(method = "checkGui", at = @At("TAIL"), require = 0)
    private void skyocean$addCraftHelperButton(CallbackInfo ci) {
        SkyOceanRrvCraftHelper.INSTANCE.addButtons((RecipeViewScreen) (Object) this);
    }
}
*///? }
