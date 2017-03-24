package tk.ardentbot.BotCommands.Fun;

import com.github.vbauer.yta.service.YTranslateApiImpl;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import tk.ardentbot.Core.CommandExecution.Command;
import tk.ardentbot.Core.CommandExecution.Subcommand;
import tk.ardentbot.Core.Translation.Language;
import tk.ardentbot.Utils.Discord.GuildUtils;

import java.util.ArrayList;
import java.util.Arrays;

public class Translate extends Command {
    public static YTranslateApiImpl translateApi;
    ArrayList<String> languages = new ArrayList<>(
            Arrays.asList(
                    "en",
                    "fr",
                    "it",
                    "es",
                    "ru",
                    "el",
                    "sv",
                    "pt",
                    "nl",
                    "et",
                    "tr",
                    "pl",
                    "de",
                    "uk"
            )
    );

    public Translate(CommandSettings commandSettings) {
        super(commandSettings);
        translateApi = new YTranslateApiImpl
                ("trnsl.1.1.20170227T013942Z.6878bfdf518abdf6.a6574733436345112da24eb08e7ee1ef2a0d6a97");
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language
            language) throws Exception {
        sendHelp(language, channel, guild, user, this);
    }

    @Override
    public void setupSubcommands() {
        languages.forEach(language -> {
            subcommands.add(new Subcommand(this, language) {
                @Override
                public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                                   Language language) throws Exception {
                    if (args.length == 2) {
                        sendRetrievedTranslation(channel, "translate", language, "includetext", user);
                    }
                    else {
                        String query = message.getRawContent().replace(GuildUtils.getPrefix(guild) + args[0] + " " +
                                args[1] + " ", "");
                        sendTranslatedMessage(translateApi.translationApi().translate(query, com.github.vbauer.yta
                                .model.Language.of(args[1])).text(), channel, user);
                    }
                }
            });
        });


    }
}
