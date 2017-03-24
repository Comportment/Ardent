package tk.ardentbot.BotCommands.BotInfo;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import tk.ardentbot.BotCommands.Music.Music;
import tk.ardentbot.Core.CommandExecution.Command;
import tk.ardentbot.Core.Translation.Language;
import tk.ardentbot.Core.Translation.Translation;
import tk.ardentbot.Core.Translation.TranslationResponse;
import tk.ardentbot.Main.Shard;
import tk.ardentbot.Utils.Discord.GuildUtils;
import tk.ardentbot.Utils.Discord.InternalStats;
import tk.ardentbot.Utils.Discord.MessageUtils;
import tk.ardentbot.Utils.Discord.UsageUtils;
import tk.ardentbot.Utils.Tuples.Pair;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import static tk.ardentbot.Main.ShardManager.getShards;

public class Status extends Command {
    public static ConcurrentHashMap<String, Integer> commandsByGuild = new ConcurrentHashMap<>();

    public Status(CommandSettings commandSettings) {
        super(commandSettings);
    }

    public static int getVoiceConnections() {
        int counter = 0;
        for (Shard shard : getShards()) {
            for (Guild guild : shard.jda.getGuilds()) {
                if (guild.getAudioManager().isConnected()) counter++;
            }
        }
        return counter;
    }

    public static int getUserAmount() {
        int amount = 0;
        for (Shard shard : getShards()) {
            amount += shard.jda.getUsers().size();
        }
        return amount;
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language
            language) throws Exception {
        Shard shard = GuildUtils.getShard(guild);
        double totalRAM = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        double usedRAM = totalRAM - Runtime.getRuntime().freeMemory() / 1024 / 1024;

        StringBuilder devUsernames = new StringBuilder();
        devUsernames.append("Adam#9261, Akio Nakao#7507");

        Translation title = new Translation("status", "title");
        Translation botstatus = new Translation("status", "botstatus");
        Translation loadedcommands = new Translation("status", "loadedcommands");
        Translation receivedmessages = new Translation("status", "receivedmessages");
        Translation commandsreceived = new Translation("status", "commandsreceived");
        Translation guilds = new Translation("status", "guilds");
        Translation audioConnections = new Translation("status", "currentaudioconnections");
        Translation cpu = new Translation("status", "cpu");
        Translation ram = new Translation("status", "ram");
        Translation developers = new Translation("status", "developers");
        Translation site = new Translation("status", "site");
        Translation botHelp = new Translation("help", "bothelp");
        ArrayList<Translation> translationQueries = new ArrayList<>();
        translationQueries.add(title);
        translationQueries.add(botstatus);
        translationQueries.add(loadedcommands);
        translationQueries.add(receivedmessages);
        translationQueries.add(commandsreceived);
        translationQueries.add(guilds);
        translationQueries.add(audioConnections);
        translationQueries.add(cpu);
        translationQueries.add(ram);
        translationQueries.add(developers);
        translationQueries.add(site);
        translationQueries.add(botHelp);

        InternalStats internalStats = InternalStats.collect();

        DecimalFormat formatter = new DecimalFormat("#,###");
        String cmds = formatter.format(internalStats.getCommandsReceived());

        Pair<Integer, Integer> musicStats = Music.getMusicStats();

        HashMap<Integer, TranslationResponse> translations = getTranslations(language, translationQueries);

        EmbedBuilder embedBuilder = MessageUtils.getDefaultEmbed(guild, user, this);

        embedBuilder.setAuthor(translations.get(0).getTranslation(), "https://ardentbot.tk", shard.bot
                .getAvatarUrl());
        embedBuilder.setThumbnail("https://a.dryicons.com/images/icon_sets/polygon_icons/png/256x256/computer.png");

        embedBuilder.addField(translations.get(1).getTranslation(), ":thumbsup:", true);
        embedBuilder.addField(translations.get(2).getTranslation(), String.valueOf(shard.factory
                .getLoadedCommandsAmount()), true);

        embedBuilder.addField(translations.get(3).getTranslation(), String.valueOf(internalStats.getMessagesReceived
                ()), true);
        embedBuilder.addField(translations.get(4).getTranslation(), cmds, true);

        embedBuilder.addField(translations.get(5).getTranslation(), String.valueOf(internalStats.getGuilds()), true);
        embedBuilder.addField(translations.get(6).getTranslation(), String.valueOf(musicStats.getK()), true);

        embedBuilder.addField("Queue", String.valueOf(musicStats.getV()), true);
        embedBuilder.addField(translations.get(7).getTranslation(), UsageUtils.getProcessCpuLoad() + "%", true);

        embedBuilder.addField(translations.get(8).getTranslation(), usedRAM + " / " + totalRAM + " MB", true);
        embedBuilder.addField(translations.get(9).getTranslation(), devUsernames.toString(), true);

        embedBuilder.addField(translations.get(10).getTranslation(), "https://ardentbot.tk", true);
        embedBuilder.addField(translations.get(11).getTranslation(), "https://ardentbot.tk/guild", true);

        sendEmbed(embedBuilder, channel, user);
    }

    @Override
    public void setupSubcommands() {}
}
