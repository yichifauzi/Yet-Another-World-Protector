package de.z0rdak.yawp.handler.flags;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.z0rdak.yawp.YetAnotherWorldProtector;
import de.z0rdak.yawp.config.server.FlagConfig;
import de.z0rdak.yawp.managers.data.region.DimensionRegionCache;
import de.z0rdak.yawp.managers.data.region.RegionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

import static de.z0rdak.yawp.core.flag.RegionFlag.*;
import static de.z0rdak.yawp.handler.flags.HandlerUtil.*;
import static net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE;

/**
 * Contains flag handler for events directly related/cause to/by players.
 */
@Mod.EventBusSubscriber(modid = YetAnotherWorldProtector.MODID, value = Dist.DEDICATED_SERVER, bus = FORGE)
public final class PlayerFlagHandler {

    private PlayerFlagHandler() {
    }

    @SubscribeEvent
    public static void onElytraFlying(TickEvent.PlayerTickEvent event) {
        if (isServerSide(event.player) && event.phase == TickEvent.Phase.END) {
            ResourceKey<Level> entityDim = getEntityDim(event.player);
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(entityDim);
            if (dimCache != null) {
                // FIXME FIXME: This check first
                if (event.player.isFallFlying()) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(event.player, event.player.blockPosition(), NO_FLIGHT, dimCache.getDimensionalRegion());
                    if (flagCheckEvent.isDenied()) {
                        sendFlagDeniedMsg(flagCheckEvent);
                        event.player.stopFallFlying();
                    }
                }
            }
        }
    }

    /**
     * Prevents traditional attacks from players which use EntityPlayer.attackTargetEntityWithCurrentItem(Entity).
     */
    @SubscribeEvent
    public static void onAttackPlayer(AttackEntityEvent event) {
        if (isServerSide(event)) {
            if (event.getTarget() instanceof Player target) {
                Player attacker = event.getEntity();
                ResourceKey<Level> entityDim = getEntityDim(attacker);
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(entityDim);
                if (dimCache != null) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(attacker, target.blockPosition(), MELEE_PLAYERS, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
            }
        }
    }

    /**
     * Prevents various entities from been attacked from a player. <br>
     * TODO: Flag for all entities
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isServerSide(event)) {
            Player player = event.getEntity();
            Entity eventEntity = event.getTarget();
            ResourceKey<Level> entityDim = getEntityDim(event.getEntity());
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(entityDim);
            if (dimCache != null) {
                if (isAnimal(eventEntity)) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, eventEntity.blockPosition(), MELEE_ANIMALS, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                    return;
                }
                if (isMonster(eventEntity)) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, eventEntity.blockPosition(), MELEE_MONSTERS, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                    return;
                }
                if (event.getTarget() instanceof Villager) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, eventEntity.blockPosition(), MELEE_VILLAGERS, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                    return;
                }
                if (event.getTarget() instanceof WanderingTrader) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, eventEntity.blockPosition(), MELEE_WANDERING_TRADER, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
            }
        }
    }

    // unrelated: mobs pickup logic => MobEntity#livingTick
    @SubscribeEvent
    public static void onPickupItem(EntityItemPickupEvent event) {
        if (isServerSide(event)) {
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null) {
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(event.getEntity(), event.getEntity().blockPosition(), ITEM_PICKUP, dimCache.getDimensionalRegion());
                handleAndSendMsg(event, flagCheckEvent);
            }
        }
    }

    @SubscribeEvent
    public static void onBreedingAttempt(BabyEntitySpawnEvent event) {
        Player player = event.getCausedByPlayer();
        if (player != null) {
            if (!player.getCommandSenderWorld().isClientSide) {
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(player));
                if (dimCache != null) {
                    if (event.getParentA() instanceof Villager) {
                        // TODO: Test on Villagers and add extra flag
                    }
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(event.getCausedByPlayer(), event.getParentB().blockPosition(), ANIMAL_BREEDING, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
            }
        } else {
            // TODO: test if this is fired when animals are bred without player interaction (with mods?)
        }
    }

    /**
     * Note: maybe add flag for different tamable animals / non vanilla / etc
     */
    @SubscribeEvent
    public static void onAnimalTameAttempt(AnimalTameEvent event) {
        Player player = event.getTamer();
        if (player != null) {
            if (!player.getCommandSenderWorld().isClientSide) {
                Animal animal = event.getAnimal();
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(player));
                if (dimCache != null) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getAnimal().blockPosition(), ANIMAL_TAMING, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLevelChange(PlayerXpEvent.LevelChange event) {
        if (isServerSide(event)) {
            Player player = event.getEntity();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null) {
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getEntity().blockPosition(), LEVEL_FREEZE, dimCache.getDimensionalRegion());
                handleAndSendMsg(event, flagCheckEvent);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerXPChange(PlayerXpEvent.XpChange event) {
        if (isServerSide(event.getEntity())) {
            Player player = event.getEntity();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null) {
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getEntity().blockPosition(), XP_FREEZE, dimCache.getDimensionalRegion());
                handleAndSendMsg(event, flagCheckEvent);
                if (flagCheckEvent.isDenied()) {
                    // TODO: Test whether this is needed?
                    event.setAmount(0);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerXpPickup(PlayerXpEvent.PickupXp event) {
        if (isServerSide(event.getEntity())) {
            Player player = event.getEntity();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null) {
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getEntity().blockPosition(), XP_PICKUP, dimCache.getDimensionalRegion());
                handleAndSendMsg(event, flagCheckEvent);
                if (flagCheckEvent.isDenied()) {
                    event.getOrb().remove(Entity.RemovalReason.DISCARDED);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPvpAction(LivingHurtEvent event) {
        if (isServerSide(event)) {
            Entity dmgSourceEntity = event.getSource().getDirectEntity();
            Entity hurtEntity = event.getEntity();
            if (hurtEntity instanceof Player && dmgSourceEntity instanceof Player) {
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(dmgSourceEntity));
                if (dimCache != null) {
                    Player playerTarget = (Player) hurtEntity;
                    Player playerSource = (Player) dmgSourceEntity;
                    FlagCheckEvent flagCheckEvent = checkTargetEvent(playerTarget.blockPosition(), NO_PVP, dimCache.getDimensionalRegion());
                    if (flagCheckEvent.isDenied()) {
                        sendFlagDeniedMsg(flagCheckEvent, playerSource);
                        event.setAmount(0f);
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    @TargetFocusedFlag(flag = INVINCIBLE)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (isServerSide(event)) {
            Entity hurtEntity = event.getEntity();
            if (hurtEntity instanceof Player) {
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(hurtEntity));
                if (dimCache != null) {
                    Player playerTarget = (Player) hurtEntity;
                    FlagCheckEvent hurtPlayerFlagCheck = checkPlayerEvent(playerTarget, playerTarget.blockPosition(), INVINCIBLE, dimCache.getDimensionalRegion());
                    if (!hurtPlayerFlagCheck.isDenied()) {
                        event.setAmount(0f);
                        event.setCanceled(true);
                    }
                }
            }
        }
    }


    /* TODO: Is this test even necessary anymore?
     *   - There is already a PVP flag for onHurt in place
     * */
    @SubscribeEvent
    public static void onReceiveDmg(LivingDamageEvent event) {
        if (isServerSide(event)) {
            Entity dmgSourceEntity = event.getSource().getDirectEntity();
            if (dmgSourceEntity instanceof Player) {
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(dmgSourceEntity));
                if (dimCache != null) {
                    if (event.getEntity() instanceof Player dmgTarget) {
                        Player dmgSource = ((Player) dmgSourceEntity);
                        // another check for PVP - this does not prevent knock-back? but prevents dmg
                        FlagCheckEvent flagCheckEvent = checkTargetEvent(dmgTarget.blockPosition(), MELEE_PLAYERS, dimCache.getDimensionalRegion());
                        if (flagCheckEvent.isDenied()) {
                            sendFlagDeniedMsg(flagCheckEvent, dmgSource);
                            event.setAmount(0f);
                            event.setCanceled(true);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    @TargetFocusedFlag(flag = KNOCKBACK_PLAYERS)
    public static void onPlayerKnockback(LivingKnockBackEvent event) {
        if (isServerSide(event)) {
            if (event.getEntity() instanceof Player) {
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
                if (dimCache != null) {
                    Player dmgTarget = (Player) event.getEntity();
                    FlagCheckEvent flagCheckEvent = checkTargetEvent(dmgTarget.blockPosition(), KNOCKBACK_PLAYERS, dimCache.getDimensionalRegion());
                    if (flagCheckEvent.isDenied()) {
                        event.setCanceled(true);
                        event.setStrength(0);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerBreakBlock(BlockEvent.BreakEvent event) {
        if (isServerSide(event)) {
            Player player = event.getPlayer();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getPlayer()));
            if (dimCache != null) {
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getPos(), BREAK_BLOCKS, dimCache.getDimensionalRegion());
                handleAndSendMsg(event, flagCheckEvent);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (isServerSide(event)) {
            if (event.getEntity() != null && event.getEntity() instanceof Player player) {
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(player));
                if (dimCache != null) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getPos(), PLACE_BLOCKS, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
            }
        }
    }

    /**
     * TODO: Compound Flags for combining common flags? E.g. BREAK_BLOCKS && BREAK_ENTITIES
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityBreak(AttackEntityEvent event) {
        if (isServerSide(event)) {
            Entity target = event.getTarget();
            Player player = event.getEntity();
            ResourceKey<Level> entityDim = getEntityDim(event.getEntity());
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(entityDim);
            if (dimCache != null) {
                List<? extends String> entities = FlagConfig.BREAK_FLAG_ENTITIES.get();
                boolean isBlockEntityCovered = entities.stream()
                        .anyMatch(entity -> EntityType.getKey(target.getType()).equals(new ResourceLocation(entity)));
                if (isBlockEntityCovered) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getTarget().blockPosition(), BREAK_ENTITIES, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionStarted(ExplosionEvent.Start event) {
        if (!event.getLevel().isClientSide) {
            Explosion explosion = event.getExplosion();
            if (explosion.getSourceMob() instanceof Player player) {
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(player));
                if (dimCache != null) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, new BlockPos(explosion.getPosition()), IGNITE_EXPLOSIVES, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
            } else {
                if (explosion.getSourceMob() == null) {
                    // ignited by e.g. dispenser
                    // TODO: Griefing/dedicated dispenser flag
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBonemealUse(BonemealEvent event) {
        if (isServerSide(event)) {
            Player player = event.getEntity();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(event.getLevel().dimension());
            if (dimCache != null) {
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getPos(), USE_BONEMEAL, dimCache.getDimensionalRegion());
                handleAndSendMsg(event, flagCheckEvent);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerUseEnderPearl(EntityTeleportEvent event) {
        if (isServerSide(event)) {
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null) {
                // handle player teleportation using ender pearls
                if (event instanceof EntityTeleportEvent.EnderPearl enderPearlEvent) {
                    Player player = enderPearlEvent.getPlayer();

                    FlagCheckEvent.PlayerFlagEvent enderPearlToRegionFlagCheck = checkPlayerEvent(player, new BlockPos(event.getTarget()), USE_ENDERPEARL_TO_REGION, dimCache.getDimensionalRegion());
                    if (handleAndSendMsg(event, enderPearlToRegionFlagCheck)) {
                        return;
                    }

                    FlagCheckEvent.PlayerFlagEvent enderPearlFromRegionFlagCheck = checkPlayerEvent(player, player.blockPosition(), USE_ENDERPEARL_FROM_REGION, dimCache.getDimensionalRegion());
                    if (handleAndSendMsg(event, enderPearlFromRegionFlagCheck)) {
                    }

                    /* FIXME: refund pearl - duplication bug with e.g. origins mod
                    int count = player.getHeldItem(player.getActiveHand()).getCount();
                    player.getHeldItem(player.getActiveHand()).setCount(count + 1);
                    return;
                    */
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerUseToolSecondary(BlockEvent.BlockToolModificationEvent event) {
        if (!event.getLevel().isClientSide()) {
            Player player = event.getPlayer();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getPlayer()));
            if (dimCache != null) {
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getPos(), TOOL_SECONDARY_USE, dimCache.getDimensionalRegion());
                if (handleAndSendMsg(event, flagCheckEvent)) {
                    // FIXME: [next update]: how about all TOOL_SECONDARY_USE is denied but one of the following is allowed?
                    // this kind of check is not uncommon. See onPlayerRightClickBlock e.g.
                    return;
                }
                // TODO: Events for ToolActions
                if (event.getToolAction().equals(ToolActions.AXE_STRIP)) {
                    flagCheckEvent = checkPlayerEvent(player, event.getPos(), AXE_STRIP, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
                if (event.getToolAction().equals(ToolActions.HOE_TILL)) {
                    flagCheckEvent = checkPlayerEvent(player, event.getPos(), HOE_TILL, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
                if (event.getToolAction().equals(ToolActions.SHOVEL_FLATTEN)) {
                    flagCheckEvent = checkPlayerEvent(player, event.getPos(), SHOVEL_PATH, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isServerSide(event)) {
            Player player = event.getEntity();
            BlockEntity targetEntity = event.getLevel().getBlockEntity(event.getPos());
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null) {
                boolean isLockableTileEntity = targetEntity instanceof BaseContainerBlockEntity;
                boolean isEnderChest = targetEntity instanceof EnderChestBlockEntity;
                boolean isContainer = targetEntity instanceof LecternBlockEntity || isLockableTileEntity;

                // used to allow player to place blocks when shift clicking container or usable bock
                boolean hasEmptyHands = hasEmptyHands(player);

                BlockHitResult pos = event.getHitVec();
                if (pos != null && pos.getType() == HitResult.Type.BLOCK) {
                    if (player.isShiftKeyDown() && hasEmptyHands || !player.isShiftKeyDown()) {
                        FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getPos(), USE_BLOCKS, dimCache.getDimensionalRegion());
                        handleAndSendMsg(event, flagCheckEvent);
                    }
                }

                if (!hasEmptyHands) {
                    FlagCheckEvent.PlayerFlagEvent useItemCheck = checkPlayerEvent(player, event.getPos(), USE_ITEMS, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, useItemCheck);
                }

                // Note: following flags are already covered with use_blocks
                // check for ender chest access
                if (isEnderChest) {
                    if (player.isShiftKeyDown() && hasEmptyHands || !player.isShiftKeyDown()) {
                        FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, targetEntity.getBlockPos(), ENDER_CHEST_ACCESS, dimCache.getDimensionalRegion());
                        handleAndSendMsg(event, flagCheckEvent);
                    }
                }
                // check for container access
                if (isContainer) {
                    if (player.isShiftKeyDown() && hasEmptyHands || !player.isShiftKeyDown()) {
                        FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, targetEntity.getBlockPos(), CONTAINER_ACCESS, dimCache.getDimensionalRegion());
                        handleAndSendMsg(event, flagCheckEvent);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onAccessMinecartChest(PlayerInteractEvent.EntityInteract event) {
        if (isServerSide(event)) {
            Player player = event.getEntity();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null) {
                boolean isMinecartContainer = event.getTarget() instanceof AbstractMinecartContainer;
                if (isMinecartContainer) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getTarget().blockPosition(), CONTAINER_ACCESS, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteraction(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isServerSide(event)) {
            Player player = event.getEntity();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null) {
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getTarget().blockPosition(), USE_ENTITIES, dimCache.getDimensionalRegion());
                handleAndSendMsg(event, flagCheckEvent);

                if (!hasEmptyHands(player)) {
                    FlagCheckEvent.PlayerFlagEvent useItemCheck = checkPlayerEvent(player, event.getPos(), USE_ITEMS, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, useItemCheck);
                }
            }
        }
    }

    private static boolean hasEmptyHands(Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem().equals(Items.AIR)
                && player.getItemInHand(InteractionHand.OFF_HAND).getItem().equals(Items.AIR);
    }

    @SubscribeEvent
    public static void onEntityInteraction(PlayerInteractEvent.EntityInteract event) {
        if (isServerSide(event)) {
            Player player = event.getEntity();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null) {
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getTarget().blockPosition(), USE_ENTITIES, dimCache.getDimensionalRegion());
                handleAndSendMsg(event, flagCheckEvent);

                if (!hasEmptyHands(player)) {
                    FlagCheckEvent.PlayerFlagEvent useItemCheck = checkPlayerEvent(player, event.getPos(), USE_ENTITIES, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, useItemCheck);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteraction(PlayerInteractEvent.RightClickItem event) {
        if (isServerSide(event)) {
            Player player = event.getEntity();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null) {

                if (!hasEmptyHands(player)) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getPos(), USE_ENTITIES, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }

                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getPos(), USE_ITEMS, dimCache.getDimensionalRegion());
                handleAndSendMsg(event, flagCheckEvent);
            }
        }
    }

    /**
     * TODO: This is difficult to test. Do it.
     *
     * @param event
     */
    @SubscribeEvent
    public static void onSteppedOnActivator(BlockEvent.NeighborNotifyEvent event) {
        if (isServerSide(event)) {
            Level world = (Level) event.getLevel();
            Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
            BlockPos pos = event.getPos();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(world.dimension());
            if (dimCache != null) {
                if (block instanceof BasePressurePlateBlock) {
                    AABB areaAbovePressurePlate = new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1, pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
                    List<Player> players = event.getLevel().getEntities(EntityType.PLAYER, areaAbovePressurePlate, (player) -> true);
                    boolean isCanceledForOne = false;
                    for (Player player : players) {
                        FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getPos(), USE_BLOCKS, dimCache.getDimensionalRegion());
                        isCanceledForOne = isCanceledForOne || handleAndSendMsg(event, flagCheckEvent);
                        event.setCanceled(isCanceledForOne);
                    }

                }
            }
        }
    }

    /**
     * Note: Does not prevent from fluids generate additional blocks (cobble generator). Use BlockEvent.FluidPlaceBlockEvent for this
     */
    @SubscribeEvent
    public static void onBucketFill(FillBucketEvent event) {
        // Note: FilledBucket seems to always be null. use maxStackSize to determine bucket state (empty or filled)
        if (isServerSide(event)) {
            Player player = event.getEntity();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null && event.getTarget() != null) {
                HitResult pos = event.getTarget();
                BlockPos targetPos = new BlockPos(event.getTarget().getLocation());
                // MaxStackSize: 1 -> full bucket so only placeable; >1 -> empty bucket, only fillable
                int bucketItemMaxStackCount = event.getEmptyBucket().getMaxStackSize();
                // placing fluid
                if (bucketItemMaxStackCount == 1) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, targetPos, PLACE_FLUIDS, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
                // scooping fluid (breaking fluid)
                if (bucketItemMaxStackCount > 1) {
                    boolean isWaterlogged = false;
                    boolean isFluid = false;
                    if (pos != null && pos.getType() == HitResult.Type.BLOCK) {
                        BlockState blockState = event.getLevel().getBlockState(targetPos);
                        // check for waterlogged block
                        if (blockState.getBlock() instanceof SimpleWaterloggedBlock) {
                            isWaterlogged = blockState.getValue(BlockStateProperties.WATERLOGGED);
                        }
                        if (ForgeRegistries.FLUIDS.tags() != null) {
                            isFluid = ForgeRegistries.FLUIDS.tags().getTagNames().anyMatch(tag -> blockState.getFluidState().is(tag));
                        }
                        if (isWaterlogged || isFluid) {
                            FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, targetPos, SCOOP_FLUIDS, dimCache.getDimensionalRegion());
                            handleAndSendMsg(event, flagCheckEvent);
                        }
                    }
                }
            }
        }
    }

    /**
     * TODO: Flag for team chat
     * Note: message received from server but not distributed to all clients
     */
    @SubscribeEvent
    public static void onSendChat(ServerChatEvent event) {
        if (event.getPlayer() != null) {
            ServerPlayer player = event.getPlayer();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getPlayer()));
            if (dimCache != null) {
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, player.blockPosition(), SEND_MESSAGE, dimCache.getDimensionalRegion());
                handleAndSendMsg(event, flagCheckEvent);
            }
        }
    }

    /**
     * TODO: add command list to block only specific commands, regardless of mod and permission of command
     */
    @SubscribeEvent
    public static void onCommandSend(CommandEvent event) {
        try {
            Player player = event.getParseResults().getContext().getSource().getPlayerOrException();
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(player));
            if (dimCache != null) {
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, player.blockPosition(), EXECUTE_COMMAND, dimCache.getDimensionalRegion());
                handleAndSendMsg(event, flagCheckEvent);
            }
        } catch (CommandSyntaxException e) {
            // Most likely thrown because command was not send by a player.
            // This is fine because we don't want this flag to be triggered from non-players entities
        }
    }

    // TODO: Flag to allow sleeping at daytime, by using event.setResult(Event.Result.ALLOW);
    @SubscribeEvent
    public static void onPlayerAttemptSleep(SleepingTimeCheckEvent event) {
        if (isServerSide(event)) {
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntity()));
            if (dimCache != null) {
                Player player = event.getEntity();
                event.getSleepingLocation().ifPresent((pos) -> {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, pos, SLEEP, dimCache.getDimensionalRegion());
                    // FIXME: Msg is default from sleep deny
                    if (sendFlagDeniedMsg(flagCheckEvent)) {
                        event.setResult(Event.Result.DENY);
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (isServerSide(event)) {
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(event.getSpawnLevel());
            if (dimCache != null) {
                BlockPos newSpawn = event.getNewSpawn();
                Player player = event.getEntity();
                if (newSpawn != null) {
                    FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, newSpawn, SET_SPAWN, dimCache.getDimensionalRegion());
                    handleAndSendMsg(event, flagCheckEvent);
                }
            }
        }
    }

    /**
     * TODO: Check for duplication
     */
    @SubscribeEvent
    public static void onPlayerDropItem(ItemTossEvent event) {
        if (!event.getPlayer().getCommandSenderWorld().isClientSide) {
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getPlayer()));
            if (dimCache != null) {
                Player player = event.getPlayer();
                FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, event.getEntity().blockPosition(), ITEM_DROP, dimCache.getDimensionalRegion());
                if (handleAndSendMsg(event, flagCheckEvent)) {
                    // FIXME: Does not proper refund items?
                    player.addItem(event.getEntity().getItem());
                }
            }
        }
    }

    /**
     * Idea: Flags for different animals to mount
     */
    @SubscribeEvent
    public static void onEntityMountAttempt(EntityMountEvent event) {
        if (isServerSide(event)) {
            Entity entityBeingMounted = event.getEntityBeingMounted();
            // TODO: could be mob that dismounts because entity being mounted dies?
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(getEntityDim(event.getEntityMounting()));
            if (dimCache != null) {
                if (event.getEntityMounting() instanceof Player player) {
                    if (event.isMounting()) {
                        FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, entityBeingMounted.blockPosition(), ANIMAL_MOUNTING, dimCache.getDimensionalRegion());
                        handleAndSendMsg(event, flagCheckEvent);
                    }
                    if (event.isDismounting()) {
                        FlagCheckEvent.PlayerFlagEvent flagCheckEvent = checkPlayerEvent(player, entityBeingMounted.blockPosition(), ANIMAL_UNMOUNTING, dimCache.getDimensionalRegion());
                        handleAndSendMsg(event, flagCheckEvent);
                    }
                }
            }
        }
    }
}