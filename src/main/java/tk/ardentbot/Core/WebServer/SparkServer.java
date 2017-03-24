package tk.ardentbot.Core.WebServer;

import spark.Request;
import spark.Response;
import tk.ardentbot.Core.CommandExecution.CommandFactory;
import tk.ardentbot.Core.LoggingUtils.BotException;
import tk.ardentbot.Core.Translation.LangFactory;
import tk.ardentbot.Core.Translation.Language;
import tk.ardentbot.Core.WebServer.Models.Command;
import tk.ardentbot.Core.WebServer.Models.Status;
import tk.ardentbot.Core.WebServer.Models.User;
import tk.ardentbot.Main.Ardent;
import tk.ardentbot.Utils.Discord.InternalStats;
import tk.ardentbot.Utils.SQL.DatabaseAction;
import tk.ardentbot.Utils.Tuples.Pair;
import tk.ardentbot.Utils.Tuples.Quintet;

import java.lang.management.ManagementFactory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Random;

import static spark.Spark.get;
import static spark.Spark.port;
import static tk.ardentbot.BotCommands.BotAdministration.Translate.*;
import static tk.ardentbot.Main.Ardent.shard0;
import static tk.ardentbot.Utils.SQL.SQLUtils.cleanString;

public class SparkServer {
    /**
     * Sets up the web server and the endpoints
     */
    public static void setup() {
        if (Ardent.testingBot) {
            return;
        }
        else {
            port(666);
        }
        get("/api/commands", (rq, rs) -> {
            CommandFactory factory = shard0.factory;
            ArrayList<Command> commands = new ArrayList<>();
            factory.getBaseCommands().forEach(command -> {
                try {
                    commands.add(new Command(command.getName(LangFactory.english), command.getCategory(), command
                            .getDescription(LangFactory.english)));
                }
                catch (Exception e) {
                    new BotException(e);
                }
            });
            return shard0.gson.toJson(commands);
        });

        get("/api/staff", (rq, rs) -> {
            ArrayList<User> developers = new ArrayList<>();
            for (String id : Ardent.developers) {
                net.dv8tion.jda.core.entities.User user = shard0.jda.getUserById(id);
                String avatarUrl = user.getAvatarUrl();
                if (avatarUrl == null) avatarUrl = getDefaultImage();

                developers.add(new User(id, user.getName(), user.getDiscriminator(), avatarUrl, "developer"));
            }
            ArrayList<User> translators = new ArrayList<>();
            for (String id : Ardent.translators) {
                net.dv8tion.jda.core.entities.User user = shard0.jda.getUserById(id);
                String avatarUrl = user.getAvatarUrl();
                if (avatarUrl == null) avatarUrl = getDefaultImage();

                translators.add(new User(id, user.getName(), user.getDiscriminator(), avatarUrl, "translator"));
            }
            ArrayList<ArrayList<User>> staff = new ArrayList<>();
            staff.add(developers);
            staff.add(translators);
            return shard0.gson.toJson(staff);
        });
        get("/api/status", (rq, rs) -> {
            InternalStats internalStats = InternalStats.collect();
            return shard0.gson.toJson(new Status(internalStats.getMessagesReceived(),
                    internalStats.getCommandsReceived(), ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                    internalStats.getLoadedCommands(), internalStats.getGuilds(), internalStats.getUsers()));
        });
        get("/api/languages", (rq, rs) -> shard0.gson.toJson(LangFactory.languages));
        get("/api/translate/*/*/*", SparkServer::translate);
        get("/api/translate/submit", (rq, rs) -> {
            String response = submit(rq);
            if (response.startsWith("Successfully")) {
                String type = rq.queryParams("type");
                String language = rq.queryParams("language");
                if (type != null && language != null) {
                    rs.redirect("http://ardentbot.tk:666/api/translate/169904324980244480/" + type + "/" + language);
                }
            }
            return null;
        });
    }

