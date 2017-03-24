package tk.ardentbot.BotCommands.GuildAdministration;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import tk.ardentbot.Core.CommandExecution.Command;
import tk.ardentbot.Core.CommandExecution.Subcommand;
import tk.ardentbot.Core.Translation.Language;
import tk.ardentbot.Utils.Discord.GuildUtils;
import tk.ardentbot.Utils.SQL.DatabaseAction;

public class Prefix extends Command {
    public Prefix(CommandSettings commandSettings) {
        super(commandSettings);
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language
            language) throws Exception {
        sendHelp(language, channel, guild, user, this);
    }

    @Override
    public void setupSubcommands() {
        subcommands.add(new Subcommand(this, "view") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                sendTranslatedMessage(getTranslation("prefix", language, "viewprefix").getTranslation().replace
                        ("{0}", GuildUtils.getPrefix(guild)), channel, user);
            }
        });
        subcommands.add(new Subcommand(this, "change") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                if (args.length > 2) {
                    if (guild.getMember(user).hasPermission(Permission.MANAGE_SERVER)) {
                        String newPrefix = message.getRawContent().replace(GuildUtils.getPrefix(guild) + args[0] + " " +
                                "" + args[1] + " ", "");
                        if (newPrefix.length() == message.getRawContent().length()) {
                            newPrefix = message.getRawContent().replace("/" + args[0] + " " + args[1] + " ", "");
                        }
                        GuildUtils.updatePrefix(newPrefix, guild);
                        new DatabaseAction("UPDATE Guilds SET Prefix=? WHERE GuildID=?").set(newPrefix).set(guild
                                .getId()).update();
                        sendTranslatedMessage(getTranslation("prefix", language, "successfullyupdated")
                                .getTranslation().replace("{0}", newPrefix), channel, user);
                    }
                    else {
                        sendRetrievedTranslation(channel, "other", language, "needmanageserver", user);
                    }
                }
                else {
                    sendRetrievedTranslation(channel, "prefix", language, "mustincludeargument", user);
                }
            }
        });
    }
}
