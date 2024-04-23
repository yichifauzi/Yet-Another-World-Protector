package de.z0rdak.yawp.managers.data.region;

import de.z0rdak.yawp.YetAnotherWorldProtector;
import de.z0rdak.yawp.commands.CommandConstants;
import de.z0rdak.yawp.commands.arguments.region.RegionArgumentType;
import de.z0rdak.yawp.config.server.RegionConfig;
import de.z0rdak.yawp.core.flag.BooleanFlag;
import de.z0rdak.yawp.core.flag.RegionFlag;
import de.z0rdak.yawp.core.region.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.commons.lang3.NotImplementedException;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

import static de.z0rdak.yawp.commands.CommandConstants.values;
import static de.z0rdak.yawp.util.constants.RegionNBT.*;

/**
 * Class which manages the region data for the mod. It is responsible for loading and saving the region data to disk and
 * provides methods to access the region data.
 */
@EventBusSubscriber(modid = YetAnotherWorldProtector.MODID)
public class RegionDataManager extends WorldSavedData {

    /**
     * Name which is used for the file to store the NBT data: yawp-dimensions.dat
     */
    private static final String DATA_NAME = YetAnotherWorldProtector.MODID + "-dimensions";
    /**
     * Map which holds the mod region information. Each dimension has its on DimensionRegionCache.
     */
    private final static Map<RegistryKey<World>, DimensionRegionCache> dimCacheMap = new HashMap<>();
    /**
     * The global region of this mod which all sets common rules/flags for all regions.
     */
    private static GlobalRegion globalRegion = new GlobalRegion();
    /**
     * Singleton used to access methods to manage region data.
     */

    private static RegionDataManager regionDataCache = new RegionDataManager();

    private static final Set<String> dimensionDataNames = new HashSet<>();

    private RegionDataManager() {
        super(DATA_NAME);
    }

    public static void save() {
        YetAnotherWorldProtector.LOGGER.debug(new TranslationTextComponent("Save for RegionDataManager called. Attempting to save region data...").getString());
        RegionDataManager.get().setDirty();
    }

    /**
     * Returns the name of all dimension tracked by the region data manager.
     *
     * @return the set of dimension names which are tracked by the region data manager.
     */
    @SuppressWarnings("unused")
    public static Set<String> getDimensionDataNames() {
        return Collections.unmodifiableSet(dimensionDataNames);
    }

    public static List<DimensionRegionCache> getDimensionCaches() {
        return Collections.unmodifiableList(new ArrayList<>(dimCacheMap.values()));
    }

