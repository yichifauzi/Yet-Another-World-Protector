package de.z0rdak.yawp.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.z0rdak.yawp.commands.arguments.region.RegionArgumentType;
import de.z0rdak.yawp.config.server.RegionConfig;
import de.z0rdak.yawp.core.area.AreaType;
import de.z0rdak.yawp.core.area.CuboidArea;
import de.z0rdak.yawp.core.area.SphereArea;
import de.z0rdak.yawp.core.region.CuboidRegion;
import de.z0rdak.yawp.core.region.DimensionalRegion;
import de.z0rdak.yawp.core.region.IMarkableRegion;
import de.z0rdak.yawp.core.region.SphereRegion;
import de.z0rdak.yawp.managers.data.region.DimensionRegionCache;
import de.z0rdak.yawp.managers.data.region.RegionDataManager;
import de.z0rdak.yawp.util.LocalRegions;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.BlockPosArgument;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static de.z0rdak.yawp.commands.CommandConstants.*;
import static de.z0rdak.yawp.commands.CommandUtil.*;
import static de.z0rdak.yawp.commands.arguments.ArgumentUtil.*;
import static de.z0rdak.yawp.util.MessageUtil.*;

public class DimensionCommands {

    private DimensionCommands() {
    }

    private static List<String> getRegionNameSuggestions() {
        String examples = new TranslationTextComponent("cli.info.region.name.examples").getString();
        return Arrays.asList(examples.split(","));
    }

    private static String getRandomExample() {
        List<String> regionNameSuggestions = getRegionNameSuggestions();
        return regionNameSuggestions.get(new Random().nextInt(regionNameSuggestions.size()));
    }

    // FIXME: typing in invalid dimension is adding this dimension to set of dims
    public static LiteralArgumentBuilder<CommandSource> build() {
        return literal(DIM)
                /* /wp dimension <dim> list region */
                .then(Commands.argument(DIM.toString(), DimensionArgument.dimension())
                        /* /wp dimension <dim> [info] */
                        .executes(ctx -> CommandUtil.promptRegionInfo(ctx, getDimCacheArgument(ctx).getDimensionalRegion()))
                        .then(literal(INFO)
                                .executes(ctx -> CommandUtil.promptRegionInfo(ctx, getDimCacheArgument(ctx).getDimensionalRegion())))
                        .then(buildListSubCommand((ctx) -> getDimCacheArgument(ctx).getDimensionalRegion()))
                        .then(buildClearSubCommand((ctx) -> getDimCacheArgument(ctx).getDimensionalRegion()))
                        .then(buildAddSubCommand((ctx) -> getDimCacheArgument(ctx).getDimensionalRegion()))
                        .then(buildRemoveSubCommand((ctx) -> getDimCacheArgument(ctx).getDimensionalRegion()))
                        .then(buildCopySubCommand((ctx) -> getDimCacheArgument(ctx).getDimensionalRegion()))
                        .then(literal(LIST)
                                .then(literal(CommandConstants.LOCAL)
                                        .executes(ctx -> promptDimensionRegionList(ctx, getDimCacheArgument(ctx), 0))
                                        .then(Commands.argument(PAGE.toString(), IntegerArgumentType.integer(0))
                                                .executes(ctx -> promptDimensionRegionList(ctx, getDimCacheArgument(ctx), getPageNoArgument(ctx)))))
                        )
                        .then(literal(DELETE)
                                .then(Commands.argument(CommandConstants.LOCAL.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> RegionArgumentType.region().listSuggestions(ctx, builder))
                                        .executes(ctx -> attemptDeleteRegion(ctx, getDimCacheArgument(ctx), getRegionArgument(ctx)))
                                        .then(Commands.literal("-y")
                                                .executes(ctx -> deleteRegion(ctx, getDimCacheArgument(ctx), getRegionArgument(ctx))))))
                        .then(literal(DELETE_ALL)
                                .then(literal(REGIONS)
                                        .executes(ctx -> deleteRegions(ctx, getDimCacheArgument(ctx)))
                                        .then(literal(FOREVER)
                                                .then(literal(SERIOUSLY)
                                                        .executes(ctx -> deleteRegions(ctx, getDimCacheArgument(ctx)))))))
                        /* /wp dimension <dim> activate */
                        .then(literal(STATE)
                                .executes(ctx -> CommandUtil.promptRegionState(ctx, getDimCacheArgument(ctx).getDimensionalRegion()))
                                .then(literal(ALERT)
                                        .executes(ctx -> CommandUtil.setAlertState(ctx, getDimCacheArgument(ctx).getDimensionalRegion(), !getDimCacheArgument(ctx).getDimensionalRegion().isMuted()))
                                        .then(Commands.argument(ALERT.toString(), BoolArgumentType.bool())
                                                .executes(ctx -> CommandUtil.setAlertState(ctx, getDimCacheArgument(ctx).getDimensionalRegion(), getAlertArgument(ctx))))
                                )
                                .then(literal(ALERT_LOCAL)
                                        .then(Commands.argument(ALERT.toString(), BoolArgumentType.bool())
                                                .executes(ctx -> setAlertStateForAllLocal(ctx, getDimCacheArgument(ctx), getAlertArgument(ctx))))
                                )
                                .then(literal(ENABLE)
                                        .executes(ctx -> CommandUtil.setActiveState(ctx, getDimCacheArgument(ctx).getDimensionalRegion(), !getDimCacheArgument(ctx).getDimensionalRegion().isActive()))
                                        .then(Commands.argument(ENABLE.toString(), BoolArgumentType.bool())
                                                .executes(ctx -> CommandUtil.setActiveState(ctx, getDimCacheArgument(ctx).getDimensionalRegion(), getEnableArgument(ctx))))
                                )
                                .then(literal(ENABLE_LOCAL)
                                        .then(Commands.argument(ENABLE.toString(), BoolArgumentType.bool())
                                                .executes(ctx -> setActiveStateForAllLocal(ctx, getDimCacheArgument(ctx), getEnableArgument(ctx))))
                                )
                        )
                        .then(literal(CREATE)
                                .then(literal(CommandConstants.LOCAL)
                                        .then(Commands.argument(CommandConstants.LOCAL.toString(), StringArgumentType.word())
                                                .suggests((ctx, builder) -> ISuggestionProvider.suggest(Collections.singletonList(getRandomExample()), builder))
                                                //.then(Commands.argument(AREA.toString(), StringArgumentType.word())
                                                //        .suggests((ctx, builder) -> AreaArgumentType.areaType().listSuggestions(ctx, builder))
                                                //        .executes(ctx -> createRegion(ctx.getSource(), getRegionNameArgument(ctx), getDimCacheArgument(ctx), getAreaTypeArgument(ctx))))
                                                .then(Commands.literal(AreaType.CUBOID.areaType)
                                                        .then(Commands.argument(POS1.toString(), BlockPosArgument.blockPos())
                                                                .then(Commands.argument(POS2.toString(), BlockPosArgument.blockPos())
                                                                        .executes(ctx -> createCuboidRegion(ctx, getRegionNameArgument(ctx), getDimCacheArgument(ctx),
                                                                                BlockPosArgument.getOrLoadBlockPos(ctx, POS1.toString()),
                                                                                BlockPosArgument.getOrLoadBlockPos(ctx, POS2.toString()), null))
                                                                        .then(Commands.argument(CommandConstants.OWNER.toString(), EntityArgument.player())
                                                                                .executes(ctx -> createCuboidRegion(ctx, getRegionNameArgument(ctx), getDimCacheArgument(ctx),
                                                                                        BlockPosArgument.getOrLoadBlockPos(ctx, POS1.toString()),
                                                                                        BlockPosArgument.getOrLoadBlockPos(ctx, POS2.toString()), getOwnerArgument(ctx))))))
                                                )
                                        )
                                )
                        )
                        .then(literal(RESET)
                                .then(literal(DIM)
                                        .executes(ctx -> resetDimRegion(ctx, getDimCacheArgument(ctx))))
                                .then(literal(REGIONS)
                                        .executes(ctx -> resetLocalRegions(ctx, getDimCacheArgument(ctx))))
                        )
                );
    }


