package de.z0rdak.yawp.handler.stick;

import de.z0rdak.yawp.YetAnotherWorldProtector;
import de.z0rdak.yawp.util.StickType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;

import static de.z0rdak.yawp.util.StickUtil.*;
import static net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE;

@Mod.EventBusSubscriber(modid = YetAnotherWorldProtector.MODID, bus = FORGE)
public class StickInteractionHandler {

    private StickInteractionHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getWorld().isClientSide) {
            ItemStack involvedItemStack = event.getItemStack();
            if (!involvedItemStack.equals(ItemStack.EMPTY) && isVanillaStick(involvedItemStack)) {
                StickType stickType = getStickType(involvedItemStack);
                if (Objects.requireNonNull(stickType) == StickType.MARKER) {
                    MarkerStickHandler.onMarkBlock(involvedItemStack, event);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCycleMode(PlayerInteractEvent.RightClickItem event) {
        if (!event.getWorld().isClientSide) {
            ItemStack involvedItemStack = event.getItemStack();
            // is some valid mod stick
            if (!involvedItemStack.equals(ItemStack.EMPTY)
                    && hasNonNullTag(involvedItemStack)
                    && involvedItemStack.getTag().contains(STICK)) {
                RayTraceResult blockLookingAt = event.getPlayer().pick(20.0d, 0.0f, false);
                boolean targetIsAir;
                if (blockLookingAt.getType() == RayTraceResult.Type.BLOCK) {
                    BlockPos blockpos = ((BlockRayTraceResult) blockLookingAt).getBlockPos();
                    BlockState blockstate = event.getWorld().getBlockState(blockpos);
                    targetIsAir = blockstate.getBlock().equals(Blocks.AIR);
                } else {
                    targetIsAir = blockLookingAt.getType() == RayTraceResult.Type.MISS;
                }
                if (event.getPlayer().isShiftKeyDown() && targetIsAir) {
                    StickType stickType = getStickType(involvedItemStack);
                    if (Objects.requireNonNull(stickType) == StickType.MARKER) {
                        // Note: cycling mode is disabled for now because there is only one working area type
                        //MarkerStickHandler.onCycleRegionMarker(involvedItemStack);
                    }
                }
            }
        }
    }

    /**
     * Handles action when renaming mod sticks in an anvil.
     * This is used to create a mod stick or to define a region by renaming a valid RegionMarker stick.
     */
    public static void onStickRename(AnvilRepairEvent event) {
        PlayerEntity player = event.getPlayer();
        if (!player.getCommandSenderWorld().isClientSide) {
            ItemStack outputItem = event.getItemResult();
            ItemStack inputItem = event.getItemInput();
            ItemStack ingredientInput = event.getIngredientInput();
            boolean isInputAndOutputStick = ItemStack.isSame(outputItem, Items.STICK.getDefaultInstance())
                    && ItemStack.isSame(inputItem, Items.STICK.getDefaultInstance());
            if (isInputAndOutputStick && ingredientInput.isEmpty()) {
                onCreateStick(event);
            }
        }
    }

    /**
     * Edits the NBT data of the renamed stick to "transform" it to the corresponding mod stick.
     * @param event the event data from renaming the stick item
     */
    private static void onCreateStick(AnvilRepairEvent event) {
        PlayerEntity player = event.getPlayer();
        ItemStack outputItem = event.getItemResult();
        ItemStack inputItem = event.getItemInput();
        StickType type = StickType.of(outputItem.getHoverName().getString());
        if (type != StickType.UNKNOWN) {
            // split stack and only create one stick, also refund xp
            inputItem.setCount(outputItem.getCount() - 1);
            player.addItem(inputItem);
            event.setBreakChance(0.0f);
            player.giveExperienceLevels(1);
            initMarkerNbt(outputItem, type, event.getPlayer().getCommandSenderWorld().dimension());
            player.inventory.setChanged();
        }
    }
}
