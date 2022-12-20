package de.z0rdak.yawp.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.z0rdak.yawp.YetAnotherWorldProtector;
import de.z0rdak.yawp.commands.arguments.region.RegionArgumentType;
import de.z0rdak.yawp.config.server.CommandPermissionConfig;
import de.z0rdak.yawp.config.server.RegionConfig;
import de.z0rdak.yawp.core.affiliation.PlayerContainer;
import de.z0rdak.yawp.core.area.AreaType;
import de.z0rdak.yawp.core.area.CuboidArea;
import de.z0rdak.yawp.core.area.SphereArea;
import de.z0rdak.yawp.core.flag.BooleanFlag;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.core.flag.RegionFlag;
import de.z0rdak.yawp.core.region.*;
import de.z0rdak.yawp.core.stick.MarkerStick;
import de.z0rdak.yawp.managers.data.region.DimensionRegionCache;
import de.z0rdak.yawp.managers.data.region.RegionDataManager;
import de.z0rdak.yawp.util.MessageUtil;
import de.z0rdak.yawp.util.RegionUtil;
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
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.World;
import org.apache.commons.lang3.NotImplementedException;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static de.z0rdak.yawp.commands.CommandConstants.*;
import static de.z0rdak.yawp.commands.CommandConstants.DELETE;
import static de.z0rdak.yawp.util.CommandUtil.*;
import static de.z0rdak.yawp.util.MessageUtil.*;

public class DimensionCommands {

    private DimensionCommands() {
    }

    public static final LiteralArgumentBuilder<CommandSource> DIMENSION_COMMAND = register();