    /**
     * Submits translations and adds them to the database
     *
     * @param rq request
     * @return status of the translation
     */
    private static String submit(Request rq) {
        try {
            String type = rq.queryParams("type");
            if (type != null) {
                String languageString = rq.queryParams("language");
                if (languageString != null) {
                    Language language = LangFactory.getLanguage(languageString);
                    if (language != null) {
                        if (type.equalsIgnoreCase("phrases")) {
                            String id = rq.queryParams("identifier");
                            String command = rq.queryParams("commandidentifier");
                            String translation = rq.queryParams("translation");
                            if (translation != null && !translation.isEmpty() && id != null && !id.isEmpty() &&
                                    command != null && !command.isEmpty())
                            {
                                new DatabaseAction("INSERT INTO Translations VALUES (?,?,?,?,?)").set(command)
                                        .set(translation).set(id).set(language.getIdentifier()).set(0).update();
                                return "Successfully added your translation. Go back and reload the page or use your " +
                                        "base URL to enter in another one!";
                            }
                            else return "Invalid translation - null or empty!";
                        }
                        else if (type.equalsIgnoreCase("commands")) {
                            String id = rq.queryParams("identifier");
                            String translationName = rq.queryParams("translationname");
                            String translationDescription = rq.queryParams("translationdescription");
                            if (id != null && translationName != null && translationDescription != null
                                    && !id.isEmpty() && !translationName.isEmpty() &&
                                    !translationDescription.isEmpty())
                            {
                                new DatabaseAction("INSERT INTO Commands VALUES (?,?,?,?)").set(id).set(language
                                        .getIdentifier()).set(translationName).set(translationDescription).update();
                                return "Successfully added your translation. Go back and reload the page or use your " +
                                        "base URL to enter in another one!";
                            }
                            else return "Invalid arguments. Make sure you entered in all the fields.";
                        }
                        else if (type.equalsIgnoreCase("subcommands")) {
                            String id = rq.queryParams("identifier");
                            String commandId = rq.queryParams("commandidentifier");
                            String translationName = rq.queryParams("translationname");
                            String translationSyntax = rq.queryParams("translationsyntax");
                            String translationDescription = rq.queryParams("translationdescription");
                            if (id != null && commandId != null && translationName != null && translationSyntax !=
                                    null && translationDescription != null
                                    && !id.isEmpty() && !commandId.isEmpty() && !translationName.isEmpty() &&
                                    !translationSyntax.isEmpty() && !translationDescription.isEmpty())
                            {
                                new DatabaseAction("INSERT INTO Subcommands VALUES (?,?,?,?,?,?,?)").set(commandId)
                                        .set(id).set(language.getIdentifier()).set(translationName).set
                                        (translationSyntax)
                                        .set(translationDescription).set(true).update();
                                return "Successfully added your translation. Go back and reload the page or use your " +
                                        "base URL to enter in another one!";
                            }
                            else return "Invalid arguments. Make sure you entered in all the fields.";
                        }
                        else return "Incorrect type specified.... somehow";
                    }
                    else return "Invalid language specified";
                }
                else return "No language specified.";
            }
            else return "wat";
        }
        catch (Exception ex) {
            new BotException(ex);
            return "Database returned an error.";
        }
    }

