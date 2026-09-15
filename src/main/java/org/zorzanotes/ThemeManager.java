package org.zorzanotes;

import javafx.scene.Scene;

public class ThemeManager {

    public static void apply(Scene scene) {

        scene.getStylesheets().clear();

        String stylesheet;

        if (AppSettings.isDarkMode()) {
            stylesheet = "/zorza-dark.css";
        } else {
            stylesheet = "/zorza-light.css";
        }

        scene.getStylesheets().add(
                ThemeManager.class
                        .getResource(stylesheet)
                        .toExternalForm()
        );

        applyTextSize(scene);
    }

    private static void applyTextSize(Scene scene) {

        scene.getRoot()
                .getStyleClass()
                .removeAll(
                        "text-normal",
                        "text-large",
                        "text-extra-large"
                );

        switch (AppSettings.getTextSize()) {

            case NORMAL ->
                    scene.getRoot()
                            .getStyleClass()
                            .add("text-normal");

            case LARGE ->
                    scene.getRoot()
                            .getStyleClass()
                            .add("text-large");

            case EXTRA_LARGE ->
                    scene.getRoot()
                            .getStyleClass()
                            .add("text-extra-large");
        }
    }
}