    public static LiteralArgumentBuilder<CommandSource> register() {
        List<String> affiliationList = Arrays.asList("member", "owner");
        return literal(DIMENSION)
                /* /wp dimension <dim> list region */
                .then(Commands.argument(DIMENSION.toString(), DimensionArgument.dimension())
                        .then(literal(CREATE)
                                .then(literal(REGION).then(Commands.argument(REGION.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> ISuggestionProvider.suggest(Arrays.asList("name"), builder))
                                        // TODO: Implement creating region with stick
                                        //.then(Commands.argument(AREA.toString(), StringArgumentType.word())
                                        //        .suggests((ctx, builder) -> AreaArgumentType.areaType().listSuggestions(ctx, builder))
                                        //        .executes(ctx -> createRegion(ctx.getSource(), getRegionNameArgument(ctx), getDimCacheArgument(ctx), getAreaTypeArgument(ctx))))
                                        .then(Commands.literal(AreaType.CUBOID.areaType)
                                                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                                .executes(ctx -> createCuboidRegion(ctx.getSource(), getRegionNameArgument(ctx), getDimCacheArgument(ctx),
                                                                        BlockPosArgument.getOrLoadBlockPos(ctx, "pos1"),
                                                                        BlockPosArgument.getOrLoadBlockPos(ctx, "pos2"), null))
                                                                .then(Commands.argument(OWNER.toString(), EntityArgument.player())
                                                                        .executes(ctx -> createCuboidRegion(ctx.getSource(), getRegionNameArgument(ctx), getDimCacheArgument(ctx),
                                                                                BlockPosArgument.getOrLoadBlockPos(ctx, "pos1"),
                                                                                BlockPosArgument.getOrLoadBlockPos(ctx, "pos2"), getOwnerArgument(ctx))))))))
                                        /* TODO: Sphere areas are disable for now. Will be added back later
                                        .then(Commands.literal(AreaType.SPHERE.areaType)
                                                .then(Commands.argument("centerPos", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("outerPos", BlockPosArgument.blockPos())
                                                                .executes(ctx -> createSphereRegion(ctx.getSource(), getRegionNameArgument(ctx), getDimCacheArgument(ctx),
                                                                        BlockPosArgument.getOrLoadBlockPos(ctx, "centerPos"),
                                                                        BlockPosArgument.getOrLoadBlockPos(ctx, "outerPos"), null))
                                                                .then(Commands.argument(OWNER.toString(), EntityArgument.player())
                                                                        .executes(ctx -> createSphereRegion(ctx.getSource(), getRegionNameArgument(ctx), getDimCacheArgument(ctx),
                                                                                BlockPosArgument.getOrLoadBlockPos(ctx, "centerPos"),
                                                                                BlockPosArgument.getOrLoadBlockPos(ctx, "outerPos"), getOwnerArgument(ctx)))))))

                                         */

                                )
                        )
                        /* /wp dimension <dim> [info] */
                        .executes(ctx -> promptDimensionInfo(ctx.getSource(), getDimCacheArgument(ctx)))
                        .then(literal(INFO).executes(ctx -> promptDimensionInfo(ctx.getSource(), getDimCacheArgument(ctx))))
                        /* /wp dimension <dim> activate */
                        .then(literal(ENABLE)
                                // TODO: Add toggle cmd
                                .executes(ctx -> setActiveState(ctx.getSource(), getDimCacheArgument(ctx), true))
                                .then(Commands.argument(ENABLE.toString(), BoolArgumentType.bool())
                                        .executes(ctx -> setActiveState(ctx.getSource(), getDimCacheArgument(ctx), getEnableArgument(ctx)))))
                        .then(literal(LIST)
                                .then(literal(REGION).executes(ctx -> promptDimensionRegionList(ctx.getSource(), getDimCacheArgument(ctx))))
                                /* /wp dimension <dim> list owner */
                                .then(literal(OWNER).executes(ctx -> promptDimensionPlayerList(ctx.getSource(), getDimCacheArgument(ctx), OWNER)))
                                /* /wp dimension <dim> list member */
                                .then(literal(MEMBER).executes(ctx -> promptDimensionPlayerList(ctx.getSource(), getDimCacheArgument(ctx), MEMBER)))
                                /* /wp dimension <dim> list flag */
                                .then(literal(FLAG).executes(ctx -> promptDimensionFlagList(ctx.getSource(), getDimCacheArgument(ctx)))))
                        .then(literal(DELETE)
                                // FIXME: Only list region which are region.parent == dim
                                // FIXME: How to handle zombie regions? change child parent to dim? or prohibit removal when children are present
                                .then(Commands.argument(REGION.toString(), StringArgumentType.word())
                                        .suggests((ctx, builder) -> RegionArgumentType.region().listSuggestions(ctx, builder))
                                        .executes(ctx -> attemptDeleteRegion(ctx.getSource(), getDimCacheArgument(ctx), getRegionArgument(ctx)))
                                        .then(Commands.literal("-y")
                                                .executes(ctx -> deleteRegion(ctx.getSource(),  getDimCacheArgument(ctx), getRegionArgument(ctx))))))
                        .then(literal(REMOVE)
                                .then(literal(PLAYER)
                                        .then(Commands.argument(AFFILIATION.toString(), StringArgumentType.string())
                                                .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                                .then(Commands.argument(PLAYER.toString(), EntityArgument.player())
                                                        .executes(ctx -> removePlayer(ctx.getSource(), getPlayerArgument(ctx), getDimCacheArgument(ctx), getAffiliationArgument(ctx)))))

                                        .then(Commands.argument(AFFILIATION.toString(), StringArgumentType.string())
                                                .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                                .then(Commands.argument(PLAYER.toString(), EntityArgument.player())
                                                        .executes(ctx -> removePlayer(ctx.getSource(), getPlayerArgument(ctx), getDimCacheArgument(ctx), getAffiliationArgument(ctx))))))
                                .then(literal(TEAM)
                                        .then(Commands.argument(AFFILIATION.toString(), StringArgumentType.string())
                                                .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                                .then(Commands.argument(TEAM.toString(), TeamArgument.team())
                                                        .executes(ctx -> removeTeam(ctx.getSource(), getTeamArgument(ctx), getDimCacheArgument(ctx), getAffiliationArgument(ctx)))))

                                        .then(Commands.argument(AFFILIATION.toString(), StringArgumentType.string())
                                                .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                                .then(Commands.argument(TEAM.toString(), TeamArgument.team())
                                                        .executes(ctx -> removeTeam(ctx.getSource(), getTeamArgument(ctx), getDimCacheArgument(ctx), getAffiliationArgument(ctx))))))
                                .then(literal(FLAG)
                                        .then(Commands.argument(FLAG.toString(), StringArgumentType.string())
                                                .suggests((ctx, builder) -> ISuggestionProvider.suggest(RegionDataManager.get().getFlagsIdsForDim(getDimCacheArgument(ctx)), builder))
                                                .executes(ctx -> removeFlag(ctx.getSource(), getDimCacheArgument(ctx), StringArgumentType.getString(ctx, FLAG.toString()))))))
                        .then(literal(ADD)
                                .then(literal(PLAYER)
                                        .then(Commands.argument(AFFILIATION.toString(), StringArgumentType.string())
                                                .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                                .then(Commands.argument(PLAYER.toString(), EntityArgument.player())
                                                        .executes(ctx -> addPlayer(ctx.getSource(), getPlayerArgument(ctx), getDimCacheArgument(ctx), getAffiliationArgument(ctx)))))

                                        .then(Commands.argument(AFFILIATION.toString(), StringArgumentType.string())
                                                .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                                .then(Commands.argument(PLAYER.toString(), EntityArgument.player())
                                                        .executes(ctx -> addPlayer(ctx.getSource(), getPlayerArgument(ctx), getDimCacheArgument(ctx), getAffiliationArgument(ctx))))))
                                .then(literal(TEAM)
                                        .then(Commands.argument(AFFILIATION.toString(), StringArgumentType.string())
                                                .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                                .then(Commands.argument(TEAM.toString(), TeamArgument.team())
                                                        .executes(ctx -> addTeam(ctx.getSource(), getTeamArgument(ctx), getDimCacheArgument(ctx), getAffiliationArgument(ctx)))))

                                        .then(Commands.argument(AFFILIATION.toString(), StringArgumentType.string())
                                                .suggests((ctx, builder) -> ISuggestionProvider.suggest(affiliationList, builder))
                                                .then(Commands.argument(TEAM.toString(), TeamArgument.team())
                                                        .executes(ctx -> addTeam(ctx.getSource(), getTeamArgument(ctx), getDimCacheArgument(ctx), getAffiliationArgument(ctx))))))
                                .then(literal(FLAG)
                                        .then(Commands.argument(FLAG.toString(), StringArgumentType.string())
                                                .suggests((ctx, builder) -> ISuggestionProvider.suggest(RegionFlag.getFlagNames(), builder))
                                                .executes(ctx -> addFlag(ctx.getSource(), getDimCacheArgument(ctx), StringArgumentType.getString(ctx, FLAG.toString())))))));
    }

    private static int createCuboidRegion(CommandSource src, String regionName, DimensionRegionCache dimCache, BlockPos pos1, BlockPos pos2, @Nullable ServerPlayerEntity owner) {
        sendCmdFeedback(src, new StringTextComponent("NOTE: Local regions are implemented, but the flags for local regions are not yet working, sorry!"));
        sendCmdFeedback(src, new StringTextComponent("NOTE: Region hierarchy (parents and children regions) is working but there are no checks in place to ensure a proper hierarchy."));
        sendCmdFeedback(src, new StringTextComponent("NOTE: These features will come soon in the next update! "));
        if (!regionName.matches(RegionArgumentType.VALID_NAME_PATTERN.pattern())) {
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.dim.info.region.create.name.invalid", regionName));
            return -1;
        }
        if (dimCache.contains(regionName)){
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.dim.info.region.create.name.exists", dimCache.dimensionKey(), regionName));
            return 1;
        }
        Set<String> defaultFlags = RegionConfig.getDefaultFlags();
        CuboidArea area = new CuboidArea(pos1, pos2);
        CuboidRegion region = new CuboidRegion(regionName, area, owner, dimCache.dimensionKey());
        defaultFlags.stream()
                .map(RegionFlag::fromId)
                .forEach(flag -> {
                    switch (flag.type) {
                        case BOOLEAN_FLAG:
                            region.addFlag(new BooleanFlag(flag));
                            break;
                        case LIST_FLAG:
                            throw new NotImplementedException("");
                        case INT_FLAG:
                            throw new NotImplementedException("");
                    }
                });
        dimCache.addRegion(region);
        sendCmdFeedback(src, new TranslationTextComponent("cli.msg.dim.info.region.create.success", buildRegionInfoLink(region)));
        return 0;
    }

    private static int createSphereRegion(CommandSource src, String regionName, DimensionRegionCache dimCache, BlockPos center, BlockPos outerPos, ServerPlayerEntity owner) {
        sendCmdFeedback(src, new StringTextComponent("NOTE: Local regions are implemented, but the flags for local regions are not yet working, sorry!"));
        sendCmdFeedback(src, new StringTextComponent("NOTE: Region hierarchy (parents and children regions) is working but there are no checks in place to ensure a proper hierarchy."));
        sendCmdFeedback(src, new StringTextComponent("NOTE: These features will come soon in the next update! "));
        if (!regionName.matches(RegionArgumentType.VALID_NAME_PATTERN.pattern())) {
            sendCmdFeedback(src, new TranslationTextComponent( "cli.msg.dim.info.region.create.name.invalid", regionName));
            return -1;
        }
        if (dimCache.contains(regionName)){
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.dim.info.region.create.name.exists", dimCache.dimensionKey(), regionName));
            return 1;
        }
        Set<String> defaultFlags = RegionConfig.getDefaultFlags();
        SphereArea area = new SphereArea(center, outerPos);
        SphereRegion region = new SphereRegion(regionName, area, owner, dimCache.dimensionKey());
        defaultFlags.stream()
                .map(RegionFlag::fromId)
                .forEach(flag -> {
                    switch (flag.type) {
                        case BOOLEAN_FLAG:
                            region.addFlag(new BooleanFlag(flag));
                            break;
                        case LIST_FLAG:
                            throw new NotImplementedException("");
                        case INT_FLAG:
                            throw new NotImplementedException("");
                    }
                });
        dimCache.addRegion(region);
        sendCmdFeedback(src, new TranslationTextComponent("cli.msg.dim.info.region.create.success", buildRegionInfoLink(region)));
        return 0;
    }

    // TODO:
    private static int createRegion(CommandSource source, String regionName, DimensionRegionCache dimCache, AreaType area) {
        sendCmdFeedback(source, "Not yet implemented");
        try {
            PlayerEntity player = source.getPlayerOrException();
            ItemStack maybeStick = player.getMainHandItem();
            // TODO: create a method which trhows exception on trying to get stick
            if (StickUtil.isVanillaStick(maybeStick)) {
                StickType stickType = StickUtil.getStickType(maybeStick);
                if (stickType == StickType.MARKER) {
                    CompoundNBT stickNBT = StickUtil.getStickNBT(maybeStick);
                    if (stickNBT != null) {
                        AbstractMarkableRegion region = RegionUtil.regionFrom(source.getPlayerOrException(), new MarkerStick(stickNBT), regionName);
                        // TODO
                    }
                }
            }
        } catch (CommandSyntaxException e) {
            YetAnotherWorldProtector.LOGGER.error(e);
        }
        return 0;
    }

    private static int attemptDeleteRegion(CommandSource src, DimensionRegionCache dim, IMarkableRegion region) {
        if (dim.contains(region.getName())) {
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.dim.region.remove.attempt", region.getName(), dim.dimensionKey().location()));
            return 0;
        }
        return 1;
    }

    private static int deleteRegion(CommandSource src, DimensionRegionCache dim, IMarkableRegion region) {
        if (dim.contains(region.getName())) {
            dim.removeRegion(region);
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.dim.region.remove.confirm", region.getName(), dim.dimensionKey().location()));
            return 0;
        }
        return 1;
    }

    public static int selectReferenceDim(CommandSource src, DimensionalRegion dim) throws CommandSyntaxException {
        RegionCommands.CommandSourceReferenceDims.put(src, dim.getDimensionKey());
        MessageUtil.sendCmdFeedback(src, new StringTextComponent("Selected dim '" + dim.getDimensionKey().location().toString() + "' as reference for region commands for '" + src.getTextName() + "'."));
        return 0;
    }

    private static int removeFlag(CommandSource src,  DimensionRegionCache dimCache, String flag) {
        if (dimCache != null) {
            RegistryKey<World> dim = dimCache.dimensionKey();
            dimCache.removeFlag(flag);
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.flags.removed", flag, dim.location().toString()));
            return 0;
        }
        return 1;
    }

    private static int addFlag(CommandSource src, DimensionRegionCache dimCache, String flag) {
        // FIXME: For now this works because we only have condition flags and no black/whitelist feature
        IFlag iflag = new BooleanFlag(flag, false);
        if (dimCache != null) {
            RegistryKey<World> dim = dimCache.dimensionKey();
            dimCache.addFlag(iflag);
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.flags.added", flag, dim.location().toString()));
            return 0;
        }
        return 1;
    }

    private static int removePlayer(CommandSource src, ServerPlayerEntity player, DimensionRegionCache dimCache, String affiliationType) {
        if (dimCache != null) {
            RegistryKey<World> dim = dimCache.dimensionKey();
            if (affiliationType.equals(MEMBER.toString())) {
                dimCache.removeMember(player);
            }
            if (affiliationType.equals(OWNER.toString())) {
                dimCache.removeOwner(player);
            }
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.dim.info.player.removed", affiliationType, player.getScoreboardName(), dim.location().toString()));
        }
        return 0;
    }

    private static int removeTeam(CommandSource src, ScorePlayerTeam team, DimensionRegionCache dimCache, String affiliationType) {
        if (dimCache != null) {
            RegistryKey<World> dim = dimCache.dimensionKey();
            if (affiliationType.equals(MEMBER.toString())) {
                dimCache.removeMember(team);
            }
            if (affiliationType.equals(OWNER.toString())) {
                dimCache.removeOwner(team);
            }
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.dim.info.player.removed", affiliationType, team.getName(), dim.location().toString()));
            return 0;
        }
        return 1;
    }

    // TODO: If works replace with switch and catch error
    private static int addPlayer(CommandSource src, ServerPlayerEntity player, DimensionRegionCache dimCache, String affiliationType) {
        if (dimCache != null) {
            RegistryKey<World> dim = dimCache.dimensionKey();
            if (affiliationType.equals(MEMBER.toString())) {
                dimCache.addMember(player);
            }
            if (affiliationType.equals(OWNER.toString())) {
                dimCache.addOwner(player);
            }
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.dim.info.player.added", player.getScoreboardName(), dim.location().toString(), affiliationType));
            return 0;
        }
        return 1;
    }

    private static int addTeam(CommandSource src, ScorePlayerTeam team, DimensionRegionCache dimCache, String affiliationType) {
        if (dimCache != null) {
            RegistryKey<World> dim = dimCache.dimensionKey();
            if (affiliationType.equals(MEMBER.toString())) {
                dimCache.addMember(team);
            }
            if (affiliationType.equals(OWNER.toString())) {
                dimCache.addOwner(team);
            }
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.dim.info.team.added", team.getName(), dim.location().toString(), affiliationType));
            return 0;
        }
        return 1;
    }


    // TODO: Check
    private static int promptDimensionFlagList(CommandSource src,  DimensionRegionCache dimCache) {
        List<IFlag> flags = dimCache.getDimensionalRegion().getFlags()
                .stream()
                // TODO: implement comparable for flags
                // .sorted()
                .collect(Collectors.toList());
        RegistryKey<World> dim = dimCache.dimensionKey();
        if (flags.isEmpty()) {
            sendCmdFeedback(src, new TranslationTextComponent("cli.msg.dim.info.flags.empty", dim.location().toString()));
            return 1;
        }
        // TODO: lang key
        // TODO Dim info link
        sendCmdFeedback(src, new TranslationTextComponent(TextFormatting.BOLD + "== Flags in dimension '" + dim.location().toString() + "' =="));
        flags.forEach(flag -> {
            IFormattableTextComponent removeFlagLink = new StringTextComponent(" - ")
                    .append(buildDimensionRemoveFlagLink(flag, dim))
                    .append(new StringTextComponent(" '" + flag.getFlagIdentifier() + "' "));

            sendCmdFeedback(src, removeFlagLink);
        });
        return 0;
    }

    private static int promptDimensionPlayerList(CommandSource src,  DimensionRegionCache dimCache, CommandConstants memberOrOwner) {
        if (dimCache != null) {
            DimensionalRegion dimRegion = dimCache.getDimensionalRegion();
            String playerLangKeyPart = memberOrOwner == OWNER ? "owner" : "member";
            String affiliationText = playerLangKeyPart.substring(0, 1).toUpperCase() + playerLangKeyPart.substring(1) + "s";
            // TODO Dim info link
            sendCmdFeedback(src, new TranslationTextComponent(TextFormatting.BOLD + "== " + affiliationText + " in dimension '" + dimRegion.getDimensionKey().location() + "' =="));
            sendCmdFeedback(src, buildTeamList(dimRegion, memberOrOwner));
            sendCmdFeedback(src, buildPlayerList(dimRegion, memberOrOwner));
            return 0;
        }
        return 1;
    }

    private static int setActiveState(CommandSource src, DimensionRegionCache dimCache, boolean activate) {
        if (dimCache != null) {
            dimCache.setDimState(activate);

            String langKey = "cli.msg.info.state." + (activate ? "activated" : "deactivated");
            sendCmdFeedback(src, new TranslationTextComponent(langKey, dimCache.getDimensionalRegion().getDimensionKey().location().toString()));
            return 0;
        }
        return 1;
    }

    // TODO: Check and extract to own method -> dimInfo uses this, too
    private static int promptDimensionRegionList(CommandSource source, DimensionRegionCache dimCache) {
        if (dimCache != null) {
            RegistryKey<World> dim =  dimCache.getDimensionalRegion().getDimensionKey();
            List<IMarkableRegion> regionsForDim = dimCache.regionsInDimension
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(IMarkableRegion::getName))
                    .collect(Collectors.toList());
            if (regionsForDim.isEmpty()) {
                sendCmdFeedback(source, new TranslationTextComponent("cli.msg.dim.info.regions.empty", dim.location().toString()));
                return -1;
            }
            // TODO Dim info link
            sendCmdFeedback(source, new TranslationTextComponent(TextFormatting.BOLD + "== Regions in dimension '" + dim.location().toString() + "' =="));
            // TODO: Pagination for more than x regions
            regionsForDim.forEach(region -> {
                IFormattableTextComponent regionRemoveLink = new StringTextComponent(" - ")
                        .append(buildDimSuggestRegionRemovalLink(region))
                        .append(" ")
                        .append(buildRegionInfoAndTpLink(region));
                sendCmdFeedback(source, regionRemoveLink);
            });
            return 0;
        }
        return 1;
    }

    /* Used for dimension info */
    private static void promptDimensionOwners(CommandSource src, DimensionalRegion dimRegion) {
        // [n player(s)] [+]
        PlayerContainer owners = dimRegion.getOwners();
        IFormattableTextComponent playersAddLink = buildDimAddPlayerLink(dimRegion, "cli.msg.dim.info.players.add",
                OWNER);
        IFormattableTextComponent players = owners.hasPlayers()
                ? buildDimPlayerListLink(dimRegion, owners, OWNER)
                : new TranslationTextComponent(owners.getPlayers().size() + " player(s)");
        players.append(playersAddLink);

        // [n team(s)] [+]
        IFormattableTextComponent teamAddLink = buildDimAddTeamLink(dimRegion, "cli.msg.dim.info.teams.add",
                OWNER);
        IFormattableTextComponent teams = owners.hasTeams()
                ? buildDimTeamListLink(dimRegion, owners, OWNER)
                : new TranslationTextComponent(owners.getTeams().size() + " teams(s)");
        teams.append(teamAddLink);

        // Owners: [n player(s)] [+], [n team(s)] [+]
        IFormattableTextComponent dimOwners = new TranslationTextComponent("cli.msg.dim.info.owners")
                .append(new StringTextComponent(": "))
                .append(players).append(new StringTextComponent(", "))
                .append(teams);
        sendCmdFeedback(src, dimOwners);
    }

    private static void promptDimensionMembers(CommandSource src, DimensionalRegion dimRegion) {
        // [n player(s)] [+]
        PlayerContainer members = dimRegion.getMembers();
        IFormattableTextComponent playersAddLink = buildDimAddPlayerLink(dimRegion, "cli.msg.dim.info.players.add",
                MEMBER);
        IFormattableTextComponent players = members.hasPlayers() ?
                buildDimPlayerListLink(dimRegion, members, MEMBER)
                // TODO lang-key
                : new TranslationTextComponent(members.getPlayers().size() + " player(s)");
        players.append(playersAddLink);

        // [n team(s)] [+]
        IFormattableTextComponent teamAddLink = buildDimAddTeamLink(dimRegion, "cli.msg.dim.info.teams.add",
                MEMBER);
        IFormattableTextComponent teams = members.hasTeams()
                ? buildDimTeamListLink(dimRegion, members, MEMBER)
                // TODO lang-key
                : new TranslationTextComponent(members.getTeams().size() + " teams(s)");
        teams.append(teamAddLink);

        // Members: [n player(s)] [+], [n team(s)] [+]
        IFormattableTextComponent dimMembers = new TranslationTextComponent("cli.msg.dim.info.members")
                .append(new StringTextComponent(": "))
                .append(players).append(new StringTextComponent(", "))
                .append(teams);
        sendCmdFeedback(src, dimMembers);
    }

    private static void promptDimensionFlags(CommandSource src, DimensionalRegion dimRegion) {
        IFormattableTextComponent dimFlagMessage = new TranslationTextComponent("cli.msg.dim.info.flags");
        IFormattableTextComponent flags = dimRegion.getFlags().isEmpty()
                // TODO lang-key
                ? new StringTextComponent(dimRegion.getFlags().size() + " flags(s)")
                : buildDimFlagListLink(dimRegion);
        dimFlagMessage.append(new StringTextComponent(": "))
                .append(flags)
                .append(buildAddDimFlagLink(dimRegion));
        sendCmdFeedback(src, dimFlagMessage);
    }

    private static void promptDimensionState(CommandSource src, AbstractRegion region, String command) {
        String onClickAction = region.isActive() ? "deactivate" : "activate";
        String hoverText = "cli.msg.info.state." + onClickAction;
        String linkText = "cli.msg.info.state.link." + (region.isActive() ? "activate" : "deactivate");
        TextFormatting color = region.isActive() ? TextFormatting.GREEN : TextFormatting.RED;
        IFormattableTextComponent stateLink = buildExecuteCmdComponent(linkText, hoverText, command, ClickEvent.Action.RUN_COMMAND, color);
        sendCmdFeedback(src, new TranslationTextComponent("cli.msg.info.state")
                .append(new StringTextComponent(": "))
                .append(stateLink));
    }

    private static int promptDimensionInfo(CommandSource src, DimensionRegionCache dimCache) {
        // Dimension info header
        DimensionalRegion dimRegion = dimCache.getDimensionalRegion();
        // TODO: header extraction to build uniform header by providing langkey and args
        IFormattableTextComponent dimInfoHeader = new StringTextComponent(TextFormatting.BOLD + "== Dimension ")
                .append(buildDimensionalInfoLink(dimRegion.getDimensionKey()))
                .append(new StringTextComponent(TextFormatting.BOLD + " information =="));
        sendCmdFeedback(src, dimInfoHeader);

        // Regions in dimension
        promptDimensionRegionList(src, dimCache);

        // Dimension owners & members
        promptDimensionOwners(src, dimRegion);
        promptDimensionMembers(src, dimRegion);

        // Flags: [n flag(s)] [+]
        promptDimensionFlags(src, dimRegion);

        // State: [activated]
        String command = "/" + CommandPermissionConfig.BASE_CMD + " " + DIMENSION + " " + dimRegion.getName() + " " + ENABLE + " " + !dimRegion.isActive();
        promptDimensionState(src, dimRegion, command);
        return 0;
    }
}
