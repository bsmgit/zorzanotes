
package org.zorzanotes;

import java.util.prefs.Preferences;

public final class AppSettings {

    private static final Preferences prefs =
            Preferences.userNodeForPackage(AppSettings.class);

    public enum TextSize {
        NORMAL, LARGE, EXTRA_LARGE
    }

    private AppSettings() {}

    public static boolean isDarkMode() {
        return prefs.getBoolean("darkMode", false);
    }

    public static void setDarkMode(boolean value) {
        prefs.putBoolean("darkMode", value);
    }

    public static TextSize getTextSize() {
        try {
            return TextSize.valueOf(
                    prefs.get("textSize", "NORMAL"));
        } catch (IllegalArgumentException ex) {
            return TextSize.NORMAL;
        }
    }

    public static void setTextSize(TextSize size) {
        prefs.put("textSize", size.name());
    }

    public static String getNoteFont() {
        return prefs.get("noteFont", "System");
    }

    public static void setNoteFont(String font) {
        prefs.put("noteFont", font);
    }

    public static int getNoteFontSize() {
        return prefs.getInt("noteFontSize", 16);
    }

    public static void setNoteFontSize(int size) {
        prefs.putInt("noteFontSize", size);
    }

    public static String getAppFont() {
        return prefs.get("appFont", "System");
    }

    public static void setAppFont(String font) {
        prefs.put("appFont", font);
    }

    public static int getAppFontSize() {
        return prefs.getInt("appFontSize", 13);
    }

    public static void setAppFontSize(int size) {
        prefs.putInt("appFontSize", size);
    }
}
