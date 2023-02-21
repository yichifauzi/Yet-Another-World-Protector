package de.z0rdak.yawp.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.z0rdak.yawp.YetAnotherWorldProtector;
import de.z0rdak.yawp.commands.arguments.flag.RegionFlagArgumentType;
import de.z0rdak.yawp.commands.arguments.region.AddRegionChildArgumentType;
import de.z0rdak.yawp.commands.arguments.region.RegionArgumentType;
import de.z0rdak.yawp.commands.arguments.region.RemoveRegionChildArgumentType;
import de.z0rdak.yawp.config.server.RegionConfig;
import de.z0rdak.yawp.core.affiliation.AffiliationType;
import de.z0rdak.yawp.core.area.AreaType;
import de.z0rdak.yawp.core.area.CuboidArea;
import de.z0rdak.yawp.core.flag.BooleanFlag;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.core.flag.RegionFlag;
import de.z0rdak.yawp.core.region.*;
import de.z0rdak.yawp.core.stick.AbstractStick;
import de.z0rdak.yawp.core.stick.MarkerStick;
import de.z0rdak.yawp.managers.data.region.DimensionRegionCache;
import de.z0rdak.yawp.managers.data.region.RegionDataManager;
import de.z0rdak.yawp.util.LocalRegions;
import de.z0rdak.yawp.util.StickException;
import de.z0rdak.yawp.util.StickType;
import de.z0rdak.yawp.util.StickUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.BlockPosArgument;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.command.arguments.TeamArgument;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static de.z0rdak.yawp.commands.CommandConstants.*;
import static de.z0rdak.yawp.core.region.RegionType.LOCAL;
import static de.z0rdak.yawp.util.CommandUtil.*;
import static de.z0rdak.yawp.util.MessageUtil.*;


public class RegionCommands {

    public static final LiteralArgumentBuilder<CommandSource> REGION_COMMAND = registerRegionCommands();

    private RegionCommands() {
    }

    public static LiteralArgumentBuilder<CommandSource> registerRegionCommands() {
        return literal(REGION)
                .then(Commands.argument(DIMENSION.toString(), DimensionArgument.dimension())
                        .then(regionCommands()));
    }

    public final static String MEMBER = "member";
    public final static String OWNER = "owner";

