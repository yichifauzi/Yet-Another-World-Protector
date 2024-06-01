package de.z0rdak.yawp.handler.flags;

import de.z0rdak.yawp.YetAnotherWorldProtector;
import de.z0rdak.yawp.api.events.region.FlagCheckEvent;
import de.z0rdak.yawp.core.flag.FlagState;
import de.z0rdak.yawp.core.flag.RegionFlag;
import de.z0rdak.yawp.util.MessageSender;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static de.z0rdak.yawp.handler.flags.HandlerUtil.*;
import static net.neoforged.fml.common.EventBusSubscriber.Bus.GAME;

@EventBusSubscriber(modid = YetAnotherWorldProtector.MODID, bus = GAME)
public class GrievingFlagHandler {

    private GrievingFlagHandler() {
    }

    @SubscribeEvent
    public static void onFarmLandTrampled(BlockEvent.FarmlandTrampleEvent event) {
        if (isServerSide(event.getEntity())) {
            Entity trampler = event.getEntity();
            ResourceKey<Level> dim = getEntityDim(trampler);
            Player player = trampler instanceof Player ? (Player) trampler : null;
            FlagCheckEvent checkEvent = new FlagCheckEvent(event.getPos(), RegionFlag.TRAMPLE_FARMLAND, dim, player);
            if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                return;
            }
            FlagState flagState = processCheck(checkEvent, null, denyResult -> {
                event.setCanceled(true);
                MessageSender.sendFlagMsg(denyResult);
            });
            if (flagState == FlagState.DENIED)
                return;
            // cancel only player trampling
            if (trampler instanceof Player) {
                FlagCheckEvent playerTrampleFlagCheck = new FlagCheckEvent(event.getPos(), RegionFlag.TRAMPLE_FARMLAND_PLAYER, dim, player);
                if (NeoForge.EVENT_BUS.post(playerTrampleFlagCheck).isCanceled()) {
                    return;
                }
                processCheck(playerTrampleFlagCheck, null, denyResult -> {
                    event.setCanceled(true);
                    MessageSender.sendFlagMsg(denyResult);
                });
            } else {
                FlagCheckEvent entityTrampleFlagCheck = new FlagCheckEvent(event.getPos(), RegionFlag.TRAMPLE_FARMLAND_OTHER, dim, null);
                if (NeoForge.EVENT_BUS.post(entityTrampleFlagCheck).isCanceled()) {
                    return;
                }
                processCheck(entityTrampleFlagCheck, null, denyResult -> {
                    event.setCanceled(true);
                });
            }

        }
    }

    @SubscribeEvent
    public static void onEntityDestroyBlock(LivingDestroyBlockEvent event) {
        if (isServerSide(event)) {
            LivingEntity destroyer = event.getEntity();
            BlockPos target = event.getPos();
            FlagCheckEvent checkEvent = null;
            if (destroyer instanceof EnderDragon) {
                checkEvent = new FlagCheckEvent(target, RegionFlag.DRAGON_BLOCK_PROT, getEntityDim(destroyer), null);
                if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                    return;
                }
            }
            if (destroyer instanceof WitherBoss) {
                checkEvent = new FlagCheckEvent(target, RegionFlag.WITHER_BLOCK_PROT, getEntityDim(destroyer), null);
                if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                    return;
                }
            }
            if (destroyer instanceof Zombie) {
                checkEvent = new FlagCheckEvent(target, RegionFlag.ZOMBIE_DOOR_PROT, getEntityDim(destroyer), null);
                if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                    return;
                }
            }
            if (checkEvent != null) {
                processCheck(checkEvent, null, denyResult -> {
                    event.setCanceled(true);
                });
            }
        }
    }

    /**
     * Idea: Flag for player not dropping loot as member/owner? -> local keepInventory
     */
    @SubscribeEvent
    public static void onEntityDropLoot(LivingDropsEvent event) {
        if (isServerSide(event)) {
            LivingEntity lootEntity = event.getEntity();
            Player player = isPlayer(lootEntity) ? (Player) lootEntity : null;
            FlagCheckEvent checkEvent = new FlagCheckEvent(lootEntity.blockPosition(), RegionFlag.DROP_LOOT_ALL, event.getEntity().level().dimension(), player);
            if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                return;
            }
            FlagState flagState = processCheck(checkEvent, null, denyResult -> {
                event.setCanceled(true);
                MessageSender.sendFlagMsg(denyResult);
            });
            if (flagState == FlagState.DENIED)
                return;
            if (player != null) {
                FlagCheckEvent playerCheckEvent = new FlagCheckEvent(lootEntity.blockPosition(), RegionFlag.DROP_LOOT_PLAYER, player.level().dimension(), player);
                if (NeoForge.EVENT_BUS.post(playerCheckEvent).isCanceled()) {
                    return;
                }
                processCheck(playerCheckEvent, null, denyResult -> {
                    event.setCanceled(true);
                    MessageSender.sendFlagMsg(denyResult);
                });
            }
        }
    }

    @SubscribeEvent
    public static void onEntityXpDrop(LivingExperienceDropEvent event) {
        if (isServerSide(event)) {
            Player player = event.getAttackingPlayer();
            Entity xpDroppingEntity = event.getEntity();
            ResourceKey<Level> dim = getEntityDim(xpDroppingEntity);
            BlockPos pos = xpDroppingEntity.blockPosition();
            if (player != null) {
                // prevent all xp drop
                FlagCheckEvent checkEvent = new FlagCheckEvent(pos, RegionFlag.XP_DROP_ALL, dim, null);
                if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                    return;
                }
                FlagState flagState = processCheck(checkEvent, null, denyResult -> {
                    event.setCanceled(true);
                });
                if (flagState == FlagState.DENIED)
                    return;

                // prevent non-member/owner players from dropping xp by killing mobs
                checkEvent = new FlagCheckEvent(pos, RegionFlag.XP_DROP_PLAYER, dim, player);
                if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                    return;
                }
                flagState = processCheck(checkEvent, null, denyResult -> {
                    event.setCanceled(true);
                    MessageSender.sendFlagMsg(denyResult);
                });
                if (flagState == FlagState.DENIED)
                    return;

                // prevent monster xp drop
                if (isMonster(xpDroppingEntity)) {
                    checkEvent = new FlagCheckEvent(pos, RegionFlag.XP_DROP_MONSTER, dim, null);
                    if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                        return;
                    }
                    flagState = processCheck(checkEvent, null, denyResult -> {
                        event.setCanceled(true);
                        MessageSender.sendFlagMsg(denyResult);
                    });
                    if (flagState == FlagState.DENIED) {
                    }
                } else {
                    checkEvent = new FlagCheckEvent(pos, RegionFlag.XP_DROP_OTHER, dim, null);
                    if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                        return;
                    }
                    processCheck(checkEvent, null, denyResult -> {
                        event.setCanceled(true);
                        MessageSender.sendFlagMsg(denyResult);
                    });
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (!event.getEntity().level().isClientSide) {
            if (isServerSide(event.getEntity())) {
                FlagCheckEvent checkEvent = new FlagCheckEvent(event.getEntity().blockPosition(), RegionFlag.MOB_GRIEFING, getEntityDim(event.getEntity()), null);
                if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                    return;
                }
                FlagState flagState = processCheck(checkEvent, null, denyResult -> {
                    event.setCanGrief(false);
                });
                if (flagState == FlagState.DENIED)
                    return;
                if (event.getEntity() instanceof EnderMan) {
                    checkEvent = new FlagCheckEvent(event.getEntity().blockPosition(), RegionFlag.ENDERMAN_GRIEFING, getEntityDim(event.getEntity()), null);
                    if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                        return;
                    }
                    processCheck(checkEvent, null, denyResult -> {
                        event.setCanGrief(false);
                    });
                }
            }
        }
    }

    // idea: differentiate between player and other entities (armor stand/mobs)
    /*
    TODO: Disabled this to enable compatibility with PLACE_BLOCKS again because they use the same event
    @SubscribeEvent
    public static void onFreezeWaterWithBoots(BlockEvent.EntityPlaceEvent event) {
        if (!event.getWorld().isClientSide()) {
            if (event.getEntity() != null) {
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(event.getEntity().level.dimension());
                FlagCheckEvent flagCheckEvent = HandlerUtil.checkTargetEvent(event.getPos(), NO_WALKER_FREEZE, dimCache.getDimensionalRegion());
                if (flagCheckEvent.isDenied()) {
                    event.setCanceled(true);
                }
            }
        }
    }
    */

    /**
     * TODO: Inverted flags would need to re-add allowed blocks/entities to the event list
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!event.getLevel().isClientSide) {
            ResourceKey<Level> dim = event.getLevel().dimension();

            Set<BlockPos> protectedBlocks = event.getAffectedBlocks().stream()
                    .filter(explosionBlockPosFilterPredicate(dim, RegionFlag.EXPLOSION_BLOCK))
                    .collect(Collectors.toSet());
            Set<Entity> protectedEntities = event.getAffectedEntities().stream()
                    .filter(explosionEntityPosFilterPredicate(dim, RegionFlag.EXPLOSION_BLOCK))
                    .collect(Collectors.toSet());
            preventDestructionFor(event, protectedBlocks, protectedEntities);

            if (event.getExplosion().getIndirectSourceEntity() != null) {
                boolean explosionTriggeredByCreeper = (event.getExplosion().getIndirectSourceEntity() instanceof Creeper);
                if (explosionTriggeredByCreeper) {
                    protectedBlocks = event.getAffectedBlocks().stream()
                            .filter(explosionBlockPosFilterPredicate(dim, RegionFlag.EXPLOSION_CREEPER_BLOCK))
                            .collect(Collectors.toSet());
                    protectedEntities = event.getAffectedEntities().stream()
                            .filter(explosionEntityPosFilterPredicate(dim, RegionFlag.EXPLOSION_CREEPER_ENTITY))
                            .collect(Collectors.toSet());
                } else {
                    protectedBlocks = event.getAffectedBlocks().stream()
                            .filter(explosionBlockPosFilterPredicate(dim, RegionFlag.EXPLOSION_OTHER_BLOCKS))
                            .collect(Collectors.toSet());
                    protectedEntities = event.getAffectedEntities().stream()
                            .filter(explosionEntityPosFilterPredicate(dim, RegionFlag.EXPLOSION_OTHER_ENTITY))
                            .collect(Collectors.toSet());
                }
                preventDestructionFor(event, protectedBlocks, protectedEntities);
            }
        }
    }

    /**
     * Removes affected entities and/or blocks from the event list to protect them
     *
     * @param event             the explosion event
     * @param protectedBlocks   the blocks to protect
     * @param protectedEntities the entities to protect
     */
    private static void preventDestructionFor(ExplosionEvent.Detonate event, Set<BlockPos> protectedBlocks, Set<Entity> protectedEntities) {
        event.getAffectedBlocks().removeAll(protectedBlocks);
        event.getAffectedEntities().removeAll(protectedEntities);
    }

    private static Predicate<Entity> explosionEntityPosFilterPredicate(ResourceKey<Level> dim, RegionFlag flag) {
        return entity -> {
            // TODO: Introduce a subtype for FlagCheckEvent which holds multiple blocks? This way only one event is fired
            // TODO: Make the event cancellable and have a mutable blockpos list
            FlagCheckEvent checkEvent = new FlagCheckEvent(entity.blockPosition(), flag, dim, null);
            if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                return true;
            }
            // TODO: Same for check result, here we only need one result for all blocks
            FlagState flagState = processCheck(checkEvent, null, null);
            return flagState == FlagState.DENIED;
        };
    }

    private static Predicate<BlockPos> explosionBlockPosFilterPredicate(ResourceKey<Level> dim, RegionFlag flag) {
        return pos -> {
            // TODO: Introduce a subtype for FlagCheckEvent which holds multiple blocks? This way only one event is fired
            // TODO: Make the event cancellable and have a mutable blockpos list
            FlagCheckEvent checkEvent = new FlagCheckEvent(pos, flag, dim, null);
            if (NeoForge.EVENT_BUS.post(checkEvent).isCanceled()) {
                return true;
            }
            // TODO: Same for check result, here we only need one result for all blocks
            FlagState flagState = processCheck(checkEvent, null, null);
            return flagState == FlagState.DENIED;
        };
    }
}
