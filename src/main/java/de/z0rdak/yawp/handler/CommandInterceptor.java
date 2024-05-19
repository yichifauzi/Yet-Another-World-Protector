package de.z0rdak.yawp.handler;

import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.z0rdak.yawp.YetAnotherWorldProtector;
import de.z0rdak.yawp.commands.CommandConstants;
import de.z0rdak.yawp.commands.CommandSourceType;
import de.z0rdak.yawp.commands.CommandUtil;
import de.z0rdak.yawp.config.server.CommandPermissionConfig;
import de.z0rdak.yawp.core.region.GlobalRegion;
import de.z0rdak.yawp.core.region.IMarkableRegion;
import de.z0rdak.yawp.core.region.IProtectedRegion;
import de.z0rdak.yawp.managers.data.region.DimensionRegionCache;
import de.z0rdak.yawp.managers.data.region.RegionDataManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.z0rdak.yawp.commands.CommandConstants.*;
import static de.z0rdak.yawp.config.server.CommandPermissionConfig.*;
import static de.z0rdak.yawp.util.ChatComponentBuilder.buildRegionInfoLink;
import static de.z0rdak.yawp.util.MessageSender.sendCmdFeedback;
import static net.minecraft.ChatFormatting.RED;
import static net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE;

@Mod.EventBusSubscriber(modid = YetAnotherWorldProtector.MODID, bus = FORGE)
public class CommandInterceptor {

    private final static int CANCEL_CMD = 1;
    private final static int ALLOW_CMD = 0;

    /**
     * Handler for managing different command permissions.
     */
    @SubscribeEvent
    public static void handleModCommandPermission(CommandEvent event) {
        CommandContextBuilder<CommandSourceStack> cmdContext = event.getParseResults().getContext();
        List<String> nodeNames = cmdContext.getNodes().stream().map(node -> node.getNode().getName()).collect(Collectors.toList());
        CommandSourceStack src = cmdContext.getSource();
        try {

            CommandSourceType cmdSrcType = CommandSourceType.of(src);
            if (!hasModBaseCmd(nodeNames)) {
                return;
            }
            if (nodeNames.size() > 2) {
                YetAnotherWorldProtector.LOGGER.debug("Executed command: '" + event.getParseResults().getReader().getString() + "' by '" + cmdContext.getSource().getTextName() + "'.");
                String subCmd = nodeNames.get(1);
                int cancelExecutionResultCode = 0;
                switch (subCmd) {
                    case "local":
                        if (!nodeNames.contains(CommandConstants.LOCAL.toString())) {
                            cancelExecutionResultCode = 9;
                            break;
                        }
                        cancelExecutionResultCode = handleRegionCmdExecution(cmdContext, nodeNames, cmdSrcType);
                        break;
                    case "dim":
                        if (!nodeNames.contains(DIM.toString())) {
                            cancelExecutionResultCode = 9;
                            break;
                        }
                        cancelExecutionResultCode = handleDimCommandExecution(cmdContext, cmdSrcType);
                        break;
                    case "global":
                        if (!nodeNames.contains(GLOBAL.toString())) {
                            cancelExecutionResultCode = 9;
                            break;
                        }
                        cancelExecutionResultCode = verifyGlobalCommandPermission(cmdContext, cmdSrcType);
                        break;
                    case "flag":
                        if (!nodeNames.contains(FLAG.toString())) {
                            cancelExecutionResultCode = 9;
                            break;
                        }
                        cancelExecutionResultCode = verifyFlagCommandPermission(cmdContext, nodeNames, cmdSrcType);
                        break;
                    case "marker":
                        if (!nodeNames.contains(MARKER.toString())) {
                            cancelExecutionResultCode = 9;
                            break;
                        }
                        cancelExecutionResultCode = verifyMarkerCommandPermission(cmdContext, nodeNames, cmdSrcType);
                        break;
                }
                event.setCanceled(cancelExecutionResultCode != 0);
            }

        } catch (IllegalArgumentException e) {
            YetAnotherWorldProtector.LOGGER.error(e);
        }
    }