    /**
     * TODO: Command to invert enable and alert based on region state
     * TODO: Renaming a region
     *
     * @return
     */
    private static RequiredArgumentBuilder<CommandSource, String> regionCommands() {
        List<String> affiliationList = Arrays.asList(MEMBER, OWNER);
        return Commands.argument(REGION.toString(), StringArgumentType.word())
                .suggests((ctx, builder) -> RegionArgumentType.region().listSuggestions(ctx, builder))
                .executes(ctx -> promptRegionInfo(ctx.getSource(), getRegionArgument(ctx)))
                .then(literal(INFO)
                        .executes(ctx -> promptRegionInfo(ctx.getSource(), getRegionArgument(ctx))))
                .then(literal(SPATIAL)
                        .executes(ctx -> promptRegionSpatialProperties(ctx.getSource(), getRegionArgument(ctx))))
                .then(literal(STATE)
                        .executes(ctx -> promptRegionState(ctx.getSource(), getRegionArgument(ctx)))
                        .then(literal(ALERT)
                                // TODO: add default true and toggle cmd
                                .then(Commands.argument(ALERT.toString(), BoolArgumentType.bool())
                                        .executes(ctx -> setAlertState(ctx.getSource(), getRegionArgument(ctx), getAlertArgument(ctx)))))
                        .then(literal(ENABLE)
                                // TODO: add default true and toggle cmd
                                .then(Commands.argument(ENABLE.toString(), BoolArgumentType.bool())
                                        .executes(ctx -> setEnableState(ctx.getSource(), getRegionArgument(ctx), getEnableArgument(ctx)))))
                        .then(literal(PRIORITY)
                                .then(Commands.argument(PRIORITY.toString(), IntegerArgumentType.integer())
                                        .executes(ctx -> setPriority(ctx.getSource(), getRegionArgument(ctx), getPriorityArgument(ctx))))
                                .then(literal(INC)
                                        .then(Commands.argument(PRIORITY.toString(), IntegerArgumentType.integer())
                                                .executes(ctx -> setPriority(ctx.getSource(), getRegionArgument(ctx), getPriorityArgument(ctx), 1))))
                                .then(literal(DEC)
                                        .then(Commands.argument(PRIORITY.toString(), IntegerArgumentType.integer())
                                                .executes(ctx -> setPriority(ctx.getSource(), getRegionArgument(ctx), getPriorityArgument(ctx), -1))))))
                .then(literal(LIST)
                        .then(literal(FLAG)
                                .executes(ctx -> promptRegionFlags(ctx.getSource(), getRegionArgument(ctx), 0))
                                .then(Commands.argument(PAGE.toString(), IntegerArgumentType.integer(0))
                                        .executes(ctx -> promptRegionFlags(ctx.getSource(), getRegionArgument(ctx), getPageNoArgument(ctx)))))
                        .then(literal(CommandConstants.OWNER)
                                .executes(ctx -> promptRegionAffiliates(ctx.getSource(), getRegionArgument(ctx), OWNER))
                                .then(literal(TEAM)
                                        .executes(ctx -> promptRegionAffiliationList(ctx.getSource(), getRegionArgument(ctx), OWNER, AffiliationType.TEAM, 0))
                                        .then(Commands.argument(PAGE.toString(), IntegerArgumentType.integer(0))
                                                .executes(ctx -> promptRegionAffiliationList(ctx.getSource(), getRegionArgument(ctx), OWNER, AffiliationType.TEAM, getPageNoArgument(ctx)))))
                                .then(literal(PLAYER)
                                        .executes(ctx -> promptRegionAffiliationList(ctx.getSource(), getRegionArgument(ctx), OWNER, AffiliationType.PLAYER, 0))
                                        .then(Commands.argument(PAGE.toString(), IntegerArgumentType.integer(0))
                                                .executes(ctx -> promptRegionAffiliationList(ctx.getSource(), getRegionArgument(ctx), OWNER, AffiliationType.PLAYER, getPageNoArgument(ctx))))
                                ))
                        .then(literal(CommandConstants.MEMBER)
                                .executes(ctx -> promptRegionAffiliates(ctx.getSource(), getRegionArgument(ctx), MEMBER))
                                .then(literal(TEAM)
                                        .executes(ctx -> promptRegionAffiliationList(ctx.getSource(), getRegionArgument(ctx), MEMBER, AffiliationType.TEAM, 0))
                                        .then(Commands.argument(PAGE.toString(), IntegerArgumentType.integer(0))
                                                .executes(ctx -> promptRegionAffiliationList(ctx.getSource(), getRegionArgument(ctx), MEMBER, AffiliationType.TEAM, getPageNoArgument(ctx)))))
                                .then(literal(PLAYER)
                                        .executes(ctx -> promptRegionAffiliationList(ctx.getSource(), getRegionArgument(ctx), MEMBER, AffiliationType.PLAYER, 0))
                                        .then(Commands.argument(PAGE.toString(), IntegerArgumentType.integer(0))
                                                .executes(ctx -> promptRegionAffiliationList(ctx.getSource(), getRegionArgument(ctx), MEMBER, AffiliationType.PLAYER, getPageNoArgument(ctx))))
                                ))
                        .then(literal(CHILDREN)
                                .executes(ctx -> promptRegionChildren(ctx.getSource(), getRegionArgument(ctx), 0))
                                .then(Commands.argument(PAGE.toString(), IntegerArgumentType.integer(0))
                                        .executes(ctx -> promptRegionChildren(ctx.getSource(), getRegionArgument(ctx), getPageNoArgument(ctx))))
                        ))
                .then(literal(AREA)
                        .then(Commands.literal(AreaType.CUBOID.areaType)
                                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                .executes(ctx -> updateArea(ctx.getSource(), getRegionArgument(ctx), AreaType.CUBOID,
                                                        BlockPosArgument.getOrLoadBlockPos(ctx, "pos1"),
                                                        BlockPosArgument.getOrLoadBlockPos(ctx, "pos2")))))
                        ))
                // TODO: rename region
                // TODO: Only with marker
                //.then(literal(UPDATE)
                //        .then(Commands.argument(AREA.toString(), StringArgumentType.word())
                //                .suggests((ctx, builder) -> AreaArgumentType.areaType().listSuggestions(ctx, builder))
                //                .executes(ctx -> updateRegion(ctx.getSource(), getRegionArgument(ctx)))))
                .then(literal(ADD)
                        .then(literal(CommandConstants.PLAYER)
                                .then(Commands.argument(CommandConstants.AFFILIATION.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                        .then(Commands.argument(CommandConstants.PLAYER.toString(), EntityArgument.player())
                                                .executes(ctx -> addPlayer(ctx.getSource(), getPlayerArgument(ctx), getRegionArgument(ctx), getAffiliationArgument(ctx)))))
                                .then(Commands.argument(CommandConstants.AFFILIATION.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                        .then(Commands.argument(CommandConstants.PLAYER.toString(), EntityArgument.player())
                                                .executes(ctx -> addPlayer(ctx.getSource(), getPlayerArgument(ctx), getRegionArgument(ctx), getAffiliationArgument(ctx))))))
                        .then(literal(CommandConstants.TEAM)
                                .then(Commands.argument(CommandConstants.AFFILIATION.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                        .then(Commands.argument(CommandConstants.TEAM.toString(), TeamArgument.team())
                                                .executes(ctx -> addTeam(ctx.getSource(), getTeamArgument(ctx), getRegionArgument(ctx), getAffiliationArgument(ctx)))))
                                .then(Commands.argument(CommandConstants.AFFILIATION.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                        .then(Commands.argument(CommandConstants.TEAM.toString(), TeamArgument.team())
                                                .executes(ctx -> addTeam(ctx.getSource(), getTeamArgument(ctx), getRegionArgument(ctx), getAffiliationArgument(ctx))))))
                        .then(literal(FLAG)
                                .then(Commands.argument(FLAG.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> RegionFlagArgumentType.flag().listSuggestions(ctx, builder))
                                        .executes(ctx -> addFlag(ctx.getSource(), getRegionArgument(ctx), getFlagArgument(ctx)))))
                        .then(literal(CHILD)
                                .then(Commands.argument(CHILD.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> AddRegionChildArgumentType.potentialChildRegions().listSuggestions(ctx, builder))
                                        .executes(ctx -> addChildren(ctx.getSource(), getRegionArgument(ctx), getChildRegionArgument(ctx))))))
                .then(literal(REMOVE)
                        .then(literal(CommandConstants.PLAYER)
                                .then(Commands.argument(CommandConstants.AFFILIATION.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                        .then(Commands.argument(CommandConstants.PLAYER.toString(), EntityArgument.player())
                                                .executes(ctx -> removePlayer(ctx.getSource(), getPlayerArgument(ctx), getRegionArgument(ctx), getAffiliationArgument(ctx)))))
                                .then(Commands.argument(CommandConstants.AFFILIATION.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                        .then(Commands.argument(CommandConstants.PLAYER.toString(), EntityArgument.player())
                                                .executes(ctx -> removePlayer(ctx.getSource(), getPlayerArgument(ctx), getRegionArgument(ctx), getAffiliationArgument(ctx))))))
                        .then(literal(CommandConstants.TEAM)
                                .then(Commands.argument(CommandConstants.AFFILIATION.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                        .then(Commands.argument(CommandConstants.TEAM.toString(), TeamArgument.team())
                                                .executes(ctx -> removeTeam(ctx.getSource(), getTeamArgument(ctx), getRegionArgument(ctx), getAffiliationArgument(ctx)))))
                                .then(Commands.argument(CommandConstants.AFFILIATION.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                        .then(Commands.argument(CommandConstants.TEAM.toString(), TeamArgument.team())
                                                .executes(ctx -> removeTeam(ctx.getSource(), getTeamArgument(ctx), getRegionArgument(ctx), getAffiliationArgument(ctx))))))
                        .then(literal(FLAG)
                                .then(Commands.argument(FLAG.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> RegionFlagArgumentType.flag().listSuggestions(ctx, builder))
                                        .executes(ctx -> removeFlag(ctx.getSource(), getRegionArgument(ctx), getFlagArgument(ctx)))))
                        .then(literal(CHILD)
                                .then(Commands.argument(CHILD.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> RemoveRegionChildArgumentType.childRegions().listSuggestions(ctx, builder))
                                        .executes(ctx -> removeChildren(ctx.getSource(), getDimCacheArgument(ctx), getRegionArgument(ctx), getChildRegionArgument(ctx))))))
                /* TODO: Facade for reverse child setting ?
                .then(literal(PARENT)
                        .then(literal(SET)
                                .then(Commands.argument(PARENT_REGION.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> SetRegionParentArgumentType.parentRegion().listSuggestions(ctx, builder))
                                        .executes(ctx -> setRegionParent(ctx.getSource(), RegionArgumentType.getRegion(ctx, REGION.toString()), RegionArgumentType.getRegion(ctx, PARENT_REGION.toString())))))
                        .then(literal(CLEAR)
                                .executes(ctx -> clearRegionParent(ctx.getSource(), RegionArgumentType.getRegion(ctx, REGION.toString())))))
                 */
                .then(literal(TELEPORT)
                        .executes(ctx -> teleport(ctx.getSource(), getRegionArgument(ctx)))
                        .then(Commands.argument(PLAYER.toString(), EntityArgument.player())
                                .executes(ctx -> teleport(ctx.getSource(), getRegionArgument(ctx), getPlayerArgument(ctx))))
                        .then(Commands.literal(SET.toString())
                                .then(Commands.argument(TARGET.toString(), BlockPosArgument.blockPos())
                                        .executes(ctx -> setTeleportPos(ctx.getSource(), getRegionArgument(ctx), BlockPosArgument.getOrLoadBlockPos(ctx, TARGET.toString()))))));
    }

    private static int updateArea(CommandSource src, IMarkableRegion region, AreaType areaType, BlockPos pos1, BlockPos pos2) {
        IProtectedRegion parent = region.getParent();
        switch (areaType) {
            case CUBOID:
                CuboidArea cuboidArea = new CuboidArea(pos1, pos2);
                CuboidRegion cuboidRegion = (CuboidRegion) region;
                if (parent instanceof DimensionalRegion) {
                    int newPriority = LocalRegions.ensureHigherRegionPriorityFor(cuboidRegion, RegionConfig.DEFAULT_REGION_PRIORITY.get());
                }
                if (parent instanceof IMarkableRegion) {
                    IMarkableRegion localParentRegion = (IMarkableRegion) parent;
                    CuboidArea parentArea = (CuboidArea) localParentRegion.getArea();
                    if (parentArea.contains(cuboidArea)) {
                        int newPriority = LocalRegions.ensureHigherRegionPriorityFor(cuboidRegion, localParentRegion.getPriority() + 1);
                    } else {
                        IFormattableTextComponent updateAreaFailMsg = new TranslationTextComponent("cli.msg.info.region.spatial.area.update.fail", buildRegionSpatialPropLink(region), buildRegionInfoLink(region, LOCAL));
                        sendCmdFeedback(src, updateAreaFailMsg);
                        return 1;
                    }
                }
                IFormattableTextComponent updateAreaMsg = new TranslationTextComponent("cli.msg.info.region.spatial.area.update", buildRegionSpatialPropLink(region), buildRegionInfoLink(region, LOCAL));
                cuboidRegion.setArea(cuboidArea);
                RegionDataManager.save();
                sendCmdFeedback(src, updateAreaMsg);
                break;
            case CYLINDER:
            case SPHERE:
            case POLYGON_3D:
            case PRISM:
                throw new UnsupportedOperationException("Unsupported region type");
        }
        return 0;
    }

    private static int removeTeam(CommandSource src, Team team, IMarkableRegion region, String affiliation) {
        switch (affiliation) {
            case "member":
                if (region.getMembers().containsTeam(team)) {
                    region.removeMember(team);
                    RegionDataManager.save();
                    sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.affiliation.team.removed", team.getName(), buildRegionInfoLink(region, LOCAL)));
                }
                break;
            case "owner":
                if (region.getOwners().containsTeam(team)) {
                    region.removeOwner(team);
                    RegionDataManager.save();
                    sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.affiliation.team.removed", team.getName(), buildRegionInfoLink(region, LOCAL)));
                }
                break;
            default:
                // TODO Create new affiliation with no permissions?
                return 1;
        }
        return 0;
    }

    private static int addTeam(CommandSource src, Team team, IMarkableRegion region, String affiliation) {
        switch (affiliation) {
            case "member":
                if (!region.getMembers().containsTeam(team)) {
                    region.addMember(team);
                    RegionDataManager.save();
                    sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.affiliation.team.added", team.getName(), affiliation, buildRegionInfoLink(region, LOCAL)));
                }
                break;
            case "owner":
                if (!region.getOwners().containsTeam(team)) {
                    region.addOwner(team);
                    RegionDataManager.save();
                    sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.affiliation.team.added", team.getName(), affiliation, buildRegionInfoLink(region, LOCAL)));
                }
                break;
            default:
                // TODO Create new affiliation with no permissions?
                return 1;
        }
        return 0;
    }

    // TODO: Option to remove player by name
    private static int removePlayer(CommandSource src, ServerPlayerEntity player, IMarkableRegion region, String affiliation) {
        switch (affiliation) {
            case "member":
                if (region.getMembers().containsPlayer(player.getUUID())) {
                    region.removeMember(player);
                    RegionDataManager.save();
                    sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.affiliation.player.removed", buildPlayerHoverComponent(player), buildRegionInfoLink(region, LOCAL)));
                }
                break;
            case "owner":
                if (region.getOwners().containsPlayer(player.getUUID())) {
                    region.removeOwner(player);
                    RegionDataManager.save();
                    sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.affiliation.player.removed", buildPlayerHoverComponent(player), buildRegionInfoLink(region, LOCAL)));
                }
                break;
            default:
                // TODO Create new affiliation with no permissions?
                return 1;
        }
        return 0;
    }

    private static int addPlayer(CommandSource src, ServerPlayerEntity player, IMarkableRegion region, String affiliation) {
        switch (affiliation) {
            case "member":
                if (!region.getMembers().containsPlayer(player.getUUID())) {
                    region.addMember(player);
                    RegionDataManager.save();
                    sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.affiliation.player.added", buildPlayerHoverComponent(player), affiliation, buildRegionInfoLink(region, LOCAL)));
                }
                break;
            case "owner":
                if (!region.getOwners().containsPlayer(player.getUUID())) {
                    region.addOwner(player);
                    RegionDataManager.save();
                    sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.affiliation.player.added", buildPlayerHoverComponent(player), affiliation, buildRegionInfoLink(region, LOCAL)));
                }
                break;
            default:
                // TODO Create new affiliation with no permissions?
                return 1;
        }
        return 0;
    }

    private static int removeChildren(CommandSource src, DimensionRegionCache dimCache, IMarkableRegion parent, IMarkableRegion child) {
        if (parent.hasChild(child)) {
            // FIXME: Removing child does not set priority correct with overlapping regions
            dimCache.getDimensionalRegion().addChild(child); // this also removes the child from the local parent
            child.setIsActive(false);
            LocalRegions.ensureLowerRegionPriorityFor((CuboidRegion) child, RegionConfig.DEFAULT_REGION_PRIORITY.get());
            RegionDataManager.save();
            IFormattableTextComponent parentLink = buildRegionInfoLink(parent, LOCAL);
            IFormattableTextComponent notLongerChildLink = buildRegionInfoLink(child, LOCAL);
            IFormattableTextComponent dimensionalLink = buildRegionInfoLink(dimCache.getDimensionalRegion(), RegionType.DIMENSION);
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.children.remove", notLongerChildLink, parentLink));
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.parent.clear", notLongerChildLink, dimensionalLink));
            return 0;
        }
        // should not happen, due to RemoveRegionChildArgumentType should only provide valid child regions
        return -1;
    }

    private static int addChildren(CommandSource src, IMarkableRegion parent, IMarkableRegion child) {
        if (!parent.hasChild(child) && child.getParent() != null && child.getParent() instanceof DimensionalRegion) {
            parent.addChild(child);
            LocalRegions.ensureHigherRegionPriorityFor((CuboidRegion) child, parent.getPriority() + 1);
            RegionDataManager.save();

            IFormattableTextComponent parentLink = buildRegionInfoLink(parent, LOCAL);
            IFormattableTextComponent childLink = buildRegionInfoLink(child, LOCAL);
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.children.add", childLink, parentLink));
            return 0;
        }
        // should not happen, due to AddRegionChildArgumentType should only provide valid child regions
        return -1;
    }

    // Adds default flag for provided RegionFlag
    private static int addFlag(CommandSource src, IMarkableRegion region, RegionFlag flag) {
        if (!region.containsFlag(flag)) {
            switch (flag.type) {
                case BOOLEAN_FLAG:
                    region.addFlag(new BooleanFlag(flag));
                    break;
                case LIST_FLAG:
                case INT_FLAG:
                    break;
            }
            RegionDataManager.save();
            // TODO: replace flag.name with link to flag info cmd
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.flags.added", flag.name, buildRegionInfoLink(region, LOCAL)));
            return 0;
        }
        return 1;
    }

    private static int removeFlag(CommandSource src, IMarkableRegion region, RegionFlag flag) {
        if (region.containsFlag(flag)) {
            region.removeFlag(flag.name);
            RegionDataManager.save();
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.flags.removed", flag.name, buildRegionInfoLink(region, LOCAL)));
            return 0;
        }
        return 1;
    }

    private static int setAlertState(CommandSource src, IMarkableRegion region, boolean showAlert) {
        boolean wasEnabled = !region.isMuted();
        region.setIsMuted(showAlert);
        RegionDataManager.save();
        if (wasEnabled == region.isMuted()) {
            boolean isEnabled = !region.isMuted();
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.state.alert.set.value", buildRegionInfoLink(region, LOCAL), wasEnabled, isEnabled));
        }
        return 0;
    }

    private static int setEnableState(CommandSource src, IMarkableRegion region, boolean enable) {
        boolean oldState = region.isActive();
        region.setIsActive(enable);
        RegionDataManager.save();
        if (oldState != region.isActive()) {
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.state.enable.set.value", buildRegionInfoLink(region, LOCAL), oldState, region.isActive()));
        }
        return 0;
    }

    private static int setPriority(CommandSource src, IMarkableRegion region, int priority, int factor) {
        long newValue = (long) region.getPriority() + ((long) priority * factor);
        if (Integer.MAX_VALUE - newValue > 0) {
            return setPriority(src, region, (int) newValue);
        } else {
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.warn.region.state.priority.set.invalid", buildRegionInfoLink(region, LOCAL), newValue));
            return -1;
        }
    }

    /**
     * Attempt to set new priority for the given region. <br>
     * Fails if region priority is used by an overlapping region at same hierarchy level.
     *
     * @param src
     * @param region
     * @param priority
     * @return
     */
    private static int setPriority(CommandSource src, IMarkableRegion region, int priority) {
        CuboidRegion cuboidRegion = (CuboidRegion) region;
        List<CuboidRegion> intersectingRegions = LocalRegions.getIntersectingRegionsFor(cuboidRegion);
        boolean existRegionWithSamePriority = intersectingRegions
                .stream()
                .anyMatch(r -> r.getPriority() == priority);
        IProtectedRegion parent = region.getParent();
        if (parent instanceof IMarkableRegion) {
            int parentPriority = ((IMarkableRegion) parent).getPriority();
            if (parentPriority >= priority) {
                IFormattableTextComponent updatePriorityFailMsg = new TranslationTextComponent("cli.msg.info.region.state.priority.set.fail.to-low", buildRegionInfoLink(region, LOCAL));
                sendCmdFeedback(src, updatePriorityFailMsg);
                return 1;
            }
        }
        if (existRegionWithSamePriority) {
            IFormattableTextComponent updatePriorityFailMsg = new TranslationTextComponent("cli.msg.info.region.state.priority.set.fail.same", buildRegionInfoLink(region, LOCAL), priority);
            sendCmdFeedback(src, updatePriorityFailMsg);
            return 1;
        } else {
            int oldPriority = region.getPriority();
            if (oldPriority != priority) {
                region.setPriority(priority);
                RegionDataManager.save();
                sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.state.priority.set.success", buildRegionInfoLink(region, LOCAL), oldPriority, region.getPriority()));
                return 0;
            } else {
                sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.state.priority.set.fail.no-change", buildRegionInfoLink(region, LOCAL)));
                return 1;
            }
        }
    }

    private static int promptRegionInfo(CommandSource src, IMarkableRegion region) {
        // == Region [<name>] overview ==
        sendCmdFeedback(src, buildRegionOverviewHeader(region, LOCAL));
        // Flags: [n flag(s)][+]
        sendCmdFeedback(src, buildInfoComponent("cli.msg.info.region.flag", buildFlagListLink(region, RegionType.LOCAL)));
        // Spatial: [Spatial Properties]
        sendCmdFeedback(src, buildInfoComponent("cli.msg.info.region.spatial", buildRegionSpatialPropLink(region)));
        // Affiliations: [owners], [members], [<listAffiliations>]
        sendCmdFeedback(src, buildInfoComponent("cli.msg.info.region.affiliation", buildAffiliationLinks(region, RegionType.LOCAL)));
        // Hierarchy: [parent][-|+], [n children][+]
        IFormattableTextComponent regionHierarchy = new TranslationTextComponent("cli.msg.info.region.hierarchy")
                .append(": ")
                .append(buildRegionParentLink(region))
                .append(new StringTextComponent(RESET + ", "))
                .append(buildRegionChildrenLink(region, LOCAL));
        sendCmdFeedback(src, regionHierarchy);
        // State: [State]
        sendCmdFeedback(src, buildInfoComponent("cli.msg.info.region.state", buildRegionStateLink(region)));
        return 0;
    }

    private static int promptRegionChildren(CommandSource src, IMarkableRegion region, int pageNo) {
        List<IMarkableRegion> children = region.getChildren().values().stream().map(r -> (IMarkableRegion) r).collect(Collectors.toList());
        IFormattableTextComponent childRegionList = new StringTextComponent("");
        if (children.isEmpty()) {
            IFormattableTextComponent noChildrenText = new TranslationTextComponent("cli.msg.info.region.children.empty", buildRegionInfoLink(region, LOCAL));
            childRegionList.append(noChildrenText);
            sendCmdFeedback(src, childRegionList);
        }
        List<IFormattableTextComponent> regionPagination = buildPaginationComponents(
                buildRegionChildrenHeader(region, LOCAL),
                buildCommandStr(REGION.toString(), region.getDim().location().toString(), region.getName(), LIST.toString(), CHILDREN.toString()),
                buildRemoveRegionEntries(region, children, LOCAL),
                pageNo,
                new StringTextComponent(" - ").append(buildRegionAddChildrenLink(region)));
        regionPagination.forEach(line -> sendCmdFeedback(src, line));
        return 0;
    }

    /**
     * == Affiliation '%s' for '%s'==
     * Players: [n player(s)][+]
     * Teams: [m team(s)][+]
     */
    private static int promptRegionAffiliates(CommandSource src, IMarkableRegion region, String affiliation) {
        sendCmdFeedback(src, buildAffiliationHeader(region, affiliation, RegionType.LOCAL));
        sendCmdFeedback(src, buildAffiliationPlayerListLink(region, affiliation, RegionType.LOCAL));
        sendCmdFeedback(src, buildAffiliationTeamListLink(region, affiliation, RegionType.LOCAL));
        return 0;
    }

    private static int promptRegionAffiliationList(CommandSource src, IMarkableRegion region, String affiliation, AffiliationType affiliationType, int pageNo) {
        List<String> affiliateNames = getAffiliateList(region, affiliation, affiliationType);
        if (affiliateNames.isEmpty()) {
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.affiliation." + affiliationType.name + ".empty", affiliation, buildRegionInfoLink(region, LOCAL)));
            return 1;
        }
        List<IFormattableTextComponent> regionPagination = buildPaginationComponents(
                buildAffiliationHeader(region, affiliation, affiliationType, RegionType.LOCAL),
                buildCommandStr(REGION.toString(), region.getDim().location().toString(), region.getName(), LIST.toString(), affiliation, affiliationType.name),
                buildRemoveAffiliationEntries(region, affiliateNames, affiliationType, affiliation, RegionType.LOCAL),
                pageNo,
                new StringTextComponent(" - ").append(buildAddAffiliateLink(region, affiliation, affiliationType, RegionType.LOCAL)));
        regionPagination.forEach(line -> sendCmdFeedback(src, line));
        return 0;
    }

    /**
     * Prompt region spatial properties like teleport location and area.
     * == Region [<name>] spatial properties ==
     * Location: [dimInfo]@[tpCoordinates]
     * Area: [spatialProperties]
     *
     * @param src
     * @param region
     * @return
     */
    public static int promptRegionSpatialProperties(CommandSource src, IMarkableRegion region) {
        sendCmdFeedback(src, buildHeader(new TranslationTextComponent("cli.msg.info.header.for", buildRegionSpatialPropLink(region), buildRegionInfoLink(region, LOCAL))));
        sendCmdFeedback(src, buildInfoComponent("cli.msg.info.region.spatial.location", buildDimensionTeleportLink(region)));
        sendCmdFeedback(src, buildInfoComponent("cli.msg.info.region.spatial.area", buildRegionAreaDetailComponent(region)));
        return 0;
    }

    /**
     * Prompt the region state to the command issuer.
     * ==  [state] for [<name>]  ==
     * Enabled: [true|false]
     * Priority: n [#][+5][-5]
     * Alert: [on|off]
     *
     * @param src
     * @param region
     * @return
     */
    public static int promptRegionState(CommandSource src, IMarkableRegion region) {
        sendCmdFeedback(src, buildHeader(new TranslationTextComponent("cli.msg.info.header.for", buildRegionStateLink(region), buildRegionInfoLink(region, LOCAL))));
        sendCmdFeedback(src, buildInfoComponent("cli.msg.info.region.state.priority", buildRegionPriorityComponent(region)));
        sendCmdFeedback(src, buildInfoComponent("cli.msg.info.region.state.enable", buildRegionEnableComponent(region)));
        sendCmdFeedback(src, buildInfoComponent("cli.msg.info.region.state.alert", buildRegionAlertComponentLink(region)));
        return 0;
    }

    public static int promptRegionFlags(CommandSource src, IMarkableRegion region, int pageNo) {
        if (region.getFlags().isEmpty()) {
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.flag.empty", buildRegionInfoLink(region, LOCAL)));
            return 1;
        }
        List<IFlag> flags = LocalRegions.getSortedFlags(region);
        List<IFormattableTextComponent> flagPagination = buildPaginationComponents(
                buildFlagHeader(region, LOCAL),
                buildCommandStr(REGION_COMMAND.toString(), region.getDim().location().toString(), region.getName(), LIST.toString(), FLAG.toString()),
                buildRemoveFlagEntries(region, flags, LOCAL),
                pageNo,
                new StringTextComponent(" - ").append(buildRegionAddFlagLink(region))
        );
        flagPagination.forEach(line -> sendCmdFeedback(src, line));
        return 0;
    }

    // TODO: Only with region marker
    // assumption: regions are only updated with the region marker when in the same dimension
    private static int updateRegion(CommandSource src, IMarkableRegion region) {
        try {
            PlayerEntity player = src.getPlayerOrException();
            ItemStack maybeStick = player.getMainHandItem();
            if (StickUtil.isVanillaStick(maybeStick)) {
                try {
                    AbstractStick abstractStick = StickUtil.getStick(maybeStick);
                    if (abstractStick.getStickType() == StickType.MARKER) {
                        MarkerStick marker = (MarkerStick) abstractStick;
                        // TODO: RegionDataManager.get().update(regionName, marker);
                    }
                } catch (StickException e) {
                    sendCmdFeedback(src, "CommandSource is not player. Aborting.. Needs RegionMarker with Block-NBT data in player hand");
                }
            }
        } catch (CommandSyntaxException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // TODO
    private static int listRegionsAround(CommandSource source) {

        return 0;
    }

    private static int teleport(CommandSource src, IMarkableRegion region) {
        try {
            ServerPlayerEntity player = src.getPlayerOrException();
            src.getServer().getCommands().getDispatcher().execute(buildRegionTpCmd(region, player.getScoreboardName()), src);
            return 0;
        } catch (CommandSyntaxException e) {
            YetAnotherWorldProtector.LOGGER.warn("Unable to teleport command source to region.");
            return -1;
        }
    }

    private static int teleport(CommandSource src, IMarkableRegion region, PlayerEntity player) {
        try {
            src.getServer().getCommands().getDispatcher().execute(buildRegionTpCmd(region, player.getScoreboardName()), src);
            return 0;
        } catch (CommandSyntaxException e) {
            YetAnotherWorldProtector.LOGGER.warn("Error executing teleport command.");
            // TODO: error executing tp command
            return -1;
        }
    }

    private static int setTeleportPos(CommandSource src, IMarkableRegion region, BlockPos target) {
        if (!region.getTpTarget().equals(target)) {
            region.setTpTarget(target);
            RegionDataManager.save();
            IFormattableTextComponent newTpTargetLink = buildDimensionalBlockTpLink(region.getDim(), target);
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.region.spatial.location.teleport.set", buildRegionInfoLink(region, LOCAL), newTpTargetLink));
            return 0;
        }
        return 1;
    }
}
