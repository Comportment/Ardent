package tk.ardentbot.Core.Events;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

public class InteractiveOnMessage {
    private static final Timer timer = new Timer();
    /**
     * First param is the text channel id, second is the user id
     */
    public static ConcurrentHashMap<String, String> queuedInteractives = new ConcurrentHashMap<>();

    /**
     * Pair params are Pair(Channel ID, User ID) and then its corresponding message
     */
    public static HashMap<Message, TextChannel> lastMessages = new HashMap<>();

    @SubscribeEvent
    public void onMessage(GuildMessageReceivedEvent event) {
        if (lastMessages.size() + 1 > 1000) {
            Iterator<Message> keys = lastMessages.keySet().iterator();
            if (keys.hasNext()) {
                keys.next();
                keys.remove();
            }
        }
        lastMessages.put(event.getMessage(), event.getChannel());
    }
}