    /**
     * Verifies the permission for the given marker command. <br>
     * Syntax: /wp marker reset|give|create [...] <br>
     */
    private static int verifyMarkerCommandPermission(CommandContextBuilder<CommandSourceStack> cmdContext, List<String> nodeNames, CommandSourceType cmdSrcType) {
        try {
            boolean isMarkerSubCmd = checkSubCmdAtIndex(nodeNames, 2, RESET, GIVE, CREATE);
            if (!isMarkerSubCmd) {
                return ALLOW_CMD;
            }
            if (cmdSrcType == CommandSourceType.PLAYER) {
                boolean isCreateCmd = checkSubCmdAtIndex(nodeNames, 2, CREATE);
                // 0   1      2       3      4
                // wp marker create <name> >parent>
                boolean isParentArgProvided = nodeNames.size() >= 5 && nodeNames.get(4) != null;
                Player player = cmdContext.getSource().getPlayerOrException();
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(player.getCommandSenderWorld().dimension());
                boolean hasPermission = hasConfigPermission(cmdContext.getSource(), cmdSrcType);
                boolean hasRegionPermission = false;
                if (isCreateCmd) {
                    if (isParentArgProvided) {
                        ParsedArgument<CommandSourceStack, ?> commandSourceParsedArgument = cmdContext.getArguments().get(nodeNames.get(4));
                        if (commandSourceParsedArgument.getResult() instanceof String parentName) {
                            IMarkableRegion parent = dimCache.getRegion(parentName);
                            if (parent != null) {
                                hasRegionPermission = hasRegionPermission(parent, player, CommandUtil.OWNER);
                            }
                        }
                    } else { // assuming dimensional regions as parent
                        hasRegionPermission = hasRegionPermission(dimCache.getDimensionalRegion(), player, CommandUtil.OWNER);
                    }
                } else {
                    hasRegionPermission = hasRegionPermission(dimCache.getDimensionalRegion(), player, CommandUtil.OWNER);
                }
                hasPermission = hasPermission || hasRegionPermission;
                handlePermission(cmdContext.getSource(), hasPermission);
                return hasPermission ? ALLOW_CMD : CANCEL_CMD;
            }
            YetAnotherWorldProtector.LOGGER.error("A player is required to execute this command.");
            return CANCEL_CMD;
        } catch (CommandSyntaxException e) {
            YetAnotherWorldProtector.LOGGER.error(e);
            return CANCEL_CMD;
        }
    }


    /**
     * Verifies the permission for the given flag command. <br>
     * Syntax for Local Regions:        /wp flag local &lt;dim&gt; &lt;region&gt; &lt;flag&gt; enable|msg ... <br>
     * Syntax for Dimensional Regions:  /wp flag dim &lt;dim&gt; &lt;flag&gt; enable|msg ... <br>
     * Syntax for Global Regions:       /wp flag global &lt;flag&gt; enable|msg ... <br>
     * Note: Implementation can still be improved through generalization or fusing the flag command with the sub-flag command. <br>
     */
    private static int verifyFlagCommandPermission(CommandContextBuilder<CommandSourceStack> cmdContext, List<String> nodeNames, CommandSourceType cmdSrcType) {
        CommandSourceStack src = cmdContext.getSource();
        try {
            boolean isRegionTypeCmd = checkSubCmdAtIndex(nodeNames, 2, LOCAL, DIM, GLOBAL);
            if (!isRegionTypeCmd) {
                return ALLOW_CMD;
            }
            String regionTypeCmd = nodeNames.get(2);
            switch (regionTypeCmd) {
                case "local": {
                    IProtectedRegion region = checkValidLocalRegion(cmdContext);
                    if (region == null) {
                        return ALLOW_CMD;
                    }
                    Function<List<String>, Boolean> subCmdPermission = (nodes) -> {
                        //  0   1    2       3      4    5      6
                        // /wp flag local <dim> <region> <flag> enable|msg ...
                        int subCmdIdx = 6;
                        boolean isReadOnlyCmd = checkSubCmdAtIndex(nodes, subCmdIdx, INFO);
                        boolean isFlagShortCmd = nodes.size() == subCmdIdx;
                        return (isFlagShortCmd || isReadOnlyCmd) && isReadOnlyAllowed();
                    };
                    boolean hasPermission = hasCmdPermission(cmdContext, cmdSrcType, CommandUtil.OWNER, region, subCmdPermission);
                    handlePermission(src, region, hasPermission);
                    return hasPermission ? ALLOW_CMD : CANCEL_CMD;
                }
                case "dim": {
                    DimensionRegionCache dimCache = checkValidDimRegion(cmdContext);
                    if (dimCache == null) {
                        return ALLOW_CMD;
                    }
                    IProtectedRegion region = dimCache.getDimensionalRegion();
                    Function<List<String>, Boolean> subCmdPermission = (nodes) -> {
                        //  0   1    2     3    4    5          6
                        // /wp flag dim <dim> <flag> enable|msg ...
                        int subCmdIdx = 5;
                        boolean isReadOnlyCmd = checkSubCmdAtIndex(nodes, subCmdIdx, INFO);
                        boolean isFlagShortCmd = nodes.size() == subCmdIdx;
                        return (isFlagShortCmd || isReadOnlyCmd) && isReadOnlyAllowed();
                    };
                    boolean hasPermission = hasCmdPermission(cmdContext, cmdSrcType, CommandUtil.OWNER, region, subCmdPermission);
                    handlePermission(src, region, hasPermission);
                    return hasPermission ? ALLOW_CMD : CANCEL_CMD;
                }
                case "global": {
                    GlobalRegion region = RegionDataManager.get().getGlobalRegion();
                    Function<List<String>, Boolean> subCmdPermission = (nodes) -> {
                        //  0   1    2       3      4         5
                        // /wp flag global <flag> enable|msg ...
                        int subCmdIdx = 4;
                        boolean isReadOnlyCmd = checkSubCmdAtIndex(nodes, subCmdIdx, INFO);
                        boolean isFlagShortCmd = nodes.size() == subCmdIdx;
                        return (isFlagShortCmd || isReadOnlyCmd) && isReadOnlyAllowed();
                    };
                    boolean hasPermission = hasCmdPermission(cmdContext, cmdSrcType, CommandUtil.OWNER, region, subCmdPermission);
                    handlePermission(src, region, hasPermission);
                    return hasPermission ? ALLOW_CMD : CANCEL_CMD;
                }
                default:
                    break;
            }
            return ALLOW_CMD;
        } catch (CommandSyntaxException e) {
            YetAnotherWorldProtector.LOGGER.error(e);
            return CANCEL_CMD;
        }
    }

