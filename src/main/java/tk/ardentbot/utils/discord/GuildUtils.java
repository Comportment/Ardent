package tk.ardentbot.utils.discord;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;
import tk.ardentbot.main.Ardent;
import tk.ardentbot.main.Shard;

import java.util.ArrayList;

import static tk.ardentbot.main.ShardManager.getShards;

public class GuildUtils {
    public static Guild getGuild(String id) {
        for (Shard shard : getShards()) {
            Guild temp = shard.jda.getGuildById(id);
            if (temp != null) return temp;
        }
        return null;
    }

    public static Shard getShard(int id) {
        for (Shard shard : getShards()) {
            if (shard.getId() == id) {
                return shard;
            }
        }
        return null;
    }

    public static Shard getShard(Guild guild) {
        if (guild == null) return null;
        long bitwise = Long.parseLong(guild.getId()) >> 22;
        long modulus = bitwise % Ardent.shardCount;
        int numbered = (int) modulus;
        return getShard(numbered);
    }

    public static Shard getShard(JDA jda) {
        for (Shard shard : getShards()) {
            if (shard.jda.equals(jda)) return shard;
        }
        return null;
    }

    public static void updatePrefix(String prefix, Guild guild) {
        getShard(guild).botPrefixData.set(guild, prefix);
    }

    private static ArrayList<String> getGuildIds() {
        ArrayList<String> guildIds = new ArrayList<>();
        for (Shard shard : getShards()) {
            shard.jda.getGuilds().forEach(guild -> guildIds.add(guild.getId()));
        }
        return guildIds;
    }

    public static String getPrefix(Guild guild) throws Exception {
        String prefix = getShard(guild).botPrefixData.getPrefix(guild);
        if (prefix != null) return prefix;
        else return "/";
    }


    public static boolean hasManageServerPermission(Member member) {
        return member.hasPermission(Permission.MANAGE_SERVER) || Ardent.developers.contains(member.getUser().getId());
    }

    public static ArrayList<String> getChannelNames(ArrayList<String> ids, Guild guild) {
        ArrayList<String> names = new ArrayList<>();
        ids.forEach(id -> {
            TextChannel channel = guild.getTextChannelById(id);
            if (channel != null) names.add(channel.getName());
        });
        return names;
    }

    public static TextChannel getTextChannelById(String channel) {
        for (Shard shard : getShards()) {
            TextChannel ch = shard.jda.getTextChannelById(channel);
            if (ch != null) return ch;
        }
        return null;
    }
}
