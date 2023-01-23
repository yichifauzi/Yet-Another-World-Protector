package de.z0rdak.yawp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.z0rdak.yawp.config.server.CommandPermissionConfig;
import de.z0rdak.yawp.util.CommandUtil;
import de.z0rdak.yawp.util.MessageUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.ClickEvent;

import static de.z0rdak.yawp.util.MessageUtil.buildExecuteCmdComponent;
import static de.z0rdak.yawp.util.MessageUtil.buildHelpHeader;
import static net.minecraft.util.text.TextFormatting.AQUA;
import static net.minecraft.util.text.TextFormatting.GREEN;

public class CommandRegistry {

    private CommandRegistry() {
    }

    public static void init(CommandDispatcher<CommandSource> commandDispatcher) {
        commandDispatcher.register(register());
    }

    public static LiteralArgumentBuilder<CommandSource> register() {
        return withSubCommands(Commands.literal(CommandPermissionConfig.BASE_CMD));
    }

    private static LiteralArgumentBuilder<CommandSource> withSubCommands(LiteralArgumentBuilder<CommandSource> baseCommand) {
        return baseCommand
                //.requires(CommandPermissionConfig::hasPermission)
                .executes(ctx -> promptHelp(ctx.getSource()))
                .then(CommandUtil.literal(CommandConstants.HELP)
                        .executes(ctx -> promptHelp(ctx.getSource())))
                //.then(literal(CommandConstants.SELECT)
                //        .then(Commands.argument(CommandConstants.DIMENSION.toString(), DimensionArgument.dimension())
                //                .executes(ctx -> DimensionCommands.selectReferenceDim(ctx.getSource(), getDimRegionArgument(ctx)))))
                .then(DimensionCommands.DIMENSION_COMMAND)
                .then(MarkerCommands.MARKER_COMMAND)
                .then(RegionCommands.REGION_COMMAND)
                //.then(FlagCommands.FLAG_COMMAND)
                //.then(RegionCommands.REGIONS_COMMAND)
                //.then(DimensionFlagCommands.DIMENSION_FLAGS_COMMAND);
                //.then(CommandExpand.EXPAND_COMMAND)
                //.then(CommandPlayer.PLAYER_COMMAND);
        ;
    }

    private static int promptHelp(CommandSource src) {
        MessageUtil.sendCmdFeedback(src, buildHelpHeader("cli.msg.help.header"));
        String command = CommandUtil.buildCommandStr(CommandConstants.DIMENSION.toString());
        IFormattableTextComponent cmdStr = new TranslationTextComponent("cli.msg.help.1", CommandPermissionConfig.BASE_CMD);
        MessageUtil.sendCmdFeedback(src, buildExecuteCmdComponent(
                new StringTextComponent("=> "),
                new TranslationTextComponent("help.tooltip.dim"),
                command, ClickEvent.Action.SUGGEST_COMMAND, GREEN).append(cmdStr));
        IFormattableTextComponent wikiText1 = new TranslationTextComponent("help.tooltip.info.wiki.1");
        IFormattableTextComponent wikiText2 = new TranslationTextComponent("help.tooltip.info.wiki.2");
        IFormattableTextComponent wikiText3 = new TranslationTextComponent("help.tooltip.info.wiki.3");
        IFormattableTextComponent wikiLinkHover = new TranslationTextComponent("help.tooltip.info.wiki.link.hover");
        IFormattableTextComponent wikiLink = new TranslationTextComponent("help.tooltip.info.wiki.link.text");
        IFormattableTextComponent wikiCopyToClipboardLink = buildExecuteCmdComponent(wikiLink, wikiLinkHover, "", ClickEvent.Action.OPEN_URL, AQUA);
        wikiText1.append("\n")
                .append(wikiText2)
                .append("\n")
                .append(wikiText3)
                .append(": ")
                .append(wikiCopyToClipboardLink);
        MessageUtil.sendCmdFeedback(src, wikiText1);
        return 0;
    }
}