    /**
     * Verifies the permission for the given flag command. <br>
     * Syntax: /wp global info|clear|add|remove|list|state.
     */
    private static int verifyGlobalCommandPermission(CommandContextBuilder<CommandSourceStack> cmdContext, CommandSourceType cmdSrcType) {
        CommandSourceStack src = cmdContext.getSource();
        GlobalRegion region = RegionDataManager.get().getGlobalRegion();
        try {
            Function<List<String>, Boolean> subCmdPermission = (nodes) -> {
                //  0  1      2         3
                // /wp global info|list ...
                int subCmdIdx = 2;
                boolean isReadOnlyCmd = checkSubCmdAtIndex(nodes, subCmdIdx, INFO, LIST);
                boolean isExtendedInfoCmd = checkSubCmdAtIndex(nodes, subCmdIdx, STATE) && nodes.size() == subCmdIdx + 1;
                boolean isRegionShortCmd = nodes.size() == subCmdIdx;
                return (isRegionShortCmd || isReadOnlyCmd || isExtendedInfoCmd) && isReadOnlyAllowed();
            };
            boolean hasPermission = hasCmdPermission(cmdContext, cmdSrcType, CommandUtil.OWNER, region, subCmdPermission);
            handlePermission(src, region, hasPermission);
            return hasPermission ? ALLOW_CMD : CANCEL_CMD;
        } catch (CommandSyntaxException e) {
            YetAnotherWorldProtector.LOGGER.error(e);
            return CANCEL_CMD;
        }
    }

