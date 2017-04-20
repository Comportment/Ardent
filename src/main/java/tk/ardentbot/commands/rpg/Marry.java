package tk.ardentbot.commands.rpg;

import com.rethinkdb.net.Cursor;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import tk.ardentbot.Core.executor.Command;
import tk.ardentbot.Core.misc.logging.BotException;
import tk.ardentbot.Core.translate.Language;
import tk.ardentbot.rethink.models.Marriage;
import tk.ardentbot.utils.discord.UserUtils;

import java.util.HashMap;
import java.util.List;

import static tk.ardentbot.rethink.Database.connection;
import static tk.ardentbot.rethink.Database.r;

public class Marry extends Command {
    public Marry(CommandSettings commandSettings) {
        super(commandSettings);
    }

    static Marriage getMarriage(User user) {
        Cursor<HashMap> marriagesForUser = r.db("data").table("marriages").filter(row -> row.g("user_one").eq(user.getId()).or(row.g
                ("user_two").eq(user.getId()))).run(connection);
        if (marriagesForUser.hasNext()) return asPojo(marriagesForUser.next(), Marriage.class);
        else return null;
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws Exception {
        if (args.length == 1) {
            Marriage marriage = getMarriage(user);
            if (marriage == null) sendRetrievedTranslation(channel, "marry", language, "notmarried", user);
            else {
                sendEditedTranslation("marry", language, "marriedto", user, channel, UserUtils.getUserById(marriage.getUser_one())
                        .getName(), UserUtils.getUserById(marriage.getUser_two()).getName());
            }
        }
        else {
            List<User> mentionedUsers = message.getMentionedUsers();
            if (mentionedUsers.size() == 0) {
                sendRetrievedTranslation(channel, "other", language, "mentionuser", user);
                return;
            }
            User toMarryTo = mentionedUsers.get(0);
            Marriage marriage = getMarriage(user);
            if (marriage != null) {
                sendRetrievedTranslation(channel, "marry", language, "nopolygamy", user);
                return;
            }
            if (toMarryTo.isBot()) {
                sendRetrievedTranslation(channel, "marry", language, "cantmarrybot", user);
                return;
            }
            if (getMarriage(toMarryTo) != null) {
                sendEditedTranslation("marry", language, "thatpersonalreadymarried", user, channel, toMarryTo.getName());
                return;
            }
            if (toMarryTo.getId().equals(user.getId())) {
                sendRetrievedTranslation(channel, "marry", language, "cantmarryyourself", user);
                return;
            }
            sendEditedTranslation("marry", language, "requesttomarry", user, channel, toMarryTo.getAsMention(), user.getName());
            longInteractiveOperation(language, channel, message, toMarryTo, 90, replyMessage -> {
                String reply = replyMessage.getContent();
                if (reply.equalsIgnoreCase("yes")) {
                    Marriage m = getMarriage(toMarryTo);
                    if (m != null) {
                        sendRetrievedTranslation(channel, "marry", language, "nopolygamy", user);
                        return;
                    }
                    sendRetrievedTranslation(channel, "marry", language, "nowmarried", user);
                    r.db("data").table("marriages").insert(r.json(gson.toJson(new Marriage(user.getId(), toMarryTo.getId())))).run
                            (connection);
                }
                else if (reply.equalsIgnoreCase("no")) {
                    try {
                        sendEditedTranslation("marry", language, "rejected", user, channel, user.getName(), toMarryTo.getName());
                    }
                    catch (Exception e) {
                        new BotException(e);
                    }
                }
                else {
                    sendRetrievedTranslation(channel, "marry", language, "invalidresponsecancelling", user);
                }
            });
        }
    }

    @Override
    public void setupSubcommands() throws Exception {
    }
}
