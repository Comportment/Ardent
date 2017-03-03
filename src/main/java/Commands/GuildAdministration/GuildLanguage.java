package Commands.GuildAdministration;

import Backend.Commands.BotCommand;
import Backend.Commands.Subcommand;
import Backend.Translation.LangFactory;
import Backend.Translation.Language;
import Backend.Translation.Translation;
import Backend.Translation.TranslationResponse;
import Utils.GuildUtils;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;

import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static Main.Ardent.conn;
import static Main.Ardent.jda;

public class GuildLanguage extends BotCommand {
    public GuildLanguage(CommandSettings commandSettings) {
        super(commandSettings);
    }

    public Subcommand set;

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args, Backend.Translation.Language language) throws Exception {
        sendHelp(language, channel);
    }

    @Override
    public void setupSubcommands() {
        subcommands.add(new Subcommand(this, "view") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws Exception {
                sendTranslatedMessage(getTranslation("language", language, "currentlanguage").getTranslation().replace("{0}", "**" + LangFactory.getName(language) + "**"), channel);
            }
        });
        subcommands.add(new Subcommand(this, "list") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws Exception {
                if (args.length == 2) {
                    StringBuilder languages = new StringBuilder();
                    LangFactory.languages.forEach((lang) -> {
                        if (lang.getLanguageStatus() == Language.Status.MATURE || lang.getLanguageStatus() == Language.Status.MOST) {
                            languages.append("\n    > **" + LangFactory.getName(lang) + "**  ");
                        }
                    });
                    String reply = getTranslation("language", language, "checklanguages").getTranslation().replace("{0}", languages.toString()).replace("{2}", set.getName(language)).replace("{1}", GuildUtils.getPrefix(guild) + args[0]);
                    sendTranslatedMessage(reply, channel);
                }
                else
                    sendTranslatedMessage(getTranslation("other", language, "checksyntax").getTranslation().replace("{0}", GuildUtils.getPrefix(guild) + args[0]), channel);
            }
        });
        set = new Subcommand(this, "set") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws Exception {
                Member member = guild.getMember(user);
                if (member.hasPermission(Permission.MANAGE_SERVER)) {
                    if (args.length == 3) {
                        Language changeTo = LangFactory.getLanguage(args[2]);
                        if (changeTo != null) {
                            Statement statement = conn.createStatement();
                            statement.executeUpdate("UPDATE Guilds SET Language='" + changeTo.getIdentifier() + "' WHERE GuildID='" + guild.getId() + "'");
                            statement.close();
                            sendRetrievedTranslation(channel, "language", changeTo, "changedlanguage");
                        }
                        else sendRetrievedTranslation(channel, "language", language, "invalidlanguage");
                    }
                    else
                        sendTranslatedMessage(getTranslation("other", language, "checksyntax").getTranslation().replace("{0}", GuildUtils.getPrefix(guild) + args[0]), channel);
                }
                else sendRetrievedTranslation(channel, "other", language, "needmanageserver");
            }
        };
        subcommands.add(set);
        subcommands.add(new Subcommand(this, "statistics") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws Exception {
                ArrayList<Translation> translations = new ArrayList<>();
                translations.add(new Translation("language", "languageusages"));
                translations.add(new Translation("status", "guilds"));
                HashMap<Integer, TranslationResponse> responses = getTranslations(language, translations);
                Map<String, Integer> usages = GuildUtils.getLanguageUsages();
                int guilds = jda.getGuilds().size();
                DecimalFormat format = new DecimalFormat("##");
                StringBuilder sb = new StringBuilder();
                sb.append("**" + responses.get(0).getTranslation() + "**\n");
                usages.forEach((key, value) -> {
                    sb.append(" > **" + key + "**: " + format.format((value / guilds)) + "%\n");
                });
                sendTranslatedMessage(sb.toString(), channel);
            }
        });
    }
}
