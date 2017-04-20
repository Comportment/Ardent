package tk.ardentbot.commands.music;

import com.rethinkdb.net.Cursor;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.wrapper.spotify.methods.RecommendationsRequest;
import com.wrapper.spotify.methods.TrackRequest;
import com.wrapper.spotify.methods.TrackSearchRequest;
import com.wrapper.spotify.models.Page;
import com.wrapper.spotify.models.Track;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.exceptions.PermissionException;
import net.dv8tion.jda.core.managers.AudioManager;
import org.apache.commons.lang.WordUtils;
import tk.ardentbot.core.executor.BaseCommand;
import tk.ardentbot.core.executor.Command;
import tk.ardentbot.core.executor.Subcommand;
import tk.ardentbot.core.misc.logging.BotException;
import tk.ardentbot.core.translation.Language;
import tk.ardentbot.core.translation.Translation;
import tk.ardentbot.core.translation.TranslationResponse;
import tk.ardentbot.main.Shard;
import tk.ardentbot.main.ShardManager;
import tk.ardentbot.rethink.models.MusicSettingsModel;
import tk.ardentbot.utils.StringUtils;
import tk.ardentbot.utils.discord.GuildUtils;
import tk.ardentbot.utils.discord.MessageUtils;
import tk.ardentbot.utils.discord.UserUtils;
import tk.ardentbot.utils.javaAdditions.Pair;
import tk.ardentbot.utils.rpg.EntityGuild;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static tk.ardentbot.main.Ardent.spotifyApi;
import static tk.ardentbot.rethink.Database.connection;
import static tk.ardentbot.rethink.Database.r;

@SuppressWarnings("Duplicates")
public class Music extends Command {
    public Music(CommandSettings commandSettings, Shard shard) {
        super(commandSettings);
    }

    public static Pair<Integer, Integer> getMusicStats() {
        int playingGuilds = 0;
        int queueLength = 0;
        for (Shard shard : ShardManager.getShards()) {
            for (Guild guild : shard.jda.getGuilds()) {
                GuildMusicManager guildMusicManager = getGuildAudioPlayer(guild, null, shard);
                ArdentMusicManager manager = guildMusicManager.scheduler.manager;
                if (guildMusicManager.player.getPlayingTrack() != null) {
                    playingGuilds++;
                    queueLength++;
                    queueLength += manager.getQueue().size();
                }
            }
        }
        return new Pair<>(playingGuilds, queueLength);
    }