    private static int setActiveStateForAllLocal(CommandContext<CommandSource> ctx, DimensionRegionCache dimCache, boolean enable) {
        if (dimCache != null) {
            dimCache.getRegionsInDimension().values().forEach(region -> region.setIsActive(enable));
            String state = enable ? "Enabled" : "Disabled";
            sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.info.region.state.enable.set.value.local",
                    state, buildRegionInfoLink(dimCache.getDimensionalRegion())));
            RegionDataManager.save();
            return 0;
        } else {
            return 1;
        }
    }

    private static int setAlertStateForAllLocal(CommandContext<CommandSource> ctx, DimensionRegionCache dimCache, boolean mute) {
        if (dimCache != null) {
            dimCache.getRegionsInDimension().values().forEach(region -> region.setIsMuted(mute));
            String state = mute ? "Enabled" : "Disabled";
            sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.info.region.state.alert.set.value.local",
                    state, buildRegionInfoLink(dimCache.getDimensionalRegion())));
            RegionDataManager.save();
            return 0;
        } else {
            return 1;
        }
    }

    private static int resetLocalRegions(CommandContext<CommandSource> ctx, DimensionRegionCache dimCache) {
        // TODO: What should happen? Clear players? Clear flags? Clear.... ?
        return 0;
    }

    private static int deleteRegions(CommandContext<CommandSource> ctx, DimensionRegionCache dimCache) {
        // TODO:
        return 0;
    }

    private static int resetDimRegion(CommandContext<CommandSource> ctx, DimensionRegionCache dimCache) {
        // TODO: Remove flags
        // TODO: Remove players
        // TODO: Remove teams
        // TODO: apply default region state
        return 0;
    }

    public static int checkValidRegionName(String regionName, DimensionRegionCache dimCache) {
        List<String> commandStrings = Arrays.stream(values()).map(CommandConstants::toString).collect(Collectors.toList());
        if (!regionName.matches(RegionArgumentType.VALID_NAME_PATTERN.pattern())
                || commandStrings.contains(regionName.toLowerCase())) {
            return -1;
        }
        if (dimCache.contains(regionName)) {
            return 1;
        }
        return 0;
    }

    private static int createCuboidRegion(CommandContext<CommandSource> ctx, String regionName, DimensionRegionCache dimCache, BlockPos pos1, BlockPos pos2, @Nullable ServerPlayerEntity owner) {
        int res = checkValidRegionName(regionName, dimCache);
        if (res == -1) {
            sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.dim.info.region.create.name.invalid", regionName));
            return res;
        }
        if (res == 1) {
            sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.dim.info.region.create.name.exists", dimCache.getDimensionalRegion().getName(), buildRegionInfoLink(dimCache.getRegion(regionName))));
            return res;
        }
        CuboidRegion region = new CuboidRegion(regionName, new CuboidArea(pos1, pos2), owner, dimCache.dimensionKey());
        RegionDataManager.addFlags(RegionConfig.getDefaultFlags(), region);
        dimCache.addRegion(region);
        LocalRegions.ensureHigherRegionPriorityFor(region, RegionConfig.getDefaultPriority());
        RegionDataManager.save();
        sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.dim.info.region.create.success", buildRegionInfoLink(region)));
        return 0;
    }

    private static int createSphereRegion(CommandContext<CommandSource> ctx, @Nonnull String regionName, DimensionRegionCache dimCache, BlockPos center, BlockPos outerPos, ServerPlayerEntity owner) {
        int res = checkValidRegionName(regionName, dimCache);
        if (res == -1) {
            sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.dim.info.region.create.name.invalid", regionName));
            return res;
        }
        if (res == 1) {
            sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.dim.info.region.create.name.exists", dimCache.getDimensionalRegion().getName(), buildRegionInfoLink(dimCache.getRegion(regionName))));
            return res;
        }
        SphereArea area = new SphereArea(center, outerPos);
        SphereRegion region = new SphereRegion(regionName, area, owner, dimCache.dimensionKey());
        RegionDataManager.addFlags(RegionConfig.getDefaultFlags(), region);
        dimCache.addRegion(region);
        RegionDataManager.save();
        sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.dim.info.region.create.success", buildRegionInfoLink(region)));
        return 0;
    }

    private static int attemptDeleteRegion(CommandContext<CommandSource> ctx, DimensionRegionCache dim, IMarkableRegion region) {
        if (dim.contains(region.getName())) {
            IFormattableTextComponent removeRegionLink = buildRemoveRegionLink(region);
            sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.info.dim.region.remove.attempt",
                    buildRegionInfoLink(region), buildRegionInfoLink(dim.getDimensionalRegion(), removeRegionLink)));
            return 0;
        }
        return 1;
    }

    private static int deleteRegion(CommandContext<CommandSource> ctx, DimensionRegionCache dim, IMarkableRegion region) {
        if (dim.contains(region.getName())) {
            if (!region.getChildren().isEmpty()) {
                sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.info.dim.region.remove.fail.hasChildren", buildRegionInfoLink(region)));
                return -1;
            }
            if (region.getParent() != null) {
                region.getParent().removeChild(region);
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(region.getDim());
                dimCache.getDimensionalRegion().addChild(region);
            }
            dim.removeRegion(region);
            RegionDataManager.save();
            sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.info.dim.region.remove.confirm", region.getName(), buildRegionInfoLink(dim.getDimensionalRegion())));
            return 0;
        }
        return 1;
    }

    private static int promptDimensionRegionList(CommandContext<CommandSource> ctx, DimensionRegionCache dimCache, int pageNo) {
        if (dimCache != null) {
            DimensionalRegion dimRegion = dimCache.getDimensionalRegion();
            List<IMarkableRegion> regionsForDim = dimCache.getRegionsInDimension()
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(IMarkableRegion::getName))
                    .collect(Collectors.toList());
            if (regionsForDim.isEmpty()) {
                sendCmdFeedback(ctx.getSource(), new TranslationTextComponent("cli.msg.dim.info.regions.empty", buildRegionInfoLink(dimCache.getDimensionalRegion())));
                return -1;
            }
            List<IFormattableTextComponent> regionPagination = buildPaginationComponents(
                    buildRegionListHeader(dimRegion),
                    buildCommandStr(DIM.toString(), dimRegion.getName(), LIST.toString(), CommandConstants.LOCAL.toString()),
                    buildRemoveRegionEntries(dimRegion, regionsForDim),
                    pageNo,
                    new StringTextComponent(" - ").append(buildDimCreateRegionLink(dimRegion)));
            regionPagination.forEach(line -> sendCmdFeedback(ctx.getSource(), line));
            return 0;
        }
        return 1;
    }
}
