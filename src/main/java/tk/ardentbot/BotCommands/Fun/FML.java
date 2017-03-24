package tk.ardentbot.BotCommands.Fun;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import tk.ardentbot.Core.CommandExecution.Command;
import tk.ardentbot.Core.Translation.Language;

import java.io.IOException;

public class FML extends Command {
    public FML(CommandSettings commandSettings) {
        super(commandSettings);
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws Exception {
        channel.sendTyping().queue();
        try {
            String title = FML.this.getTranslation("fml", language, "title").getTranslation();
            Document doc = Jsoup.connect("http://fmylife.com/random").userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6").get();
            Elements FML = doc.getElementsByTag("p").get(0).getElementsByTag("a");
            String fmylife = FML.get(0).getAllElements().get(0).text().replace(" FML", "");
            sendTranslatedMessage(title + ": " + fmylife, channel, user);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setupSubcommands() {
    }
}
