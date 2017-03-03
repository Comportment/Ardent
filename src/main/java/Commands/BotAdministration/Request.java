package Commands.BotAdministration;

import Backend.Commands.BotCommand;
import Backend.Translation.Language;
import Utils.GuildUtils;
import net.dv8tion.jda.core.entities.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import static Main.Ardent.jda;

public class Request extends BotCommand {
    private static ArrayList<RequestUtil> usersUnableToRequest = new ArrayList<>();

    public Request(CommandSettings commandSettings) {
        super(commandSettings);
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws Exception {
        String prefix = GuildUtils.getPrefix(guild);
        if (args.length == 1) {
            sendTranslatedMessage(getTranslation("request", language, "requesthelp").getTranslation().replace("{0}", prefix + args[0]), channel);
        }
        else {
            if (canRequest(user)) {
                String request = message.getRawContent().replace(prefix + args[0] + " ", "");
                TextChannel ideasChannel = jda.getTextChannelById("262810786186002432");
                ideasChannel.sendMessage("**Request** by " + user.getName() + "#" + user.getDiscriminator() + " (" + user.getId() + "): " + request).queue();
                usersUnableToRequest.add(new RequestUtil(Instant.now(), user));
                sendRetrievedTranslation(channel, "request", language, "successfullyrequested");
            }
            else sendTranslatedMessage(getRequestTime(user, language), channel);
        }
    }

    @Override
    public void setupSubcommands() {

    }

    private class RequestUtil {
        public Timer timer = new Timer();
        private Instant ableToRequest;
        private String id;

        public RequestUtil(Instant requestedAt, User user) {
            this.ableToRequest = requestedAt.plusSeconds(60 * 5);
            this.id = user.getId();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    usersUnableToRequest.remove(RequestUtil.this);
                }
            }, ableToRequest.getEpochSecond() - requestedAt.getEpochSecond());
            usersUnableToRequest.add(this);
        }
    }

    public boolean canRequest(User user) {
        for (RequestUtil r : usersUnableToRequest) {
            if (r.id.equalsIgnoreCase(user.getId())) return false;
        }
        return true;
    }

    public String getRequestTime(User user, Language language) throws Exception {
        for (RequestUtil r : usersUnableToRequest) {
            if (r.id.equalsIgnoreCase(user.getId())) {
                int minutes = (int) ((r.ableToRequest.getEpochSecond() - Instant.now().getEpochSecond()) / 60);
                return Request.this.getTranslation("request", language, "requestin").getTranslation().replace("{0}", String.valueOf(minutes));
            }
        }
        return null;
    }
}
