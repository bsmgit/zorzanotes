package org.zorzanotes;

import java.util.prefs.Preferences;

public class AppSettings {

    private static final Preferences preferences =
            Preferences.userNodeForPackage(AppSettings.class);

    private static final String DARK_MODE = "darkMode";
    private static final String TEXT_SIZE = "textSize";

    public enum TextSize {
        NORMAL,
        LARGE,
        EXTRA_LARGE
    }

    public static boolean isDarkMode() {
        return preferences.getBoolean(DARK_MODE, false);
    }

    public static void setDarkMode(boolean enabled) {
        preferences.putBoolean(DARK_MODE, enabled);
    }

    public static TextSize getTextSize() {
        String value = preferences.get(
                TEXT_SIZE,
                TextSize.NORMAL.name()
        );

        try {
            return TextSize.valueOf(value);
        } catch (IllegalArgumentException e) {
            return TextSize.NORMAL;
        }
    }

    public static void setTextSize(TextSize size) {
        preferences.put(
                TEXT_SIZE,
                size.name()
        );
    }
}