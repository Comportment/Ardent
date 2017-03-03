package Backend.Translation;

import java.util.ArrayList;

public class LangFactory {
    public static Language english = new Language("english", Language.Status.MATURE, null, null);

    public static Language french = new Language("french", Language.Status.MATURE, "fr", "French");

    public static Language turkish = new Language("turkish", Language.Status.DECENT, "tr", "Turkish");

    public static Language croatian = new Language("croatian", Language.Status.DECENT, "hr", "Croatian");

    public static Language romanian = new Language("romanian", Language.Status.DECENT, "ro", "Romanian");

    public static Language portugese = new Language("portugese", Language.Status.INFANCY, "pt-PT", "Portugese");

    public static Language german = new Language("german", Language.Status.MOST, "de", "German");

    public static Language cyrillicserbian = new Language("cyrillicserbian", Language.Status.MATURE, "sr", "Cyrillic_Serbian");

    public static Language spanish = new Language("spanish", Language.Status.INFANCY, "es", "Spanish");

    public static Language dutch = new Language("dutch", Language.Status.MATURE, "nl", "Dutch");

    public static Language polish = new Language("polish", Language.Status.INFANCY, "pl", "Polish");

    public static Language emoji = new Language("emoji", Language.Status.INFANCY, "emoji", "Emoji");

    public static ArrayList<Language> languages;

    /**
     * Get a language by its identifier
     *
     * @param s language identifier
     * @return language with the specified identifier
     */
    public static Language getLanguage(String s) {
        switch (s.toUpperCase()) {
            case "ENGLISH":
                return english;
            case "FRENCH":
                return french;
            case "TURKISH":
                return turkish;
            case "CROATIAN":
                return croatian;
            case "ROMANIAN":
                return romanian;
            case "PORTUGESE":
                return portugese;
            case "GERMAN":
                return german;
            case "CYRILLICSERBIAN":
                return cyrillicserbian;
            case "CYRILLIC_SERBIAN":
                return cyrillicserbian;
            case "SPANISH":
                return spanish;
            case "DUTCH":
                return dutch;
            case "POLISH":
                return polish;
            case "EMOJI":
                return emoji;
            default:
                return null;
        }
    }

    public static String getName(Language language) {
        if (language == english) return "English";
        else if (language == french) return "French";
        else if (language == turkish) return "Turkish";
        else if (language == croatian) return "Croatian";
        else if (language == romanian) return "Romanian";
        else if (language == portugese) return "Portugese";
        else if (language == german) return "German";
        else if (language == cyrillicserbian) return "Cyrillic_Serbian";
        else if (language == spanish) return "Spanish";
        else if (language == dutch) return "Dutch";
        else if (language == polish) return "Polish";
        else if (language == emoji) return "Emoji";
        else return null;
    }

}
