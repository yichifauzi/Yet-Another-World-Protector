package de.z0rdak.yawp.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.z0rdak.yawp.config.server.CommandPermissionConfig;
import de.z0rdak.yawp.util.CommandUtil;
import de.z0rdak.yawp.util.MessageUtil;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.MutableText;
import net.minecraft.text.TranslatableTextContent;

import static de.z0rdak.yawp.util.MessageUtil.buildExecuteCmdComponent;
import static de.z0rdak.yawp.util.MessageUtil.buildHeader;
import static net.minecraft.util.Formatting.AQUA;
import static net.minecraft.util.Formatting.GREEN;

public class CommandRegistry {

    private CommandRegistry() {
    }

    public static void init(CommandDispatcher<ServerCommandSource> commandDispatcher, String modRootCmd) {
        commandDispatcher.register(buildCommands(modRootCmd));
    }

    public static LiteralArgumentBuilder<ServerCommandSource> buildCommands(String baseCmd) {
        return withSubCommands(CommandManager.literal(baseCmd));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> withSubCommands(LiteralArgumentBuilder<ServerCommandSource> baseCommand) {
        return baseCommand
                .executes(ctx -> promptHelp(ctx.getSource()))
                .then(CommandUtil.literal(CommandConstants.HELP)
                        .executes(ctx -> promptHelp(ctx.getSource())))
                .then(DimensionCommands.DIMENSION_COMMAND)
                .then(MarkerCommands.MARKER_COMMAND)
                .then(RegionCommands.REGION_COMMAND);
    }

    private static int promptHelp(ServerCommandSource src) {
        MessageUtil.sendCmdFeedback(src, buildHeader("cli.msg.help.header"));
        String command = CommandUtil.buildCommandStr(CommandConstants.DIM.toString());
        MutableText cmdStr = MutableText.of(new TranslatableTextContent("cli.msg.help.1", CommandPermissionConfig.BASE_CMD));
        MessageUtil.sendCmdFeedback(src, buildExecuteCmdComponent(
                MutableText.of(new LiteralTextContent("=> ")),
                MutableText.of(new TranslatableTextContent("help.tooltip.dim")),
                command, ClickEvent.Action.SUGGEST_COMMAND, GREEN).append(cmdStr));
        MutableText wikiText1 = MutableText.of(new TranslatableTextContent("help.tooltip.info.wiki.1"));
        MutableText wikiText2 = MutableText.of(new TranslatableTextContent("help.tooltip.info.wiki.2"));
        MutableText wikiText3 = MutableText.of(new TranslatableTextContent("help.tooltip.info.wiki.3"));
        MutableText wikiLinkHover = MutableText.of(new TranslatableTextContent("help.tooltip.info.wiki.link.hover"));
        MutableText wikiLink = MutableText.of(new TranslatableTextContent("help.tooltip.info.wiki.link.text"));
        MutableText wikiCopyToClipboardLink = buildExecuteCmdComponent(wikiLink, wikiLinkHover, "", ClickEvent.Action.OPEN_URL, AQUA);
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
