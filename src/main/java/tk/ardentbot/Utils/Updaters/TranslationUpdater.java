package tk.ardentbot.Utils.Updaters;

/**
 * Downloads and inserts phrase translations on a loop
 */
@Deprecated
public abstract class TranslationUpdater implements Runnable {
    /*private Statement statement = Ardent.conn.createStatement();
    private Credentials credentials = new Credentials(PhraseUpdater.BASE_URL, PhraseUpdater.PROJECT_IDENTIFIER,
            PhraseUpdater.PROJECT_KEY, PhraseUpdater.ACCOUNT_KEY);
    private CrowdinApiClient crwdn = new Crwdn();

    public TranslationUpdater() throws SQLException {
    }

    @Override
    public void run() {
        try {
            for (Language l : shard0.crowdinLanguages) {
                CrowdinApiParametersBuilder parameters = new CrowdinApiParametersBuilder();

                File temp = new File("null" + l.getCrowdinLangCode() + ".zip");
                if (temp.exists()) temp.delete();

                parameters.downloadPackage(l.getCrowdinLangCode());
                crwdn.downloadTranslations(credentials, parameters);
                File downloaded = new File("null" + l.getCrowdinLangCode() + ".zip");
                ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(downloaded));
                zipInputStream.getNextEntry();

                CSVParser parser = new CSVParser(new StringReader(IOUtils.toString(zipInputStream)), CSVFormat
                        .DEFAULT.withDelimiter(';'));

                parser.forEach(record -> {
                    String context = record.get(0);
                    String[] split = context.split("\\|");

                    String commandId = split[0];
                    String translationId = split[1];

                    String original = record.get(1);
                    String translation = record.get(2);

                    if (!original.equalsIgnoreCase(translation)) {
                        try {
                            DatabaseAction queryTranslations = new DatabaseAction("SELECT * FROM Translations WHERE " +
                                    "CommandIdentifier=? AND ID=? AND Language=?").set(commandId).set(translationId)
                                    .set(l.getIdentifier());
                            ResultSet set = queryTranslations.request();
                            if (!set.next()) {
                                new DatabaseAction("INSERT INTO Translations VALUES (?,?,?,?,?").set(commandId)
                                        .set(translation).set(translationId).set(l.getIdentifier()).set(0).update();
                                p("INSERTED VALUES: Language: " + l.getIdentifier() + " | BaseCommand ID: " +
                                        commandId +
                                        " | TranslationModel ID: " + translationId + " | TranslationModel: " + translation);
                            }
                            queryTranslations.update();
                        }
                        catch (SQLException e) {
                            e.printStackTrace();
                        }

                    }
                });
            }
        }
        catch (Exception ex) {
            new BotException(ex);
        }
    }

    private void p(String s) {
        botLogsShard.botLogs.sendMessage(s).queue();
    }*/
}