    public static int handleRegionCmdExecution(CommandContextBuilder<CommandSourceStack> cmdContext, List<String> nodeNames, CommandSourceType cmdSrcType) {
        CommandSourceStack src = cmdContext.getSource();
        IProtectedRegion region = checkValidLocalRegion(cmdContext);
        if (region == null) {
            return CANCEL_CMD;
        }
        try {
            /* TODO:
                Check if tp is outside region and inside parent region for permission to tp ?
                This is a valid concern, but it is not considered and up to the user to manage this for now.
            */
            int subCmdIndex = nodeNames.indexOf(AREA.toString());
            subCmdIndex = 4;
            Function<List<String>, Boolean> subCmdPermission = (nodes) -> {
                int subCmdIdx = nodes.indexOf(AREA.toString());
                subCmdIdx = 4;
                // nodes.size() > 5
                //  0   1      2      3      4    5
                // /wp local <dim> <region> area ...
                boolean isAreaWriteCmd = checkSubCmdAtIndex(nodes, subCmdIdx, AREA) && nodes.size() > subCmdIdx + 1;
                // nodes.size() == 6
                //  0   1      2      3      4    5     6
                // /wp local <dim> <region> area  tp [player]
                boolean isRegionTpCmd = isAreaWriteCmd && checkSubCmdAtIndex(nodes, subCmdIdx + 1, TELEPORT) && nodes.size() >= subCmdIdx + 2;
                // nodes.size() == 4 || (  nodes.size() == 5 + INFO || LIST  )
                //  0   1      2      3        4
                // /wp local <dim> <region> info|list ...
                boolean isReadOnlyCmd = checkSubCmdAtIndex(nodes, subCmdIdx, INFO, LIST);
                boolean isExtendedInfoCmd = checkSubCmdAtIndex(nodes, subCmdIdx, STATE, AREA) && nodes.size() == subCmdIdx + 1;
                boolean isRegionShortCmd = nodes.size() == subCmdIdx;
                boolean isReadOnlyAndAllowed = (isRegionShortCmd || isReadOnlyCmd || isExtendedInfoCmd) && isReadOnlyAllowed();
                boolean isRegionTpAndAllowed = isRegionTpCmd && CommandPermissionConfig.allowRegionTp();
                return isReadOnlyAndAllowed || isRegionTpAndAllowed;
            };
            // nodes.size() > 5
            //  0   1      2      3      4    5
            // /wp local <dim> <region> area ...
            boolean isAreaWriteCmd = checkSubCmdAtIndex(nodeNames, subCmdIndex, AREA) && nodeNames.size() > subCmdIndex + 1;
            //  0    1      2      3       4     5     6
            // /wp local <dim> <region> area set|expand ...
            // needs to be handled separately, because the player also needs permission to modify the region parent
            boolean isAreaSetCmd = isAreaWriteCmd && checkSubCmdAtIndex(nodeNames, subCmdIndex + 1, SET) && nodeNames.size() > subCmdIndex + 2;
            boolean isAreaExpandCmd = isAreaWriteCmd && checkSubCmdAtIndex(nodeNames, subCmdIndex + 1, EXPAND) && nodeNames.size() > subCmdIndex + 2;
            boolean isAreaModifyCmd = isAreaWriteCmd && (isAreaExpandCmd || isAreaSetCmd);
            boolean hasParentPermission = hasCmdPermission(cmdContext, cmdSrcType, CommandUtil.OWNER, region.getParent());
            if (isAreaModifyCmd && !hasParentPermission) {
                handlePermission(src, region.getParent(), false);
                return CANCEL_CMD;
            }
            boolean hasPermission = hasCmdPermission(cmdContext, cmdSrcType, CommandUtil.OWNER, region, subCmdPermission);
            handlePermission(src, region, hasPermission);
            return hasPermission ? ALLOW_CMD : CANCEL_CMD;
        } catch (CommandSyntaxException e) {
            YetAnotherWorldProtector.LOGGER.error(e);
            return CANCEL_CMD;
        }
    }

    public static int handleDimCommandExecution(CommandContextBuilder<CommandSourceStack> cmdContext, CommandSourceType cmdSrcType) {
        CommandSourceStack src = cmdContext.getSource();
        DimensionRegionCache dimCache = checkValidDimRegion(cmdContext);
        if (dimCache == null) {
            return CANCEL_CMD;
        }
        try {
            IProtectedRegion region = dimCache.getDimensionalRegion();
            Function<List<String>, Boolean> subCmdPermission = (nodes) -> {
                //  0   1    2       3      4
                // /wp dim <dim> info|list ...
                int subCmdIdx = 3;
                boolean isReadOnlyCmd = checkSubCmdAtIndex(nodes, subCmdIdx, INFO, LIST);
                boolean isExtendedInfoCmd = checkSubCmdAtIndex(nodes, subCmdIdx, STATE) && nodes.size() == subCmdIdx + 1;
                boolean isRegionShortCmd = nodes.size() == subCmdIdx;
                return (isRegionShortCmd || isReadOnlyCmd || isExtendedInfoCmd) && isReadOnlyAllowed();
            };
            boolean hasPermission = hasCmdPermission(cmdContext, cmdSrcType, CommandUtil.OWNER, region, subCmdPermission);
            handlePermission(src, region, hasPermission);
            return hasPermission ? ALLOW_CMD : CANCEL_CMD;
        } catch (CommandSyntaxException e) {
            YetAnotherWorldProtector.LOGGER.error(e);
            return CANCEL_CMD;
        }
    }

    private static boolean hasModBaseCmd(List<String> nodeNames) {
        return !nodeNames.isEmpty() && nodeNames.get(0) != null && nodeNames.get(0).equals(BASE_CMD);
    }