    public static RegionDataManager get() {
        if (regionDataCache == null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerWorld overworld = server.overworld();
                if (!overworld.isClientSide) {
                    DimensionSavedDataManager storage = overworld.getDataStorage();
                    regionDataCache = storage.computeIfAbsent(RegionDataManager::new, DATA_NAME);
                }
            }
        }
        return regionDataCache;
    }

    /**
     * Server startup hook for loading the region data from the yawp-dimension.dat file by creating an instance of RegionDataManager.
     *
     * @param event which is fired upon server start and acts as trigger to load region data from disk.
     */
    @SubscribeEvent
    public static void loadRegionData(FMLServerStartingEvent event) {
        try {
            ServerWorld world = Objects.requireNonNull(event.getServer().overworld());
            if (!world.isClientSide) {
                DimensionSavedDataManager storage = world.getDataStorage();
                RegionDataManager data = storage.computeIfAbsent(RegionDataManager::new, DATA_NAME);
                storage.set(data);
                regionDataCache = data;
                // YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("data.nbt.dimensions.load.success", data.getTotalRegionAmount(), data.getDimensionAmount()).getString());
                YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("Loaded " + data.getTotalRegionAmount() + " region(s) for " + data.getDimensionAmount()).getString() + " dimension(s)");
            }
        } catch (NullPointerException npe) {
            YetAnotherWorldProtector.LOGGER.error(new TranslationTextComponent("Loading regions failed!").getString());
            YetAnotherWorldProtector.LOGGER.error(new TranslationTextComponent(npe.getLocalizedMessage()).getString());
            // .LOGGER.error(new TranslationTextComponent("data.nbt.dimensions.load.failure").getString());
        }
    }

    /**
     * Method which gets called when a new RegionDataManager instance is created by loadRegionData.
     *
     * @param nbt compound region data read from disk to be deserialized for the region cache.
     */
    @Override
    public void load(CompoundNBT nbt) {
        CompoundNBT globalNbt = nbt.getCompound(GLOBAL);
        if (globalNbt.isEmpty()) {
            YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("Missing global region data. Initializing new data. (Ignore this for the first server start)").getString());
            globalRegion = new GlobalRegion();
        } else {
            globalRegion = new GlobalRegion(globalNbt);
            YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("Loaded global region data").getString());
        }

        dimCacheMap.clear();
        CompoundNBT dimensionRegions = nbt.getCompound(DIMENSIONS);
        YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("Loading region(s) for " + dimensionRegions.getAllKeys().size() + " dimension(s)").getString());
        // YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("data.nbt.dimensions.load.amount", dimensionRegions.getAllKeys().size()).getString());
        // deserialize all region without parent and child references
        for (String dimKey : dimensionRegions.getAllKeys()) {
            dimensionDataNames.add(dimKey);
            RegistryKey<World> dimension = RegistryKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(dimKey));
            if (dimensionRegions.contains(dimKey, Constants.NBT.TAG_COMPOUND)) {
                CompoundNBT dimCacheNbt = dimensionRegions.getCompound(dimKey);
                if (dimCacheNbt.contains(REGIONS, Constants.NBT.TAG_COMPOUND)) {
                    // YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("data.nbt.dimensions.load.dim.amount", dimCacheNbt.getCompound(REGIONS).size(), dimKey).getString());
                    YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("Loading " + dimCacheNbt.getCompound(REGIONS).size() + " region(s) for dimension '" + dimKey + "'").getString());
                } else {
                    // YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("data.nbt.dimensions.load.dim.empty", dimKey).getString());
                    YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("No region data for dimension '" + dimKey + "' found").getString());
                }
                dimCacheMap.put(dimension, new DimensionRegionCache(dimCacheNbt));
            }
        }
        dimCacheMap.forEach((dimKey, cache) -> YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("data.nbt.dimensions.loaded.dim.amount", cache.getRegions().size(), dimKey.location().toString()).getString()));

        // set parent and child references
        for (String dimKey : dimensionRegions.getAllKeys()) {
            RegistryKey<World> dimension = RegistryKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(dimKey));
            DimensionRegionCache dimCache = dimCacheMap.get(dimension);
            if (!dimCache.getRegions().isEmpty()) {
                // YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent(  "data.nbt.dimensions.load.dim.restore", dimKey).getString());
                YetAnotherWorldProtector.LOGGER.info(new TranslationTextComponent("Restoring region hierarchy for regions in dimension '" + dimKey + "'").getString());
                DimensionalRegion dimRegion = dimCache.getDimensionalRegion();
                dimCache.getRegionsInDimension().values().forEach(region -> {
                    // set child references
                    region.getChildrenNames().forEach(childName -> {
                        if (!dimCache.contains(childName)) {
                            YetAnotherWorldProtector.LOGGER.error(new TranslationTextComponent("Corrupt save data. Child region '" + childName + "' not found in dimension '" + dimKey + "'!").getString());
                        } else {
                            region.addChild(dimCache.getRegion(childName));
                        }
                    });
                    // set parent reference
                    String parentName = region.getParentName();
                    boolean hasValidParent = parentName != null && !parentName.isEmpty(); // hasNonEmptyStringParent rather
                    if (hasValidParent) {
                        if (region.getRegionType() == RegionType.LOCAL && parentName.equals(dimRegion.getName())) {
                            dimRegion.addChild(region);
                        } else {
                            if (!dimCache.contains(parentName)) {
                                YetAnotherWorldProtector.LOGGER.error(new TranslationTextComponent("Corrupt save data. Parent region '" + parentName + "' not found in dimension '" + dimKey + "'!").getString());
                            } else {
                                IMarkableRegion parent = dimCache.getRegion(parentName);
                                if (parent == null) {
                                    YetAnotherWorldProtector.LOGGER.error(new TranslationTextComponent("Corrupt save data. Parent region '" + parentName + "' not found in dimension '" + dimKey + "'!").getString());
                                } else {
                                    parent.addChild(region);
                                }
                            }
                        }
                    }
                });
            }
            globalRegion.addChild(dimCache.getDimensionalRegion());
        }
        globalRegion.addChild(globalRegion);
    }

    public int getTotalRegionAmount() {
        return dimCacheMap.values().stream()
                .mapToInt(regionCache -> regionCache.getRegionNames().size())
                .sum();
    }

    public int getRegionAmount(RegistryKey<World> dim) {
        return cacheFor(dim).getRegions().size();
    }

    public int getDimensionAmount() {
        return dimCacheMap.keySet().size();
    }

    public Collection<String> getDimensionList() {
        return dimCacheMap.keySet().stream()
                .map(entry -> entry.location().toString())
                .collect(Collectors.toList());
    }

    public GlobalRegion getGlobalRegion() {
        return globalRegion;
    }

    public void resetGlobalRegion() {
        List<IProtectedRegion> collect = new ArrayList<>(globalRegion.getChildren().values());
        globalRegion = new GlobalRegion();
        collect.forEach(dr -> {
            globalRegion.addChild(dr);
        });
        save();
    }

    public Collection<IMarkableRegion> getRegionsFor(RegistryKey<World> dim) {
        return cacheFor(dim).getRegions();
    }

    public boolean containsCacheFor(RegistryKey<World> dim) {
        return dimCacheMap.containsKey(dim);
    }

    public DimensionRegionCache cacheFor(RegistryKey<World> dim) {
        if (!dimCacheMap.containsKey(dim)) {
            newCacheFor(dim);
            save();
        }
        return dimCacheMap.get(dim);
    }

    /**
     * Method to check if a region name is valid for a given dimension. <br></br>
     * A region name is valid if it matches the pattern and is not already used in the dimension.
     *
     * @param dim        the dimension to be checked.
     * @param regionName the name of the region to be checked.
     * @return -1 if the region name is invalid, 0 if the region name is valid, 1 if the region name is already used in the dimension.
     */
    public int isValidRegionName(RegistryKey<World> dim, String regionName) {
        List<String> commandStrings = Arrays.stream(values()).map(CommandConstants::toString).collect(Collectors.toList());
        if (!regionName.matches(RegionArgumentType.VALID_NAME_PATTERN.pattern())
                || commandStrings.contains(regionName.toLowerCase())) {
            return -1;
        }
        if (cacheFor(dim).contains(regionName)) {
            return 1;
        }
        return 0;
    }

    public DimensionRegionCache newCacheFor(RegistryKey<World> dim) {
        DimensionRegionCache cache = new DimensionRegionCache(dim);
        addFlags(RegionConfig.getDefaultDimFlags(), cache.getDimensionalRegion());
        cache.getDimensionalRegion().setIsActive(RegionConfig.shouldActivateNewDimRegion());
        globalRegion.addChild(cache.getDimensionalRegion());
        dimCacheMap.put(dim, cache);
        dimensionDataNames.add(cache.getDimensionalRegion().getName());
        return cache;
    }

    public static void addFlags(Set<String> flags, IProtectedRegion region) {
        flags.stream()
                .map(RegionFlag::fromId)
                .forEach(flag -> {
                    switch (flag.type) {
                        case BOOLEAN_FLAG:
                            region.addFlag(new BooleanFlag(flag));
                            break;
                        case LIST_FLAG:
                        case INT_FLAG:
                            throw new NotImplementedException("");
                    }
                });
    }
}
