package org.zorzanotes;

import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownEditor extends VBox {

    private final TextArea editor =
            new TextArea();

    private final ComboBox<String> fontFamily =
            new ComboBox<>();

    private final ComboBox<Integer> fontSize =
            new ComboBox<>();

    private final FlowPane linkPane =
            new FlowPane();

    private HostServices hostServices;

    private Runnable deleteNoteHandler;

    private final HBox searchMatchBar = new HBox(8);
    private final Button previousMatch = new Button("‹ Previous");
    private final Button nextMatch = new Button("Next ›");
    private final Label searchMatchLabel = new Label();

    private final List<int[]> searchMatches = new ArrayList<>();
    private int currentSearchMatch = -1;
    private String activeSearchQuery = "";

    // -------------------------------------------------------------
    // URL DETECTION
    // -------------------------------------------------------------

    private static final Pattern URL_PATTERN =
            Pattern.compile(
                    "https?://[^\\s<>\"']+",
                    Pattern.CASE_INSENSITIVE
            );

    // -------------------------------------------------------------
    // LIST DETECTION
    // -------------------------------------------------------------

    private static final Pattern BULLET_PATTERN =
            Pattern.compile(
                    "^(\\s*)([-*•])\\s+(.*)$"
            );

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile(
                    "^(\\s*)(\\d+)\\.\\s+(.*)$"
            );

    public MarkdownEditor() {

        setSpacing(10);

        // =========================================================
        // FONT FAMILY
        // =========================================================

        fontFamily.getItems().addAll(
                Font.getFamilies()
        );

        if (Font.getFamilies().contains("Arial")) {

            fontFamily.setValue("Arial");

        } else {

            fontFamily
                    .getSelectionModel()
                    .selectFirst();
        }

        fontFamily.setPrefWidth(170);

        // =========================================================
        // FONT SIZE
        // =========================================================

        fontSize.getItems().addAll(
                12,
                14,
                16,
                18,
                20,
                22,
                24,
                28,
                32,
                36,
                48
        );

        fontSize.setValue(16);
        fontSize.setPrefWidth(80);

        // =========================================================
        // BUTTONS
        // =========================================================

        Button bulletList =
                new Button("• List");

        bulletList.setTooltip(
                new Tooltip("Bulleted List")
        );

        Button numberList =
                new Button("1. List");

        numberList.setTooltip(
                new Tooltip("Numbered List")
        );

        Button undo =
                new Button("↶");

        undo.setTooltip(
                new Tooltip("Undo")
        );

        Button redo =
                new Button("↷");

        redo.setTooltip(
                new Tooltip("Redo")
        );

        Button deleteNote =
                new Button("Delete Note");

        deleteNote.setTooltip(
                new Tooltip("Delete the current note")
        );

        deleteNote.setOnAction(event -> {
            if (deleteNoteHandler != null) {
                deleteNoteHandler.run();
            }
        });

        // =========================================================
        // TOOLBAR
        // =========================================================

        HBox toolbar =
                new HBox(
                        6,
                        fontFamily,
                        fontSize,
                        new Separator(),
                        bulletList,
                        numberList,
                        new Separator(),
                        undo,
                        redo,
                        new Separator(),
                        deleteNote
                );

        toolbar.setPadding(
                new Insets(
                        0,
                        0,
                        5,
                        0
                )
        );

        // =========================================================
        // EDITOR
        // =========================================================

        editor.setWrapText(true);

        editor.setPromptText(
                "Start writing..."
        );

        VBox.setVgrow(
                editor,
                Priority.ALWAYS
        );

        updateEditorFont();

        // =========================================================
        // LINK AREA
        // =========================================================

        linkPane.setHgap(10);
        linkPane.setVgap(5);

        linkPane.setPadding(
                new Insets(
                        4,
                        0,
                        4,
                        0
                )
        );

        linkPane.setVisible(false);
        linkPane.setManaged(false);

        // =========================================================
        // FONT EVENTS
        // =========================================================

        fontFamily.setOnAction(
                event ->
                        updateEditorFont()
        );

        fontSize.setOnAction(
                event ->
                        updateEditorFont()
        );

        // =========================================================
        // UNDO / REDO
        // =========================================================

        undo.setOnAction(
                event ->
                        editor.undo()
        );

        redo.setOnAction(
                event ->
                        editor.redo()
        );

        // =========================================================
        // LIST BUTTONS
        // =========================================================

        bulletList.setOnAction(
                event ->
                        createBulletList()
        );

        numberList.setOnAction(
                event ->
                        createNumberedList()
        );

        // =========================================================
        // AUTO CONTINUE LISTS
        // =========================================================

        editor.setOnKeyPressed(event -> {

            if (event.getCode() != KeyCode.ENTER) {
                return;
            }

            if (event.isShiftDown()) {
                return;
            }

            if (continueList()) {
                event.consume();
            }
        });

        // =========================================================
        // AUTOMATIC URL DETECTION
        // =========================================================

        editor.textProperty()
                .addListener(
                        (observable,
                         oldValue,
                         newValue) -> {

                            updateLinks(newValue);
                        });

        // =========================================================
        // SEARCH MATCH NAVIGATION
        // =========================================================

        searchMatchBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        searchMatchBar.getChildren().addAll(
                previousMatch,
                searchMatchLabel,
                nextMatch
        );
        searchMatchBar.setVisible(false);
        searchMatchBar.setManaged(false);

        previousMatch.setOnAction(event -> previousSearchMatch());
        nextMatch.setOnAction(event -> nextSearchMatch());

        // =========================================================
        // LAYOUT
        // =========================================================

        getChildren().addAll(
                toolbar,
                searchMatchBar,
                editor,
                linkPane
        );
    }

    // =============================================================
    // HOST SERVICES
    // =============================================================

    public void setHostServices(
            HostServices hostServices) {

        this.hostServices =
                hostServices;

        updateLinks(
                editor.getText()
        );
    }

    // =============================================================
    // URL DETECTION
    // =============================================================

    private void updateLinks(
            String text) {

        linkPane.getChildren()
                .clear();

        if (text == null ||
                text.isBlank()) {

            linkPane.setVisible(false);
            linkPane.setManaged(false);

            return;
        }

        Matcher matcher =
                URL_PATTERN.matcher(text);

        Set<String> urls =
                new LinkedHashSet<>();

        while (matcher.find()) {

            String url =
                    cleanUrl(
                            matcher.group()
                    );

            if (!url.isBlank()) {
                urls.add(url);
            }
        }

        if (urls.isEmpty()) {

            linkPane.setVisible(false);
            linkPane.setManaged(false);

            return;
        }

        for (String url : urls) {

            Hyperlink hyperlink =
                    new Hyperlink(url);

            hyperlink.setOnAction(event ->
                    openUrl(url)
            );

            linkPane.getChildren()
                    .add(hyperlink);
        }

        linkPane.setVisible(true);
        linkPane.setManaged(true);
    }

    // =============================================================
    // CLEAN URL
    // =============================================================

    private String cleanUrl(
            String url) {

        String cleaned =
                url;

        while (cleaned.endsWith(".") ||
                cleaned.endsWith(",") ||
                cleaned.endsWith(";") ||
                cleaned.endsWith(":") ||
                cleaned.endsWith("!") ||
                cleaned.endsWith("?") ||
                cleaned.endsWith(")")) {

            cleaned =
                    cleaned.substring(
                            0,
                            cleaned.length() - 1
                    );
        }

        return cleaned;
    }

    // =============================================================
    // OPEN URL
    // =============================================================

    private void openUrl(
            String url) {

        if (hostServices == null) {
            return;
        }

        hostServices.showDocument(
                url
        );
    }

    // =============================================================
    // FONT
    // =============================================================

    private void updateEditorFont() {

        String family =
                fontFamily.getValue();

        Integer size =
                fontSize.getValue();

        if (family == null ||
                size == null) {
            return;
        }

        editor.setStyle(
                "-fx-font-family: \"" +
                        family +
                        "\";" +
                        "-fx-font-size: " +
                        size +
                        "px;"
        );
    }

    // =============================================================
    // BULLET LIST
    // =============================================================

    private void createBulletList() {

        int start =
                editor.getSelection()
                        .getStart();

        int end =
                editor.getSelection()
                        .getEnd();

        if (start == end) {

            int lineStart =
                    findLineStart(start);

            editor.insertText(
                    lineStart,
                    "• "
            );

            editor.requestFocus();

            return;
        }

        String text =
                editor.getText();

        int lineStart =
                findLineStart(start);

        int lineEnd =
                findLineEnd(end);

        String block =
                text.substring(
                        lineStart,
                        lineEnd
                );

        String[] lines =
                block.split(
                        "\n",
                        -1
                );

        StringBuilder result =
                new StringBuilder();

        for (int i = 0;
             i < lines.length;
             i++) {

            result.append("• ")
                    .append(lines[i]);

            if (i <
                    lines.length - 1) {

                result.append("\n");
            }
        }

        editor.replaceText(
                lineStart,
                lineEnd,
                result.toString()
        );

        editor.requestFocus();
    }

    // =============================================================
    // NUMBERED LIST
    // =============================================================

    private void createNumberedList() {

        int start =
                editor.getSelection()
                        .getStart();

        int end =
                editor.getSelection()
                        .getEnd();

        if (start == end) {

            int lineStart =
                    findLineStart(start);

            editor.insertText(
                    lineStart,
                    "1. "
            );

            editor.requestFocus();

            return;
        }

        String text =
                editor.getText();

        int lineStart =
                findLineStart(start);

        int lineEnd =
                findLineEnd(end);

        String block =
                text.substring(
                        lineStart,
                        lineEnd
                );

        String[] lines =
                block.split(
                        "\n",
                        -1
                );

        StringBuilder result =
                new StringBuilder();

        for (int i = 0;
             i < lines.length;
             i++) {

            result.append(i + 1)
                    .append(". ")
                    .append(lines[i]);

            if (i <
                    lines.length - 1) {

                result.append("\n");
            }
        }

        editor.replaceText(
                lineStart,
                lineEnd,
                result.toString()
        );

        editor.requestFocus();
    }

    // =============================================================
    // CONTINUE LIST
    // =============================================================

    private boolean continueList() {

        int caret =
                editor.getCaretPosition();

        String text =
                editor.getText();

        int lineStart =
                findLineStart(caret);

        String currentLine =
                text.substring(
                        lineStart,
                        caret
                );

        // ---------------------------------------------------------
        // BULLET
        // ---------------------------------------------------------

        Matcher bulletMatcher =
                BULLET_PATTERN.matcher(
                        currentLine
                );

        if (bulletMatcher.matches()) {

            String indentation =
                    bulletMatcher.group(1);

            String bullet =
                    bulletMatcher.group(2);

            String contents =
                    bulletMatcher.group(3);

            if (contents.trim().isEmpty()) {

                editor.replaceText(
                        lineStart,
                        caret,
                        ""
                );

                editor.insertText(
                        lineStart,
                        "\n"
                );

                editor.positionCaret(
                        lineStart + 1
                );

                return true;
            }

            editor.insertText(
                    caret,
                    "\n" +
                            indentation +
                            bullet +
                            " "
            );

            return true;
        }

        // ---------------------------------------------------------
        // NUMBERED
        // ---------------------------------------------------------

        Matcher numberMatcher =
                NUMBER_PATTERN.matcher(
                        currentLine
                );

        if (numberMatcher.matches()) {

            String indentation =
                    numberMatcher.group(1);

            int currentNumber =
                    Integer.parseInt(
                            numberMatcher.group(2)
                    );

            String contents =
                    numberMatcher.group(3);

            if (contents.trim().isEmpty()) {

                editor.replaceText(
                        lineStart,
                        caret,
                        ""
                );

                editor.insertText(
                        lineStart,
                        "\n"
                );

                editor.positionCaret(
                        lineStart + 1
                );

                return true;
            }

            editor.insertText(
                    caret,
                    "\n" +
                            indentation +
                            (currentNumber + 1) +
                            ". "
            );

            return true;
        }

        return false;
    }

    // =============================================================
    // LINE START
    // =============================================================

    private int findLineStart(
            int position) {

        String text =
                editor.getText();

        if (position <= 0) {
            return 0;
        }

        int index =
                text.lastIndexOf(
                        '\n',
                        position - 1
                );

        return index + 1;
    }

    // =============================================================
    // LINE END
    // =============================================================

    private int findLineEnd(
            int position) {

        String text =
                editor.getText();

        int index =
                text.indexOf(
                        '\n',
                        position
                );

        if (index == -1) {
            return text.length();
        }

        return index;
    }


    // =============================================================
    // SEARCH HIGHLIGHTING / NAVIGATION
    // =============================================================

    public int showSearchMatches(String query) {
        activeSearchQuery = query == null ? "" : query.trim();
        rebuildSearchMatches();

        if (searchMatches.isEmpty()) {
            searchMatchBar.setVisible(false);
            searchMatchBar.setManaged(false);
            editor.deselect();
            return 0;
        }

        searchMatchBar.setVisible(true);
        searchMatchBar.setManaged(true);
        currentSearchMatch = 0;
        selectCurrentSearchMatch();
        return searchMatches.size();
    }

    public void clearSearchMatches() {
        activeSearchQuery = "";
        searchMatches.clear();
        currentSearchMatch = -1;
        searchMatchBar.setVisible(false);
        searchMatchBar.setManaged(false);
        editor.deselect();
    }

    public int getSearchMatchCount() {
        return searchMatches.size();
    }

    private void rebuildSearchMatches() {
        searchMatches.clear();
        currentSearchMatch = -1;

        if (activeSearchQuery.isBlank()) {
            return;
        }

        String cleaned = activeSearchQuery
                .replaceAll("[^\\p{L}\\p{N}_]+", " ")
                .trim();

        if (cleaned.isBlank()) {
            return;
        }

        String text = editor.getText();
        Set<String> uniqueRanges = new LinkedHashSet<>();

        for (String term : cleaned.split("\\s+")) {
            if (term.isBlank()) {
                continue;
            }

            Pattern pattern = Pattern.compile(
                    "(?iu)(?<![\\p{L}\\p{N}_])" +
                            Pattern.quote(term) +
                            "[\\p{L}\\p{N}_]*"
            );

            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String key = matcher.start() + ":" + matcher.end();
                if (uniqueRanges.add(key)) {
                    searchMatches.add(new int[] {matcher.start(), matcher.end()});
                }
            }
        }

        searchMatches.sort((a, b) -> Integer.compare(a[0], b[0]));
    }

    private void previousSearchMatch() {
        if (searchMatches.isEmpty()) {
            return;
        }

        currentSearchMatch--;
        if (currentSearchMatch < 0) {
            currentSearchMatch = searchMatches.size() - 1;
        }
        selectCurrentSearchMatch();
    }

    private void nextSearchMatch() {
        if (searchMatches.isEmpty()) {
            return;
        }

        currentSearchMatch++;
        if (currentSearchMatch >= searchMatches.size()) {
            currentSearchMatch = 0;
        }
        selectCurrentSearchMatch();
    }

    private void selectCurrentSearchMatch() {
        if (currentSearchMatch < 0 || currentSearchMatch >= searchMatches.size()) {
            return;
        }

        int[] match = searchMatches.get(currentSearchMatch);
        editor.requestFocus();
        editor.selectRange(match[0], match[1]);

        searchMatchLabel.setText(
                (currentSearchMatch + 1) +
                        " of " +
                        searchMatches.size() +
                        " matches"
        );
    }

    // =============================================================
    // PUBLIC API
    // =============================================================

    public String getText() {

        return editor.getText();
    }

    public void setText(
            String text) {

        editor.setText(
                text == null
                        ? ""
                        : text
        );

        if (!activeSearchQuery.isBlank()) {
            rebuildSearchMatches();
        }
    }

    public void clear() {

        clearSearchMatches();
        editor.clear();
    }

    public void setEditorDisabled(
            boolean disabled) {

        editor.setDisable(
                disabled
        );
    }

    public TextArea getTextArea() {

        return editor;
    }

    public void requestEditorFocus() {

        editor.requestFocus();
    }

    public void setOnDeleteNote(
            Runnable deleteNoteHandler) {

        this.deleteNoteHandler =
                deleteNoteHandler;
    }
}