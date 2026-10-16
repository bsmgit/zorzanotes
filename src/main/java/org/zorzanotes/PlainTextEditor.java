
package org.zorzanotes;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

public final class PlainTextEditor extends VBox {

    private static final Pattern URLS = Pattern.compile(
            "(?i)(?:https?://|zorza://link/)[^\\s<>\\\"']+"
    );

    private final CodeArea text = new CodeArea();
    private final HostServices host;
    private final UrlShortener shortener;

    private boolean updatingStyles;

    public PlainTextEditor(
            HostServices host,
            UrlShortener shortener) {

        this.host = host;
        this.shortener = shortener;

        setSpacing(8);

        Button shorten = new Button("Shorten URL");
        Button undo = new Button("Undo");
        Button redo = new Button("Redo");

        shorten.setOnAction(e -> shortenAtCaret());
        undo.setOnAction(e -> text.undo());
        redo.setOnAction(e -> text.redo());

        text.setWrapText(true);

        text.getStylesheets().add(
                Objects.requireNonNull(
                        getClass().getResource("/zorza-links.css")
                ).toExternalForm()
        );

        text.textProperty().addListener(
                (obs, oldText, newText) -> refreshStyles()
        );

        text.setOnMouseClicked(event -> {

            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 1
                    && !event.isShiftDown()
                    && !event.isControlDown()
                    && !event.isMetaDown()) {

                Platform.runLater(() -> {

                    String url = urlAtCaret();

                    if (url != null) {
                        openUrl(url);
                    }
                });
            }
        });

        HBox toolbar = new HBox(
                8,
                shorten,
                undo,
                redo
        );

        getChildren().addAll(toolbar, text);

        VBox.setVgrow(text, Priority.ALWAYS);
    }

    private static String trimPunctuation(String value) {

        int end = value.length();

        while (end > 0
                && ".,;!?".indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }

        return value.substring(0, end);
    }

    private void refreshStyles() {

        if (updatingStyles) {
            return;
        }

        String content = text.getText();

        if (content.isEmpty()) {
            return;
        }

        StyleSpansBuilder<Collection<String>> builder =
                new StyleSpansBuilder<>();

        Matcher matcher = URLS.matcher(content);

        int last = 0;

        while (matcher.find()) {

            if (matcher.start() > last) {

                builder.add(
                        Collections.emptyList(),
                        matcher.start() - last
                );
            }

            String clean = trimPunctuation(matcher.group());

            if (!clean.isEmpty()) {

                builder.add(
                        Collections.singleton("url-link"),
                        clean.length()
                );
            }

            int punctuationLength =
                    matcher.group().length() - clean.length();

            if (punctuationLength > 0) {

                builder.add(
                        Collections.emptyList(),
                        punctuationLength
                );
            }

            last = matcher.end();
        }

        if (last < content.length()) {

            builder.add(
                    Collections.emptyList(),
                    content.length() - last
            );
        }

        StyleSpans<Collection<String>> styles =
                builder.create();

        updatingStyles = true;

        try {
            text.setStyleSpans(0, styles);
        } finally {
            updatingStyles = false;
        }
    }

    private Matcher matchAtCaret() {

        Matcher matcher = URLS.matcher(text.getText());

        int caret = text.getCaretPosition();

        while (matcher.find()) {

            String clean = trimPunctuation(matcher.group());

            if (matcher.start() <= caret
                    && caret < matcher.start() + clean.length()) {

                return matcher;
            }
        }

        return null;
    }

    private String urlAtCaret() {

        Matcher matcher = matchAtCaret();

        return matcher == null
                ? null
                : trimPunctuation(matcher.group());
    }

    private void openUrl(String url) {

        try {

            String destination =
                    url.startsWith("zorza://link/")
                            ? shortener.resolve(url)
                            : url;

            URI uri = URI.create(destination);

            if (!("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {

                throw new IllegalArgumentException(
                        "Only valid HTTP or HTTPS URLs can be opened."
                );
            }

            host.showDocument(uri.toString());

        } catch (Exception ex) {

            showError(ex);
        }
    }

    private void shortenAtCaret() {

        Matcher matcher = matchAtCaret();

        if (matcher == null) {

            showInfo(
                    "Place the cursor inside a URL first."
            );

            return;
        }

        String original =
                trimPunctuation(matcher.group());

        if (original.startsWith("zorza://link/")) {

            showInfo("This URL is already shortened.");
            return;
        }

        try {

            String alias = shortener.shorten(original);

            int start = matcher.start();

            text.replaceText(
                    start,
                    start + original.length(),
                    alias
            );

            text.moveTo(start + alias.length());

            refreshStyles();

        } catch (Exception ex) {

            showError(ex);
        }
    }

    public CodeArea area() {
        return text;
    }

    public void setEditorFont(
            String family,
            int size) {

        String safeFamily = family
                .replace("\\", "\\\\")
                .replace("'", "\\'");

        text.setStyle(
                "-fx-font-family: '" + safeFamily + "';"
                        + "-fx-font-size: " + size + "px;"
        );
    }

    public void showMatch(String query) {

        if (query == null || query.isBlank()) {
            return;
        }

        int index = text.getText()
                .toLowerCase(Locale.ROOT)
                .indexOf(
                        query.toLowerCase(Locale.ROOT)
                );

        if (index >= 0) {

            text.selectRange(
                    index,
                    index + query.length()
            );

            text.requestFocus();
        }
    }

    private void showInfo(String message) {

        new Alert(
                Alert.AlertType.INFORMATION,
                message,
                ButtonType.OK
        ).showAndWait();
    }

    private void showError(Exception ex) {

        ex.printStackTrace();

        new Alert(
                Alert.AlertType.ERROR,
                ex.getMessage() == null
                        ? ex.toString()
                        : ex.getMessage(),
                ButtonType.OK
        ).showAndWait();
    }
}
