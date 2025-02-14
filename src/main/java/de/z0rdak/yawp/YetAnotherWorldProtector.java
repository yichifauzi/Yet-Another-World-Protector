package de.z0rdak.yawp;

import de.z0rdak.yawp.config.ConfigRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(YetAnotherWorldProtector.MODID)
public class YetAnotherWorldProtector {
    public static final String MODID = "yawp";
    public static final Logger LOGGER = LogManager.getLogger(MODID.toUpperCase());

    public YetAnotherWorldProtector() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onInit);
        //Make sure the mod being absent on the other network side does not cause the client to display the server as incompatible
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (version, network) -> true));
    }

    @SubscribeEvent
    public void onInit(FMLCommonSetupEvent event) {
        ConfigRegistry.register();
    }
}
