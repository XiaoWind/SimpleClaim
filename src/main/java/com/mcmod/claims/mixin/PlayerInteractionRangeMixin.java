package com.mcmod.claims.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 手持金斧（选区工具）时跳过方块交互距离校验，从而支持 tweakeroo freecamera 等
 * 远距离选点场景。真正的破坏/使用仍由 ProtectionHandler 的事件层拦截，因此不会造成越权。
 */
@Mixin(Player.class)
public abstract class PlayerInteractionRangeMixin {

    @Inject(method = "isWithinBlockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void claims$allowSelectionReach(BlockPos pos, double margin, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.getItemInHand(InteractionHand.MAIN_HAND).is(Items.GOLDEN_AXE)) {
            cir.setReturnValue(true);
        }
    }
}
