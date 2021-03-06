package tk.ardentbot.commands.fun;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import tk.ardentbot.core.executor.Command;
import tk.ardentbot.core.misc.web.models.EightBallResponse;
import tk.ardentbot.utils.discord.GuildUtils;

import java.net.URLEncoder;

public class EightBall extends Command {
    public EightBall(CommandSettings commandSettings) {
        super(commandSettings);
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
        if (args.length == 1) {
            sendTranslatedMessage("Type something so the 8ball has something to respond to!", channel, user);
        } else {
            String query = message.getRawContent().replace(GuildUtils.getPrefix(guild) + args[0] + " ", "");
            try {
                String json = Unirest.get("https://8ball.delegator.com/magic/JSON/" + URLEncoder.encode(query)).asString().getBody();
                EightBallResponse eightBallResponse = GuildUtils.getShard(guild).gson.fromJson(json,
                        EightBallResponse.class);
                sendTranslatedMessage(eightBallResponse.getMagic().getAnswer(), channel, user);
            } catch (UnirestException e) {
                sendTranslatedMessage("I can't answer that question!", channel, user);
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setupSubcommands() {
    }
}