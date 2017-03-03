package Commands.BotInfo;

import Backend.Commands.BotCommand;
import Backend.Translation.Language;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;

public class TranslateForArdent extends BotCommand {
    public TranslateForArdent(CommandSettings commandSettings) {
        super(commandSettings);
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws Exception {
        sendTranslatedMessage("Ardent is looking for translators! You must be **fluent** in both English and your native languages. Message Adam#9261 if you want " +
                "to join our translation efforts or join our server @ https://discord.gg/rfGSxNA", channel);
    }

    @Override
    public void setupSubcommands() {}
}