    public static synchronized GuildMusicManager getGuildAudioPlayer(Guild guild, MessageChannel channel, Shard shard) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = shard.musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(shard.playerManager, channel);
            guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
            shard.musicManagers.put(guildId, musicManager);
        }
        else {
            ArdentMusicManager ardentMusicManager = musicManager.scheduler.manager;
            if (ardentMusicManager.getChannel() == null) {
                ardentMusicManager.setChannel(channel);
            }
        }
        return musicManager;
    }

    public static synchronized GuildMusicManager getGuildAudioPlayer(Guild guild, MessageChannel channel) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = GuildUtils.getShard(guild).musicManagers.get(guildId);
        if (musicManager == null) {
            musicManager = new GuildMusicManager(GuildUtils.getShard(guild).playerManager, channel);
            guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
            GuildUtils.getShard(guild).musicManagers.put(guildId, musicManager);
        }
        else {
            ArdentMusicManager ardentMusicManager = musicManager.scheduler.manager;
            if (ardentMusicManager.getChannel() == null) {
                ardentMusicManager.setChannel(channel);
            }
        }
        return musicManager;
    }

    private static void play(User user, Guild guild, VoiceChannel channel, GuildMusicManager musicManager, AudioTrack
            track, TextChannel textChannel) {
        if (guild.getAudioManager().getConnectedChannel() == null) {
            guild.getAudioManager().openAudioConnection(channel);
        }
        musicManager.scheduler.manager.addToQueue(new ArdentTrack(user.getId(), textChannel, track));
    }

    private static boolean shouldContinue(User user, Language language, Guild guild, TextChannel channel, int
            numberOfTracks) throws Exception {
        if (guild.getMembers().size() < 150) {
            int trackAmount = getGuildAudioPlayer(guild, channel).scheduler.manager.getQueue().size();
            if (trackAmount + numberOfTracks >= 100) {
                GuildUtils.getShard(guild).help.sendRetrievedTranslation(channel, "music", language,
                        "cannotqueuemorethan20", user);
                return false;
            }
            else return true;
        }
        else return true;
    }

    private static boolean shouldContinue(User user, Language language, Guild guild, TextChannel channel, AudioTrack
            track) throws Exception {
        if (guild.getMembers().size() < 150) {
            long minutesDuration = track.getDuration() / 1000 / 60;
            if (minutesDuration > 15 && getHours(track) > 0) {
                GuildUtils.getShard(guild).help.sendRetrievedTranslation(channel, "music", language,
                        "cannotplaylongerthan15", user);
                return false;
            }
            else {
                return shouldContinue(user, language, guild, channel, 1);
            }
        }
        else return true;
    }

    static void loadAndPlay(Message message, User user, Command command, Language language, final TextChannel channel,
                            String trackUrl, final VoiceChannel voiceChannel, boolean search) {
        if (trackUrl.contains("spotify.com")) {
            String[] parsed = trackUrl.split("/track/");
            if (parsed.length == 2) {
                final TrackRequest request = spotifyApi.getTrack(parsed[1]).build();
                try {
                    trackUrl = request.get().getName();
                }
                catch (Exception e) {
                    new BotException(e);
                }
            }
        }
        Guild guild = channel.getGuild();
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild(), channel);
        String finalTrackUrl = trackUrl;
        GuildUtils.getShard(guild).playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                if (!UserUtils.hasTierTwoPermissions(user) && !EntityGuild.get(guild).isPremium()) {
                    try {
                        if (!shouldContinue(user, language, guild, channel, track)) {
                            return;
                        }
                    }
                    catch (Exception e) {
                        new BotException(e);
                    }
                }
                try {
                    command.sendTranslatedMessage(command.getTranslation("music", language, "addingsong")
                                    .getTranslation().replace("{0}", track.getInfo().title) + " " + getDuration(track),
                            channel, user);
                }
                catch (Exception e) {
                    new BotException(e);
                }
                play(user, guild, voiceChannel, musicManager, track, channel);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<AudioTrack> tracks = playlist.getTracks();
                if (playlist.isSearchResult()) {
                    try {
                        AudioTrack[] possible = playlist.getTracks().subList(0, 5).toArray(new AudioTrack[5]);
                        ArrayList<String> names = new ArrayList<>();
                        for (AudioTrack audioTrack : possible) {
                            names.add(audioTrack.getInfo().title);
                        }
                        command.sendEmbed(command.chooseFromList(command.getTranslation("music", language, "choosesong").getTranslation()
                                , guild, language, user,
                                command, names.toArray(new String[5])), channel, user);
                        interactiveOperation(language, channel, message, selectionMessage -> {
                            try {
                                AudioTrack selected = possible[Integer.parseInt(selectionMessage.getContent()) - 1];
                                if (!UserUtils.hasTierTwoPermissions(user) && !EntityGuild.get(guild).isPremium()) {
                                    try {
                                        if (!shouldContinue(user, language, guild, channel, selected)) {
                                            return;
                                        }
                                    }
                                    catch (Exception e) {
                                        new BotException(e);
                                    }
                                }
                                play(user, guild, voiceChannel, musicManager, selected, channel);
                                command.sendTranslatedMessage(command.getTranslation("music", language, "addingsong")
                                        .getTranslation().replace("{0}", selected.getInfo().title) + " " + getDuration
                                        (selected), channel, user);

                            }
                            catch (Exception e) {
                                command.sendRetrievedTranslation(channel, "tag", language, "invalidarguments", user);
                            }
                        });
                    }
                    catch (Exception e) {
                        new BotException(e);
                    }
                }
                else {
                    if (!UserUtils.hasTierTwoPermissions(user) && !EntityGuild.get(guild).isPremium()) {
                        try {
                            if (!shouldContinue(user, language, guild, channel, 1)) {
                                return;
                            }
                        }
                        catch (Exception e) {
                            new BotException(e);
                        }
                    }
                    try {
                        command.sendTranslatedMessage(command.getTranslation("music", language, "playlist")
                                .getTranslation().replace("{0}", String.valueOf(tracks.size())), channel, user);
                    }
                    catch (Exception e) {
                        new BotException(e);
                    }
                    for (AudioTrack track : tracks) {
                        play(user, guild, voiceChannel, musicManager, track, channel);
                    }
                }
            }

            @Override
            public void noMatches() {
                if (!search) {
                    loadAndPlay(message, user, command, language, channel, "ytsearch: " + finalTrackUrl, voiceChannel, true);
                }
                else {
                    try {
                        command.sendRetrievedTranslation(channel, "music", language, "nosongfound", user);
                    }
                    catch (Exception e) {
                        new BotException(e);
                    }
                }
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                try {
                    command.sendRetrievedTranslation(channel, "music", language, "notabletoplay", user);
                }
                catch (Exception e) {
                    new BotException(e);
                }
            }
        });
    }

    static VoiceChannel joinChannel(Guild guild, Member user, Language language, Command command, AudioManager
            audioManager, MessageChannel channel) throws Exception {
        GuildVoiceState voiceState = user.getVoiceState();
        if (voiceState.inVoiceChannel()) {
            VoiceChannel voiceChannel = voiceState.getChannel();
            Member bot = guild.getMember(GuildUtils.getShard(guild).bot);
            if (bot.hasPermission(voiceChannel, Permission.VOICE_CONNECT)) {
                try {
                    audioManager.openAudioConnection(voiceChannel);
                    command.sendTranslatedMessage(command.getTranslation("music", language, "connectedto").getTranslation()
                            .replace("{0}", voiceChannel.getName()), channel, user.getUser());
                }
                catch (PermissionException e) {
                    command.sendRetrievedTranslation(channel, "music", language, "unabletojoinvc", user.getUser());

                }
            }
            else {
                command.sendRetrievedTranslation(channel, "music", language, "nopermissiontojoin", user.getUser());
            }
            return voiceChannel;
        }
        else {
            command.sendRetrievedTranslation(channel, "music", language, "notinvoicechannel", user.getUser());
            return null;
        }
    }

    public static void checkMusicConnections() {
        for (Shard shard : ShardManager.getShards()) {
            shard.executorService.scheduleAtFixedRate(() -> {
                try {
                    for (Guild guild : shard.jda.getGuilds()) {
                        GuildMusicManager manager = getGuildAudioPlayer(guild, null, shard);
                        if (manager != null) {
                            GuildVoiceState voiceState = guild.getSelfMember().getVoiceState();
                            if (voiceState.inVoiceChannel()) {
                                TextChannel channel = manager.scheduler.manager.getChannel();
                                if (channel == null) channel = guild.getPublicChannel();
                                if (channel != null) {
                                    if (channel.canTalk()) {
                                        VoiceChannel voiceChannel = voiceState.getChannel();
                                        Language language = GuildUtils.getLanguage(guild);
                                        AudioPlayer player = manager.player;
                                        if (voiceState.isGuildMuted()) {
                                            shard.help.sendRetrievedTranslation(channel,
                                                    "music", language,
                                                    "mutedinchannelpausingnow", null);
                                            player.setPaused(true);
                                        }
                                        if (voiceChannel.getMembers().size() == 1) {
                                            shard.help.sendTranslatedMessage(shard.help.getTranslation("music", language,
                                                    "leftbcnic").getTranslation().replace("{0}", voiceChannel.getName()),
                                                    channel, null);
                                            guild.getAudioManager().closeAudioConnection();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                catch (Exception ex) {
                    new BotException(ex);
                }
            }, 5, 5, TimeUnit.MINUTES);
        }
    }

    private static int getHours(AudioTrack track) {
        long length = track.getInfo().length;
        int seconds = (int) (length / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        return hours % 60;
    }

    private static String getDuration(AudioTrack track) {
        long length = track.getInfo().length;
        int seconds = (int) (length / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        if (getHours(track) < 0) {
            return "[Live Stream]";
        }
        else {
            return "[" + String.format("%02d", (hours % 60)) + ":" + String.format("%02d", (minutes % 60)) + ":" +
                    String.format("%02d", (seconds % 60)) + "]";
        }
    }

    private static String getDuration(ArrayList<AudioTrack> tracks) {
        long length = 0;
        for (AudioTrack t : tracks) length += t.getDuration();
        int seconds = (int) (length / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        return "[" + String.format("%02d", (hours % 60)) + ":" + String.format("%02d", (minutes % 60)) + ":" + String
                .format("%02d", (seconds % 60)) + "]";
    }

    private static String getCurrentTime(AudioTrack track) {
        long current = track.getPosition();
        int seconds = (int) (current / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;

        long length = track.getInfo().length;
        int lengthSeconds = (int) (length / 1000);
        int lengthMinutes = lengthSeconds / 60;
        int lengthHours = lengthMinutes / 60;

        return "[" + String.format("%02d", (hours % 60)) + ":" + String.format("%02d", (minutes % 60)) + ":" + String
                .format("%02d", (seconds % 60)) + " / " + String.format("%02d", (lengthHours % 60)) + ":" + String
                .format("%02d", (lengthMinutes % 60)) + ":" + String.format("%02d", (lengthSeconds % 60)) + "]";
    }

    static boolean shouldDeleteMessages(Guild guild) throws SQLException {
        boolean returnValue = false;
        MusicSettingsModel musicSettingsModel = asPojo(r.db("data").table("music_settings").get(guild.getId()).run(connection),
                MusicSettingsModel.class);
        if (musicSettingsModel != null) {
            if (musicSettingsModel.isRemove_addition_messages()) returnValue = true;
        }
        return returnValue;
    }

    private static TextChannel getOutputChannel(Guild guild) throws SQLException {
        String id;
        MusicSettingsModel guildMusicSettings = BaseCommand.asPojo(r.db("data").table("music_settings").get(guild.getId()).run
                (connection), MusicSettingsModel.class);
        if (guildMusicSettings != null) {
            String setId = guildMusicSettings.getChannel_id();
            if (setId.equalsIgnoreCase("none")) id = null;
            else id = setId;
        }
        else {
            id = null;
            r.db("data").table("music_settings").insert(r.json(getStaticGson().toJson(new MusicSettingsModel(guild.getId(), false,
                    "none"))))
                    .run(connection);
        }
        if (id == null) return null;
        else return guild.getTextChannelById(id);
    }

    static MessageChannel sendTo(MessageChannel channel, Guild guild) throws SQLException {
        TextChannel outputChannel = getOutputChannel(guild);
        if (outputChannel != null) return outputChannel;
        else return channel;
    }

    private EmbedBuilder getMusicEmbed(Guild guild, Language language, User user) throws Exception {
        EmbedBuilder builder = MessageUtils.getDefaultEmbed(guild, user, this);
        builder.setAuthor(WordUtils.capitalize(getName(language)), getShard().url, guild.getSelfMember().getUser().getAvatarUrl());
        return builder;
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language
            language) throws Exception {
        sendHelp(language, channel, guild, user, this);
    }

    @Override
    public void setupSubcommands() throws Exception {
        subcommands.add(new Subcommand(this, "play") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                if (args.length > 2) {
                    AudioManager audioManager = guild.getAudioManager();
                    String url = message.getRawContent().replace(GuildUtils.getPrefix(guild) + args[0] + " " +
                            args[1] + " ", "");
                    boolean shouldDeleteMessage = shouldDeleteMessages(guild);
                    boolean implement = false;
                    if (!audioManager.isConnected()) {
                        VoiceChannel success = joinChannel(guild, guild.getMember(user), language, Music.this,
                                audioManager, channel);
                        if (success != null) {
                            loadAndPlay(message, user, Music.this, language, (TextChannel) channel, url, success, false);
                            implement = true;
                        }
                    }
                    else {
                        loadAndPlay(message, user, Music.this, language, (TextChannel) sendTo(channel, guild), url, audioManager
                                .getConnectedChannel(), false);
                        implement = true;
                    }
                    if (implement) {
                        if (shouldDeleteMessage) {
                            try {
                                message.delete().queue();
                            }
                            catch (PermissionException ex) {
                                guild.getOwner().getUser().openPrivateChannel().queue(privateChannel -> {
                                    privateChannel.sendMessage("Auto-deleting music play messages is enabled, " +
                                            "but you need to give me the `MANAGE MESSAGES` permission so I can " +
                                            "actually delete the messages.").queue();
                                });
                            }
                        }
                    }
                }
                else sendRetrievedTranslation(channel, "tag", language, "invalidarguments", user);
            }
        });

        subcommands.add(new Subcommand(this, "recommend") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                if (args.length > 2) {
                    AudioManager audioManager = guild.getAudioManager();
                    if (audioManager.isConnected()) {
                        VoiceChannel connected = audioManager.getConnectedChannel();
                        try {
                            int amount = Integer.parseInt(args[2]);
                            if (amount <= 0 || amount > 10) {
                                sendRetrievedTranslation(channel, "music", language, "only10recommended", user);
                                return;
                            }
                            GuildMusicManager manager = getGuildAudioPlayer(guild, channel);
                            ArdentTrack ardentTrack = manager.scheduler.manager.getCurrentlyPlaying();
                            if (ardentTrack == null) {
                                sendRetrievedTranslation(channel, "music", language, "notplayingrn", user);
                                return;
                            }
                            String[] nameArgs = StringUtils.removeBracketsParentheses(ardentTrack.getTrack().getInfo
                                    ().title).split(" ");
                            StringBuilder name = new StringBuilder();
                            for (String arg : nameArgs) {
                                if (!arg.contains(".") && !arg.contains("+") && !arg.contains(":") && !arg.contains
                                        ("//"))
                                {
                                    name.append(arg);
                                }
                                name.append(" ");
                            }
                            TrackSearchRequest trackSearchRequest = spotifyApi.searchTracks(name.toString()).build();
                            try {
                                Page<Track> tracks = trackSearchRequest.get();
                                String id = tracks.getItems().get(0).getId();
                                ArrayList<String> ids = new ArrayList<>();
                                ids.add(id);
                                RecommendationsRequest recommendationsRequest = spotifyApi.getRecommendations()
                                        .tracks(ids)
                                        .build();
                                List<Track> recommendations = recommendationsRequest.get();
                                for (int i = 0; i < amount; i++) {
                                    loadAndPlay(message, user, Music.this, language, (TextChannel) sendTo(channel, guild), recommendations
                                            .get(i).getName(), connected, false);
                                }
                            }
                            catch (Exception e) {
                                channel.sendMessage("There were no recommendations available, sorry!").queue();
                            }
                        }
                        catch (NumberFormatException e) {
                            sendRetrievedTranslation(channel, "prune", language, "notanumber", user);
                        }
                    }
                    else {
                        sendRetrievedTranslation(channel, "music", language, "notinvoicechannel", user);
                    }
                }
                else sendRetrievedTranslation(channel, "tag", language, "invalidarguments", user);
            }
        });

        subcommands.add(new Subcommand(this, "config") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                Cursor<HashMap> settings = r.db("data").table("music_settings").filter(row -> row.g("guild_id").eq(guild.getId())).run
                        (connection);
                if (settings.hasNext()) {
                    MusicSettingsModel musicSettingsModel = asPojo(settings.next(), MusicSettingsModel.class);
                    sendTranslatedMessage("**music Settings**\n" + "Delete music play messages: " + musicSettingsModel
                            .isRemove_addition_messages(), channel, user);

                }
                else
                    sendTranslatedMessage("Your guild has no set music settings! Type **/manage** to find your portal" +
                            " link", channel, user);
            }
        });

        subcommands.add(new Subcommand(this, "queue") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                ArrayList<Translation> translations = new ArrayList<>();
                translations.add(new Translation("music", "songsinqueue"));
                translations.add(new Translation("music", "queuedby"));
                translations.add(new Translation("music", "nosongsinqueue"));
                translations.add(new Translation("music", "totalqueuetime"));

                HashMap<Integer, TranslationResponse> response = getTranslations(language, translations);
                StringBuilder sb = new StringBuilder();
                String queuedBy = response.get(1).getTranslation();
                sb.append("__" + response.get(0).getTranslation() + "__\n");
                BlockingQueue<ArdentTrack> queue = getGuildAudioPlayer(guild, channel).scheduler.manager.getQueue();
                Iterator<ArdentTrack> iterator = queue.iterator();
                int current = 1;
                ArrayList<AudioTrack> trackList = new ArrayList<>();
                while (iterator.hasNext()) {
                    ArdentTrack ardentTrack = iterator.next();
                    AudioTrack track = ardentTrack.getTrack();
                    trackList.add(track);
                    sb.append("#" + current + ": " + track.getInfo().title + ": " + track.getInfo().author + " " +
                            getDuration(track) + "\n     *" + queuedBy + " " + GuildUtils.getShard(guild).jda
                            .getUserById(ardentTrack.getAuthor()).getName()
                            + "*\n");
                    current++;
                }
                if (current == 1) {
                    sb.append(response.get(2).getTranslation());
                }
                sendTranslatedMessage(sb.toString(), sendTo(channel, guild), user);
            }
        });

        subcommands.add(new Subcommand(this, "skip") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                AudioManager audioManager = guild.getAudioManager();
                Member member = guild.getMember(user);
                if (audioManager.isConnected()) {
                    GuildMusicManager manager = getGuildAudioPlayer(guild, channel);
                    ArdentMusicManager ardentMusicManager = manager.scheduler.manager;
                    ArdentTrack track = ardentMusicManager.getCurrentlyPlaying();
                    if (track != null) {
                        String ownerId = track.getAuthor();
                        if (ownerId == null) ownerId = "";
                        if (UserUtils.hasManageServerOrStaff(member) || (user.getId().equalsIgnoreCase(ownerId))) {
                            ardentMusicManager.nextTrack();
                            sendRetrievedTranslation(sendTo(channel, guild), "music", language, "skippedcurrent", user);
                        }
                        else sendRetrievedTranslation(channel, "music", language, "queuedorhavepermissions", user);
                    }
                }
                else sendRetrievedTranslation(channel, "music", language, "notinmusicchannel", user);
            }
        });

        subcommands.add(new Subcommand(this, "remove") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                if (args.length > 2) {
                    AudioManager audioManager = guild.getAudioManager();
                    Member member = guild.getMember(user);
                    if (audioManager.isConnected()) {
                        try {
                            GuildMusicManager manager = getGuildAudioPlayer(guild, channel);
                            BlockingQueue<ArdentTrack> queue = manager.scheduler.manager.getQueue();
                            int numberToRemove = Integer.parseInt(args[2]) - 1;
                            if (numberToRemove >= queue.size() || numberToRemove < 0)
                                sendRetrievedTranslation(channel, "tag", language, "invalidarguments", user);
                            else {
                                Iterator<ArdentTrack> iterator = queue.iterator();
                                int current = 0;
                                while (iterator.hasNext()) {
                                    ArdentTrack ardentTrack = iterator.next();
                                    AudioTrack track = ardentTrack.getTrack();
                                    String name = track.getInfo().title;
                                    if (current == numberToRemove) {
                                        if (UserUtils.hasManageServerOrStaff(member) || ardentTrack.getAuthor()
                                                .equalsIgnoreCase(user.getId()))
                                        {
                                            queue.remove(ardentTrack);
                                            sendTranslatedMessage(getTranslation("music", language, "removedfromqueue")
                                                    .getTranslation().replace("{0}", name), sendTo(channel, guild), user);
                                        }
                                        else {
                                            sendRetrievedTranslation(channel, "music", language,
                                                    "queuedorhavepermissions", user);
                                        }
                                    }
                                    current++;
                                }
                            }
                        }
                        catch (NumberFormatException ex) {
                            sendRetrievedTranslation(channel, "tag", language, "invalidarguments", user);
                        }
                    }
                    else sendRetrievedTranslation(channel, "music", language, "notinmusicchannel", user);
                }
                else sendRetrievedTranslation(channel, "prune", language, "notanumber", user);
            }
        });

        subcommands.add(new Subcommand(this, "leave") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                AudioManager audioManager = guild.getAudioManager();
                Member member = guild.getMember(user);
                if (UserUtils.hasManageServerOrStaff(member)) {
                    if (audioManager.isConnected()) {
                        String name = audioManager.getConnectedChannel().getName();
                        audioManager.closeAudioConnection();
                        sendTranslatedMessage(getTranslation("music", language, "disconnected").getTranslation()
                                .replace("{0}", name), channel, user);
                    }
                    else sendRetrievedTranslation(channel, "music", language, "notinmusicchannel", user);
                }
                else sendRetrievedTranslation(channel, "other", language, "needmanageserver", user);
            }
        });

        subcommands.add(new Subcommand(this, "resume") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                AudioManager audioManager = guild.getAudioManager();
                Member member = guild.getMember(user);
                if (UserUtils.hasManageServerOrStaff(member)) {
                    if (audioManager.isConnected()) {
                        GuildMusicManager manager = getGuildAudioPlayer(guild, channel);
                        if (manager.player.isPaused()) {
                            sendRetrievedTranslation(sendTo(channel, guild), "music", language, "resumedplayback", user);
                            manager.player.setPaused(false);
                        }
                        else {
                            sendRetrievedTranslation(channel, "music", language, "notpaused", user);
                        }
                    }
                    else sendRetrievedTranslation(channel, "music", language, "notinmusicchannel", user);
                }
                else sendRetrievedTranslation(channel, "other", language, "needmanageserver", user);
            }
        });

        subcommands.add(new Subcommand(this, "stop") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                AudioManager audioManager = guild.getAudioManager();
                Member member = guild.getMember(user);
                if (UserUtils.hasManageServerOrStaff(member)) {
                    if (audioManager.isConnected()) {
                        GuildMusicManager manager = getGuildAudioPlayer(guild, channel);
                        if (manager.player.getPlayingTrack() != null) manager.player.stopTrack();
                        manager.scheduler.manager.resetQueue();
                        getShard().musicManagers.remove(Long.parseLong(guild.getId()));
                        sendRetrievedTranslation(sendTo(channel, guild), "music", language, "stoppedandcleared", user);
                    }
                    else sendRetrievedTranslation(channel, "music", language, "notinmusicchannel", user);
                }
                else sendRetrievedTranslation(channel, "other", language, "needmanageserver", user);
            }
        });

        subcommands.add(new Subcommand(this, "votetoskip") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws
                    Exception {
                AudioManager audioManager = guild.getAudioManager();
                VoiceChannel connected = audioManager.getConnectedChannel();
                if (connected != null && connected.getMembers().stream().filter((member -> member.getUser().getId().equals(user.getId())))
                        .collect(Collectors.toList()).size() > 0)
                {
                    GuildMusicManager guildMusicManager = getGuildAudioPlayer(guild, channel);
                    ArdentTrack track = guildMusicManager.scheduler.manager.getCurrentlyPlaying();
                    if (track == null) {
                        sendRetrievedTranslation(channel, "music", language, "notplayingrn", user);
                        return;
                    }
                    if (track.getVotedToSkip().contains(user.getId())) {
                        sendRetrievedTranslation(channel, "music", language, "alreadyvotedtoskip", user);
                        return;
                    }
                    track.addSkipVote(user);
                    if (track.getVotedToSkip().size() >= Math.round(connected.getMembers().size() / 2)) {
                        sendRetrievedTranslation(channel, "music", language, "skippedtrack", user);
                        guildMusicManager.scheduler.manager.nextTrack();
                    }
                    else sendRetrievedTranslation(channel, "music", language, "skipvoteadded", user);
                }
                else sendRetrievedTranslation(channel, "music", language, "notinoryourenotin", user);
            }
        });

        subcommands.add(new Subcommand(this, "clear") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                AudioManager audioManager = guild.getAudioManager();
                Member member = guild.getMember(user);
                if (UserUtils.hasManageServerOrStaff(member)) {
                    if (audioManager.isConnected()) {
                        GuildMusicManager manager = getGuildAudioPlayer(guild, channel);
                        manager.scheduler.manager.resetQueue();
                        sendRetrievedTranslation(sendTo(channel, guild), "music", language, "clearedallsongs", user);
                    }
                    else sendRetrievedTranslation(channel, "music", language, "notinmusicchannel", user);
                }
                else sendRetrievedTranslation(channel, "other", language, "needmanageserver", user);
            }
        });

        subcommands.add(new Subcommand(this, "playing") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                GuildMusicManager manager = getGuildAudioPlayer(guild, channel);
                ArdentMusicManager ardentMusicManager = manager.scheduler.manager;
                ArdentTrack nowPlaying = ardentMusicManager.getCurrentlyPlaying();
                if (nowPlaying != null) {
                    AudioTrack track = nowPlaying.getTrack();
                    AudioTrackInfo info = track.getInfo();
                    StringBuilder sb = new StringBuilder();
                    String queuedBy = getTranslation("music", language, "queuedby").getTranslation();
                    sb.append(info.title + ": " + info.author + " " + getCurrentTime
                            (track) +
                            "\n     *" + queuedBy + " " + UserUtils.getUserById(nowPlaying.getAuthor()).getName() +
                            "* - [" + nowPlaying.getVotedToSkip().size() + " / " + Math.round(guild.getAudioManager().getConnectedChannel
                            ().getMembers().size() / 2) + "] " + getTranslation("music", language, "votestoskip").getTranslation());
                    sendTranslatedMessage(sb.toString(), sendTo(channel, guild), user);
                }
                else sendRetrievedTranslation(channel, "music", language, "notplayingrn", user);
            }
        });

        subcommands.add(new Subcommand(this, "shuffle") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                AudioManager audioManager = guild.getAudioManager();
                Member member = guild.getMember(user);
                if (UserUtils.hasManageServerOrStaff(member)) {
                    if (audioManager.isConnected()) {
                        GuildMusicManager manager = getGuildAudioPlayer(guild, channel);
                        manager.scheduler.manager.shuffle();
                        sendTranslatedMessage("Shuffled the queue!", sendTo(channel, guild), user);
                    }
                    else sendRetrievedTranslation(channel, "music", language, "notinmusicchannel", user);
                }
                else sendRetrievedTranslation(channel, "other", language, "needmanageserver", user);
            }
        });

        subcommands.add(new Subcommand(this, "pause") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                AudioManager audioManager = guild.getAudioManager();
                Member member = guild.getMember(user);
                if (UserUtils.hasManageServerOrStaff(member)) {
                    if (audioManager.isConnected()) {
                        GuildMusicManager manager = getGuildAudioPlayer(guild, channel);
                        if (!manager.player.isPaused()) {
                            sendRetrievedTranslation(sendTo(channel, guild), "music", language, "pausedplayback", user);
                            manager.player.setPaused(true);
                        }
                        else {
                            sendRetrievedTranslation(channel, "music", language, "alreadypaused", user);
                        }
                    }
                    else sendRetrievedTranslation(channel, "music", language, "notinmusicchannel", user);
                }
                else sendRetrievedTranslation(channel, "other", language, "needmanageserver", user);
            }
        });

        subcommands.add(new Subcommand(this, "setoutput") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws
                    Exception {
                if (args.length == 2) {
                    sendRetrievedTranslation(channel, "music", language, "setoutputhelp", user);
                }
                else {
                    if (guild.getMember(user).hasPermission(Permission.MANAGE_SERVER)) {
                        List<TextChannel> mentionedChannels = message.getMentionedChannels();
                        if (mentionedChannels.size() > 0) {
                            getOutputChannel(guild);
                            r.db("data").table("music_settings").filter(row -> row.g("guild_id").eq(guild.getId()))
                                    .update(r.hashMap("channel_id", mentionedChannels.get(0).getId())).run(connection);
                            sendRetrievedTranslation(channel, "music", language, "setoutputchannel", user);
                        }
                        else {
                            if (getOutputChannel(guild) != null) {
                                r.db("data").table("music_settings").filter(row -> row.g("guild_id").eq(guild.getId()))
                                        .update(r.hashMap("channel_id", "none")).run(connection);
                                sendRetrievedTranslation(channel, "music", language, "removedoutputchannel", user);
                            }
                            else {
                                sendRetrievedTranslation(channel, "music", language, "nochannelset", user);
                            }
                        }
                    }
                    else sendRetrievedTranslation(channel, "other", language, "needmanageserver", user);
                }
            }
        });

        subcommands.add(new Subcommand(this, "loop") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                if (args.length == 4) {
                    if (UserUtils.hasManageServerOrStaff(guild.getMember(user))) {
                        try {
                            int songsToLoop = Integer.parseInt(args[2]);
                            int amountOfTimes = Integer.parseInt(args[3]);

                            GuildMusicManager guildMusicManager = getGuildAudioPlayer(guild, channel);
                            TrackScheduler trackScheduler = guildMusicManager.scheduler;
                            BlockingQueue<ArdentTrack> queue = trackScheduler.manager.getQueue();
                            int amountOfTracks = queue.size();

                            if (songsToLoop < 0 || songsToLoop > amountOfTracks) {
                                sendRetrievedTranslation(channel, "music", language, "impossibletoloop", user);
                            }
                            else {
                                if (amountOfTimes < 0 || amountOfTimes > 3) {
                                    sendRetrievedTranslation(channel, "music", language, "onlycanloopsongs", user);
                                }
                                else {
                                    ArrayList<AudioTrack> tracksToLoop = new ArrayList<>();
                                    Iterator<ArdentTrack> trackIterator = queue.iterator();
                                    for (int i = 0; i < songsToLoop; i++) {
                                        ArdentTrack ardentTrack = trackIterator.next();
                                        tracksToLoop.add(ardentTrack.getTrack());
                                    }
                                    for (int i = 0; i < amountOfTimes; i++) {
                                        tracksToLoop.forEach(track -> {
                                            trackScheduler.manager.addToQueue(new ArdentTrack(user.getId(),
                                                    (TextChannel) channel, track.makeClone()));
                                        });
                                    }
                                    sendTranslatedMessage(getTranslation("music", language, "addedtheloop")
                                            .getTranslation().replace("{0}", String.valueOf(songsToLoop)).replace
                                                    ("{1}", String.valueOf(amountOfTimes)), sendTo(channel, guild), user);
                                }
                            }
                        }
                        catch (NumberFormatException ex) {
                            sendRetrievedTranslation(channel, "prune", language, "notanumber", user);
                        }
                    }
                    else sendRetrievedTranslation(channel, "other", language, "needmanageserver", user);
                }
                else sendRetrievedTranslation(channel, "music", language, "loopsyntaxhelp", user);
            }
        });

        subcommands.add(new Subcommand(this, "removeallfrom") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                Member member = guild.getMember(user);
                if (UserUtils.hasManageServerOrStaff(member)) {
                    List<User> mentionedUsers = message.getMentionedUsers();
                    if (mentionedUsers.size() == 1) {
                        User deleteFrom = mentionedUsers.get(0);
                        getGuildAudioPlayer(guild, channel).scheduler.manager.removeFrom(deleteFrom);
                        sendTranslatedMessage(getTranslation("music", language, "deletealltracksfrom").getTranslation()
                                .replace("{0}", deleteFrom.getName()), channel, user);
                    }
                    else sendRetrievedTranslation(channel, "other", language, "mentionuser", user);
                }
                else sendRetrievedTranslation(channel, "other", language, "needmanageserver", user);
            }
        });

        subcommands.add(new Subcommand(this, "restart") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                GuildMusicManager musicManager = getGuildAudioPlayer(guild, channel);
                ArdentMusicManager player = musicManager.scheduler.manager;
                ArdentTrack current = player.getCurrentlyPlaying();
                if (current != null) {
                    if (UserUtils.hasManageServerOrStaff(guild.getMember(user)) || user.getId().equalsIgnoreCase
                            (current.getAuthor()))
                    {
                        AudioTrack track = current.getTrack();
                        track.setPosition(0);
                        sendTranslatedMessage(getTranslation("music", language, "restartedtrack").getTranslation()
                                .replace("{0}", track.getInfo().title), channel, user);
                    }
                    else sendRetrievedTranslation(channel, "music", language, "queuedorhavepermissions", user);
                }
                else sendRetrievedTranslation(channel, "music", language, "notplayingrn", user);
            }
        });

        subcommands.add(new Subcommand(this, "geturl") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws
                    Exception {
                AudioManager audioManager = guild.getAudioManager();
                if (audioManager.isConnected()) {
                    try {
                        GuildMusicManager manager = getGuildAudioPlayer(guild, channel);
                        BlockingQueue<ArdentTrack> queue = manager.scheduler.manager.getQueue();
                        if (args.length > 2) {
                            int numberToRemove = Integer.parseInt(args[2]) - 1;
                            if (numberToRemove >= queue.size() || numberToRemove < 0)
                                sendRetrievedTranslation(channel, "tag", language, "invalidarguments", user);
                            else {
                                Iterator<ArdentTrack> iterator = queue.iterator();
                                int current = 0;
                                while (iterator.hasNext()) {
                                    ArdentTrack ardentTrack = iterator.next();
                                    AudioTrack track = ardentTrack.getTrack();
                                    AudioTrackInfo info = track.getInfo();
                                    String name = info.title;
                                    if (current == numberToRemove) {
                                        sendTranslatedMessage(getTranslation("music", language, "urlof").getTranslation()
                                                .replace("{0}", name).replace("{1}", info.uri), channel, user);
                                        return;
                                    }
                                    current++;
                                }
                            }
                        }
                        else {
                            ArdentMusicManager musicManager = manager.scheduler.manager;
                            ArdentTrack track = musicManager.getCurrentlyPlaying();
                            if (track != null) {
                                AudioTrackInfo info = track.getTrack().getInfo();
                                sendTranslatedMessage(getTranslation("music", language, "urlof").getTranslation()
                                        .replace("{0}", info.title).replace("{1}", info.uri), channel, user);
                            }
                            else {
                                sendRetrievedTranslation(channel, "music", language, "notplayingrn", user);
                            }
                        }
                    }
                    catch (NumberFormatException ex) {
                        sendRetrievedTranslation(channel, "tag", language, "invalidarguments", user);
                    }
                }
                else sendRetrievedTranslation(channel, "music", language, "notinmusicchannel", user);
            }
        });

        subcommands.add(new Subcommand(this, "volume") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args,
                               Language language) throws Exception {
                AudioManager audioManager = guild.getAudioManager();
                if (audioManager.isConnected()) {
                    GuildMusicManager guildMusicManager = getGuildAudioPlayer(guild, channel);
                    AudioPlayer player = guildMusicManager.player;
                    if (args.length == 2) {
                        sendTranslatedMessage(getTranslation("music", language, "currentplayervolume").getTranslation
                                ().replace("{0}", String.valueOf(player.getVolume())), channel, user);
                    }
                    else {
                        if (UserUtils.hasTierOnePermissions(user) || EntityGuild.get(guild).isPremium()) {
                            try {
                                int volume = Integer.parseInt(args[2]);
                                player.setVolume(volume);
                                sendTranslatedMessage(getTranslation("music", language, "setplayervolume")
                                        .getTranslation()
                                        .replace("{0}", String.valueOf(volume)), sendTo(channel, guild), user);
                            }
                            catch (NumberFormatException ex) {
                                sendRetrievedTranslation(channel, "prune", language, "notanumber", user);
                            }
                        }
                        else sendRetrievedTranslation(channel, "other", language, "mustbestafforpatron", user);
                    }
                }
                else sendRetrievedTranslation(channel, "music", language, "notinmusicchannel", user);
            }
        });
    }
}