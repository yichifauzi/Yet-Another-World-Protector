package de.z0rdak.yawp.config.server;

import de.z0rdak.yawp.YetAnotherWorldProtector;
import de.z0rdak.yawp.api.events.region.FlagCheckEvent;
import de.z0rdak.yawp.api.events.region.FlagCheckResult;
import de.z0rdak.yawp.core.flag.FlagCategory;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.core.flag.RegionFlag;
import de.z0rdak.yawp.core.region.RegionType;
import de.z0rdak.yawp.util.AreaUtil;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static de.z0rdak.yawp.YetAnotherWorldProtector.MODID;
import static de.z0rdak.yawp.config.ConfigRegistry.CONFIG_LOGGER;

public class LoggingConfig {

    public static final ForgeConfigSpec CONFIG_SPEC;
    public static final String CONFIG_NAME = YetAnotherWorldProtector.MODID + "-logging.toml";

    public static final Logger FLAG_LOGGER = LogManager.getLogger(MODID.toUpperCase() + "-Flags");

    private static final ForgeConfigSpec.ConfigValue<Boolean> FLAG_CHECK_LOG;
    private static final ForgeConfigSpec.ConfigValue<Boolean> FLAG_RESULT_LOG;
    private static final ForgeConfigSpec.ConfigValue<Boolean> LOG_EMPTY_RESULTS;
    // private static final ForgeConfigSpec.ConfigValue<Boolean> DETAILED_PLAYER_FLAG_LOG;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOG_FLAG_CATEGORIES;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOG_FLAGS;

    static {
        final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        BUILDER.push("YetAnotherWorldProtector logging configuration").build();

        FLAG_CHECK_LOG = BUILDER.comment("Enable logging of flag checks.")
                .define("log_flag_check", false);

        FLAG_RESULT_LOG = BUILDER.comment("Enable logging of flag check results.")
                .define("log_flag_result", true);

        LOG_EMPTY_RESULTS = BUILDER.comment("Enable logging of empty (without responsible region) flag check results.")
                .define("log_empty_results", false);

        LOG_FLAG_CATEGORIES = BUILDER.comment("List of flag categories which shall be logged.\nValid categories are: player, block, entity, item, environment, protection and * (for all).")
                .defineListAllowEmpty(Collections.singletonList("log_flag_categories"), () -> Collections.singletonList(FlagCategory.PLAYER.name), LoggingConfig::isValidCategory);

        LOG_FLAGS = BUILDER.comment("List of flags which shall be logged.")
                .defineListAllowEmpty(Collections.singletonList("log_flags"), () -> Arrays.asList(RegionFlag.BREAK_BLOCKS.name, RegionFlag.PLACE_BLOCKS.name), LoggingConfig::isValidFlag);

        // DETAILED_PLAYER_FLAG_LOG = BUILDER.comment("Enable logging of detailed flag checks for player related flags.").define("log_detailed_player_flags", false);

        BUILDER.pop();
        CONFIG_SPEC = BUILDER.build();

    }

    public static Set<String> getFlagCategories() {
        return LOG_FLAG_CATEGORIES.get().stream()
                .filter(Objects::nonNull)
                .map(String::toString)
                .collect(Collectors.toSet());
    }

    public static Set<String> getFlagsToLog() {
        return LOG_FLAGS.get().stream()
                .filter(Objects::nonNull)
                .map(String::toString)
                .collect(Collectors.toSet());
    }

    private static boolean isValidCategory(Object entity) {
        if (entity instanceof String) {
            try {
                String str = (String) entity;
                FlagCategory category = FlagCategory.from(str);
                return category != null || str.equalsIgnoreCase("*");
            } catch (IllegalArgumentException e) {
                FLAG_LOGGER.warn("Invalid flag category supplied for 'log_flag_categories': {}", entity);
                return false;
            }
        }
        return false;
    }

    public static boolean isValidFlag(Object flag) {
        if (flag instanceof String) {
            boolean contains = RegionFlag.contains((String) flag);
            if (!contains) {
                CONFIG_LOGGER.warn("Invalid flag supplied for 'log_flags': {}", flag);
            }
            return contains;
        }
        CONFIG_LOGGER.warn("Invalid flag supplied for 'log_flags': {}", flag);
        return false;
    }

    public static boolean shouldLogFlagChecks() {
        return FLAG_CHECK_LOG.get();
    }

    public static boolean shouldLogFlagCheckResults() {
        return FLAG_RESULT_LOG.get();
    }

    public static boolean shouldLogEmptyResults() {
        return LOG_EMPTY_RESULTS.get();
    }

    /*
     public static boolean shouldLogDetailedPlayerFlags() {
        return DETAILED_PLAYER_FLAG_LOG.get();
    }
    */

    public static boolean logCheck(FlagCheckEvent check) {
        boolean flagMatchesLogCategory = RegionFlag.matchesCategory(check.getRegionFlag(), getFlagCategories());
        boolean flagIsInConfig = LoggingConfig.getFlagsToLog().contains(check.getRegionFlag().name);
        if (flagMatchesLogCategory || flagIsInConfig) {
            FLAG_LOGGER.info("[Check] {}, at {}, in '{}', Player={}, Id={}",
                    check.getRegionFlag().name,
                    AreaUtil.blockPosStr(check.getTarget()),
                    check.getDimension().location().toString(),
                    check.getPlayer() == null ? "n/a" : check.getPlayer().getDisplayName().getString(),
                    check.getId());
        }
        return true;
    }

    public static FlagCheckResult logResult(FlagCheckResult result) {
        FlagCheckEvent check = result.getFlagCheck();
        boolean flagMatchesLogCategory = RegionFlag.matchesCategory(check.getRegionFlag(), getFlagCategories());
        boolean flagIsInConfig = LoggingConfig.getFlagsToLog().contains(check.getRegionFlag().name);
        if (flagMatchesLogCategory || flagIsInConfig) {
            if (result.getResponsible() == null || result.getFlag() == null) {
                // semantically equals to result.getFlagState() == FlagState.UNDEFINED
                if (shouldLogEmptyResults()) {
                    FLAG_LOGGER.info("[Result] No region for check with Id={}", check.getId());
                }
            } else {
                if (result.getResponsible().getRegionType() != RegionType.LOCAL) {
                    IFlag flag = result.getFlag();
                    FLAG_LOGGER.info("[Result] {} ({}), Region='{}', Id={}",
                            flag.getName(),
                            flag.getState().name,
                            result.getResponsible().getName(),
                            result.getFlagCheck().getId());
                } else {
                    IFlag flag = result.getFlag();
                    FLAG_LOGGER.info("[Result] {} ({}), Region='{}', in '{}', Id={}",
                            flag.getName(),
                            flag.getState().name,
                            result.getResponsible().getName(),
                            result.getResponsible().getDim().location().toString(),
                            result.getFlagCheck().getId());
                }
            }
        }
        return result;
    }
}