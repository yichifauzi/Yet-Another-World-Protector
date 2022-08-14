package de.z0rdak.regionshield.handler;

import de.z0rdak.regionshield.RegionShield;
import de.z0rdak.regionshield.managers.data.PlayerTrackingManager;
import de.z0rdak.regionshield.managers.data.RegionDataManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Eventhandler to track players near regions to handle entering and leaving regions without much overhead.
 */
@Mod.EventBusSubscriber(modid = RegionShield.MODID)
public class ServerPlayerEventHandler {

    private static MinecraftServer server;

    @SubscribeEvent
    public static void onServerStarted(FMLServerStartedEvent event){
        server = event.getServer();
    }

    // TODO: configurable to be adjustable for player count
    private static final int checkPlayerMovementInterval = 20;

    public static List<PlayerEntity> prevPlayerPos;
    public static List<PlayerEntity> currentPlayerPos;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event){
        // just pick start or end it really does not matter

        if (server.getPlayerCount() == 0) {
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            int scalingPlayerTickInterval = (checkPlayerMovementInterval * server.getPlayerCount());
            if (server.getTickCount() % scalingPlayerTickInterval == 0) {
                /*
                // 1. Get Players in Vincinity of REgions
                // -> List is updated by changing chunk/section events
                if (prevPlayerPos == null) {
                    prevPlayerPos = PlayerTrackingManager.getPlayersInRegionVicinity();
                    currentPlayerPos = new ArrayList<>(prevPlayerPos);
                } else {
                    // Check position on previous tick (cycle) against current position
                    // TODO: check entering & leaving
                    // TODO: tp player to fitting location
                    // TODO: show message for entering & leaving

                    prevPlayerPos = new ArrayList<>(currentPlayerPos);
                }

                 */
            }
        }
    }
}
