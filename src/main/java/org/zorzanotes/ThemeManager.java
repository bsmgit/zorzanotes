
package org.zorzanotes;

import javafx.scene.Scene;
import javafx.scene.Parent;

public final class ThemeManager {

    private ThemeManager() {}

    public static void apply(Scene scene) {
        scene.getStylesheets().clear();

        String stylesheet = AppSettings.isDarkMode()
                ? "/zorza-dark.css"
                : "/zorza-light.css";

        var resource =
                ThemeManager.class.getResource(stylesheet);

        if (resource == null) {
            throw new IllegalStateException(
                    "Missing stylesheet: " + stylesheet);
        }

        scene.getStylesheets().add(
                resource.toExternalForm());

        applyApplicationFont(scene.getRoot());
    }

    public static void applyApplicationFont(Parent root) {
        String family = AppSettings.getAppFont()
                .replace("\\", "\\\\")
                .replace("'", "\\'");

        int size = Math.max(10,
                Math.min(28, AppSettings.getAppFontSize()));

        root.setStyle(
                "-fx-font-family: '" + family + "';" +
                        "-fx-font-size: " + size + "px;");
    }
}
