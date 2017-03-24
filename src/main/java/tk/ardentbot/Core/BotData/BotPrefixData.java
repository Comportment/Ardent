package tk.ardentbot.Core.BotData;

import net.dv8tion.jda.core.entities.Guild;
import tk.ardentbot.Utils.SQL.DatabaseAction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

public class BotPrefixData {
    private HashMap<String, String> guildPrefixes = new HashMap<>();

    public BotPrefixData() throws SQLException {
        DatabaseAction getPrefixes = new DatabaseAction("SELECT * FROM Guilds");
        ResultSet prefixes = getPrefixes.request();
        while (prefixes.next()) {
            guildPrefixes.put(prefixes.getString("GuildID"), prefixes.getString("Prefix"));
        }
        getPrefixes.close();
    }

    public String getPrefix(Guild guild) {
        return guildPrefixes.get(guild.getId());
    }

    public void set(String guildId, String language) {
        guildPrefixes.putIfAbsent(guildId, language);
    }

    public void set(Guild guild, String prefix) {
        guildPrefixes.put(guild.getId(), prefix);
    }

    public void remove(Guild guild) {
        guildPrefixes.remove(guild.getId());
    }
}