    @Nullable
    private static DimensionRegionCache checkValidDimRegion(CommandContextBuilder<CommandSourceStack> cmdContext) {
        ParsedArgument<CommandSourceStack, ?> dimParsedArgument = cmdContext.getArguments().get(DIM.toString());
        if (dimParsedArgument != null && dimParsedArgument.getResult() instanceof ResourceLocation dimResLoc) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimResLoc);
            DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(dim);
            if (dimCache == null) {
                sendCmdFeedback(cmdContext.getSource(), Component.literal("Dimension not found in region data").withStyle(RED));
                return null;
            }
            return dimCache;
        }
        return null;
    }

    @Nullable
    private static IProtectedRegion checkValidLocalRegion(CommandContextBuilder<CommandSourceStack> cmdContext) {
        ParsedArgument<CommandSourceStack, ?> regionArg = cmdContext.getArguments().get(CommandConstants.LOCAL.toString());
        if (regionArg != null && regionArg.getResult() instanceof String regionName) {
            ParsedArgument<CommandSourceStack, ?> dimParsedArgument = cmdContext.getArguments().get(DIM.toString());
            if (dimParsedArgument != null && dimParsedArgument.getResult() instanceof ResourceLocation dimResLoc) {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimResLoc);
                DimensionRegionCache dimCache = RegionDataManager.get().cacheFor(dim);
                if (!dimCache.contains(regionName)) {
                    sendCmdFeedback(cmdContext.getSource(), Component.literal("No region with name '" + regionName + "' defined in dim '" + dimCache.getDimensionalRegion().getName() + "'"));
                    return null;
                }
                IMarkableRegion region = dimCache.getRegion(regionName);
                if (region == null) {
                    sendCmdFeedback(cmdContext.getSource(), Component.literal("No region with name '" + regionName + "' defined in dim '" + dimCache.getDimensionalRegion().getName() + "'"));
                    return null;
                }
                return region;
            }
        }
        return null;
    }

    private static boolean checkSubCmdAtIndex(List<String> nodeNames, int index, CommandConstants subCmd, CommandConstants... subCmds) {
        List<String> subCmdList = Arrays.stream(subCmds).map(CommandConstants::toString).collect(Collectors.toList());
        subCmdList.add(subCmd.toString());
        return nodeNames.size() >= index + 1 && nodeNames.get(index) != null && subCmdList.stream().anyMatch(nodeNames.get(index)::equals);
    }

    private static void handlePermission(CommandSourceStack src, IProtectedRegion region, boolean hasPermission) {
        if (!hasPermission) {
            YetAnotherWorldProtector.LOGGER.info("'" + src.getTextName() + "' is not allowed to manage region '" + region.getName() + "'");
            sendCmdFeedback(src, Component.translatableWithFallback("cli.msg.info.region.modify.deny", "[%s] You don't have the permission to execute this command!", buildRegionInfoLink(region)));
        }
    }

    private static void handlePermission(CommandSourceStack src, boolean hasPermission) {
        if (!hasPermission) {
            YetAnotherWorldProtector.LOGGER.info("'" + src.getTextName() + "' is not allowed to execute this command");
            sendCmdFeedback(src, Component.translatableWithFallback("cli.msg.info.cmd.deny", "You don't have permission to execute this command!"));
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static boolean hasCmdPermission(CommandContextBuilder<CommandSourceStack> ctx, CommandSourceType cmdSrcType, String permissionGroup, IProtectedRegion region) throws CommandSyntaxException {
        switch (cmdSrcType) {
            case PLAYER: {
                Player player = ctx.getSource().getPlayerOrException();
                boolean hasConfigPermission = CommandPermissionConfig.hasConfigPermission(player);
                boolean hasRegionPermission = hasRegionPermission(region, player, permissionGroup);
                return (hasRegionPermission || hasConfigPermission);
            }
            case SERVER:
                return true;
            case COMMAND_BLOCK:
                return isCommandBlockExecutionAllowed();
            default:
                return false;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static boolean hasCmdPermission(CommandContextBuilder<CommandSourceStack> ctx, CommandSourceType cmdSrcType, String permissionGroup, IProtectedRegion region, Function<List<String>, Boolean> subCmdPermission) throws CommandSyntaxException {
        switch (cmdSrcType) {
            case PLAYER: {
                List<String> nodeNames = ctx.getNodes().stream().map(node -> node.getNode().getName()).collect(Collectors.toList());
                Player player = ctx.getSource().getPlayerOrException();
                boolean hasConfigPermission = CommandPermissionConfig.hasConfigPermission(player);
                boolean hasRegionPermission = hasRegionPermission(region, player, permissionGroup);
                boolean hasSubCmdPermission = subCmdPermission.apply(nodeNames);
                return (hasRegionPermission || hasConfigPermission) || hasSubCmdPermission;
            }
            case SERVER:
                return true;
            case COMMAND_BLOCK:
                return isCommandBlockExecutionAllowed();
            default:
                return false;
        }
    }
}