    /**
     * Returns the translation form
     *
     * @param rq request
     * @param rs response
     * @return translation form
     */
    private static String translate(Request rq, Response rs) {
        String incorrectArgs = "Incorrect args specified: /api/translate/yourid/(phrases OR commands OR subcommands)" +
                "/languagecode";
        String[] splats = rq.splat();
        if (splats.length == 3) {
            if (Ardent.translators.contains(String.valueOf(Long.valueOf(splats[0])))) {
                Language language = LangFactory.getLanguage(splats[2]);
                if (language != null) {
                    try (Statement statement = Ardent.conn.createStatement()) {
                        if (splats[1].equalsIgnoreCase("phrases")) {
                            ArrayList<String> discrepancies = getTranslationDiscrepancies(language);
                            if (discrepancies.size() > 0) {
                                String discrepancy1 = discrepancies.get(0);
                                ResultSet set = statement.executeQuery("SELECT * FROM Translations WHERE " +
                                        "Translation='" + cleanString(discrepancy1) + "'");
                                if (set.next()) {
                                    String commandIdentifier = set.getString("CommandIdentifier");
                                    String id = set.getString("ID");
                                    set.close();
                                    return "<!DOCTYPE html>\n" +
                                            "<html>\n" +
                                            "<body>\n" +
                                            "\n" +
                                            "<h2>Translate Phrases for " + LangFactory.getName(language) + " (" +
                                            discrepancies.size() + " phrases left to translate)</h2><br>\n" +
                                            "\n" +
                                            "English Text<br>\n" +
                                            "<textarea rows=\"4\" cols=\"100\" name=\"original\" form=\"phrases\" " +
                                            "disabled>" + discrepancy1 + "</textarea>\n" +
                                            "<br>Translate Here <br>\n" +
                                            "<textarea rows=\"4\" cols=\"100\" name=\"translation\" " +
                                            "form=\"phrases\"></textarea>\n" +
                                            "\n" +
                                            "<form action=\"/api/translate/submit\" id=\"phrases\">\n" +
                                            "  <br>\n" +
                                            "<input type=\"hidden\" name=\"identifier\" value=\"" + id + "\"><input " +
                                            "type=\"hidden\" name=\"commandidentifier\" value=\"" + commandIdentifier
                                            + "\"><input type=\"hidden\" name=\"language\" value=\"" + LangFactory
                                            .getName(language) + "\"><input type=\"hidden\" name=\"type\" " +
                                            "value=\"phrases\"><input type=\"submit\" value=\"Add Translation\">\n" +
                                            "</form>\n" +
                                            "<br>\n" +
                                            "</body>\n" +
                                            "</html>\n";
                                }
                                else return "Something went wrong (like really wrong): " + discrepancy1;
                            }
                            else
                                return "No untranslated phrases for this language! Try commands or subcommand " +
                                        "translations.";
                        }
                        else if (splats[1].equalsIgnoreCase("commands")) {
                            ArrayList<Pair<String, String>> discrepancies = getCommandDiscrepancies(language);
                            if (discrepancies.size() > 0) {
                                Pair<String, String> discrepancy = discrepancies.get(0);
                                return "<!DOCTYPE html>\n" +
                                        "<html>\n" +
                                        "<body>\n" +
                                        "\n" +
                                        "<h2>Translate Commands for " + LangFactory.getName
                                        (language) +
                                        " (" + discrepancies.size() + " commands left to translate)</h2><br>\n" +
                                        "\n" +
                                        "English Name<br>\n" +
                                        "<textarea rows=\"4\" cols=\"100\" name=\"originalname\" form=\"commands\" " +
                                        "disabled>" + discrepancy.getK() + "</textarea>\n" +
                                        "<br>Translated Name (make sure it's lowercase) <br>\n" +
                                        "<textarea rows=\"4\" cols=\"100\" name=\"translationname\" " +
                                        "form=\"commands\"></textarea>\n" +
                                        "<br>English Description<br>\n" +
                                        "<textarea rows=\"4\" cols=\"100\" name=\"originaldescription\" " +
                                        "form=\"commands\" disabled>" + discrepancy.getV() + "</textarea>\n" +
                                        "<br>Translated Description<br>\n" +
                                        "<textarea rows=\"4\" cols=\"100\" name=\"translationdescription\" " +
                                        "form=\"commands\"></textarea>\n" +
                                        "\n" +
                                        "<form action=\"/api/translate/submit\" id=\"commands\">\n" +
                                        "  <br>\n" +
                                        "<input type=\"hidden\" name=\"identifier\" value=\"" + discrepancy.getK() +
                                        "\">  <input type=\"hidden\" name=\"language\" value=\"" + LangFactory
                                        .getName(language) + "\"><input type=\"hidden\" name=\"type\" " +
                                        "value=\"commands\"><input type=\"submit\" value=\"Add Translation\">\n" +
                                        "</form>\n" +
                                        "<br>\n" +
                                        "</body>\n" +
                                        "</html>\n";
                            }
                            else
                                return "No untranslated commands for this language! Try phrases or subcommand " +
                                        "translations.";
                        }
                        else if (splats[1].equalsIgnoreCase("subcommands")) {
                            ArrayList<Quintet<String, String, String, String, String>> discrepancies =
                                    getSubCommandDiscrepancies(language);
                            if (discrepancies.size() > 0) {
                                Quintet<String, String, String, String, String> discrepancy = discrepancies.get(0);
                                String cmdId = discrepancy.getA();
                                String id = discrepancy.getB();
                                return "<!DOCTYPE html>\n" +
                                        "<html>\n" +
                                        "<body>\n" +
                                        "\n" +
                                        "<h2>Translate Subcommands for " + LangFactory.getName(language) + " (" +
                                        discrepancies.size() + " subcommands left to translate)</h2><br>\n" +
                                        "\n" +
                                        "Subcommand English Name<br>\n" +
                                        "<textarea rows=\"4\" cols=\"100\" name=\"originalname\" form=\"subcommands\"" +
                                        " disabled>" + discrepancy.getB() + "</textarea>\n" +
                                        "<br>Translated Subcommand Name (make sure it's lowercase) <br>\n" +
                                        "<textarea rows=\"4\" cols=\"100\" name=\"translationname\" " +
                                        "form=\"subcommands\"></textarea>\n" +
                                        "<br>English Syntax<br>\n" +
                                        "<textarea rows=\"4\" cols=\"100\" name=\"originalsyntax\" " +
                                        "form=\"subcommands\" disabled>" + discrepancy.getD() + "</textarea>\n" +
                                        "<br>Translated Syntax<br>\n" +
                                        "<textarea rows=\"4\" cols=\"100\" name=\"translationsyntax\" " +
                                        "form=\"subcommands\"></textarea>\n" +
                                        "<br>English Description<br><textarea rows=\"4\" cols=\"100\" " +
                                        "name=\"originaldescription\" form=\"subcommands\" disabled>" + discrepancy
                                        .getE() + "</textarea>\n" +
                                        "<br>Translated Description<br>\n" +
                                        "<textarea rows=\"4\" cols=\"100\" name=\"translationdescription\" " +
                                        "form=\"subcommands\"></textarea>\n" +
                                        "\n" +
                                        "<form action=\"/api/translate/submit\" id=\"subcommands\">\n" +
                                        "  <br>\n" +
                                        "<input type=\"hidden\" name=\"identifier\" value=\"" + id + "\"><input " +
                                        "type=\"hidden\" name=\"commandidentifier\" value=\"" + cmdId + "\"><input " +
                                        "type=\"hidden\" name=\"language\" value=\"" + LangFactory.getName(language)
                                        + "\"><input type=\"hidden\" name=\"type\" value=\"subcommands\"><input " +
                                        "type=\"submit\" value=\"Add Translation\">\n" +
                                        "</form>\n" +
                                        "<br>\n" +
                                        "</body>\n" +
                                        "</html>\n";
                            }
                            else
                                return "No untranslated commands for this language! Try phrases or subcommand " +
                                        "translations.";
                        }
                        else return incorrectArgs;
                    }
                    catch (SQLException ex) {
                        new BotException(ex);
                        return "The database returned an error which has been logged";
                    }
                }
                else return "Incorrect language specified";
            }
            else return "You're not a translator!";
        }
        else return incorrectArgs;
    }

    private static String getDefaultImage() {
        String avatarUrl = null;
        int random = new Random().nextInt(4);

        if (random == 0)
            avatarUrl = "https://i.gyazo.com/41c854b8f366402cd75a4450becd178a.jpg";
        else if (random == 1)
            avatarUrl = "https://i.gyazo.com/5b07238cf478a02c9565d28ed6bb2b1f.jpg";
        else if (random == 2)
            avatarUrl = "https://i.gyazo.com/65ab76aa4c70f3b7e85b1cfcc74370df.jpg";
        else if (random == 3)
            avatarUrl = "https://i.gyazo.com/249ad1d26af8b388ea3b42fc23f52daa.jpg";
        return avatarUrl;
    }
}
