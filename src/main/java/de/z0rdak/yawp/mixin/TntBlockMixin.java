package de.z0rdak.yawp.mixin;

import de.z0rdak.yawp.api.events.region.FlagCheckEvent;
import net.minecraft.block.BlockState;
import net.minecraft.block.TNTBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static de.z0rdak.yawp.api.events.region.RegionEvents.post;
import static de.z0rdak.yawp.core.flag.RegionFlag.IGNITE_EXPLOSIVES;
import static de.z0rdak.yawp.handler.flags.HandlerUtil.isServerSide;
import static de.z0rdak.yawp.handler.flags.HandlerUtil.processCheck;
import static de.z0rdak.yawp.util.MessageSender.sendFlagMsg;

@Mixin(TNTBlock.class)
public class TntBlockMixin {

    @Inject(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/TNTBlock;catchFire(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/Direction;Lnet/minecraft/entity/LivingEntity;)V"), cancellable = true, allow = 1)
    public void onUseFlintAndSteel(BlockState state, World world, BlockPos pos, PlayerEntity player2, Hand hand, BlockRayTraceResult hit, CallbackInfoReturnable<ActionResultType> cir) {
        if (isServerSide(world)) {
            FlagCheckEvent checkEvent = new FlagCheckEvent(pos, IGNITE_EXPLOSIVES, world.dimension(), player2);
            if (post(checkEvent)) {
                return;
            }
            processCheck(checkEvent, denyResult -> {
                cir.setReturnValue(ActionResultType.CONSUME);
                sendFlagMsg(denyResult);
            });
        }
    }
}
