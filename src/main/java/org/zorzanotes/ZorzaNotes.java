package org.zorzanotes;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ZorzaNotes extends Application {

    private final NotebookRepository notebookRepository =
            new NotebookRepository();

    private final NoteRepository noteRepository =
            new NoteRepository();

    private final ExportImportService exportImportService =
            new ExportImportService(
                    notebookRepository,
                    noteRepository
            );

    private Note currentNote;

    private final PauseTransition autoSaveTimer =
            new PauseTransition(
                    Duration.millis(700)
            );

    private final PauseTransition searchTimer =
            new PauseTransition(
                    Duration.millis(200)
            );

    private boolean loadingNote = false;

    /*
     * When true, notebook/note selection listeners do nothing.
     *
     * This lets global search navigation change both selections
     * atomically without temporarily clearing the editor.
     */
    private boolean programmaticNavigation = false;

    @Override
    public void start(Stage stage) {

        Database.initialize();

        // =========================================================
        // NOTEBOOKS
        // =========================================================

        Label notebooksTitle =
                new Label("NOTEBOOKS");

        ListView<Notebook> notebooks =
                new ListView<>();

        notebooks.getItems().addAll(
                notebookRepository.findAll()
        );

        Button newNotebook =
                new Button("+ New Notebook");

        Button deleteNotebook =
                new Button("Delete");

        deleteNotebook.setDisable(true);

        HBox notebookButtons =
                new HBox(
                        8,
                        newNotebook,
                        deleteNotebook
                );

        VBox notebookPane =
                new VBox(
                        10,
                        notebooksTitle,
                        notebooks,
                        notebookButtons
                );

        notebookPane.setPadding(
                new Insets(20)
        );

        notebookPane.setPrefWidth(220);

        VBox.setVgrow(
                notebooks,
                Priority.ALWAYS
        );

        // =========================================================
        // NOTES
        // =========================================================

        Label notesTitle =
                new Label("NOTES");

        ListView<Note> notes =
                new ListView<>();

        Button newNote =
                new Button("+ New Note");

        Button deleteNote =
                new Button("Delete");

        deleteNote.setDisable(true);

        HBox noteButtons =
                new HBox(
                        8,
                        newNote,
                        deleteNote
                );

        VBox notesPane =
                new VBox(
                        10,
                        notesTitle,
                        notes,
                        noteButtons
                );

        notesPane.setPadding(
                new Insets(20)
        );

        notesPane.setPrefWidth(300);

        VBox.setVgrow(
                notes,
                Priority.ALWAYS
        );

        // =========================================================
        // EDITOR
        // =========================================================

        TextField noteTitle =
                new TextField();

        noteTitle.setPromptText(
                "Note title"
        );

        noteTitle.getStyleClass()
                .add("note-title");

        MarkdownEditor editor =
                new MarkdownEditor();

        editor.setHostServices(
                getHostServices()
        );

        Label status =
                new Label(
                        "Select a note"
                );

        VBox editorPane =
                new VBox(
                        12,
                        noteTitle,
                        editor,
                        status
                );

        editorPane.setPadding(
                new Insets(25)
        );

        VBox.setVgrow(
                editor,
                Priority.ALWAYS
        );

        noteTitle.setDisable(true);

        editor.setEditorDisabled(true);

        // =========================================================
        // AUTOSAVE
        // =========================================================

        autoSaveTimer.setOnFinished(event -> {

            if (currentNote == null ||
                    loadingNote ||
                    programmaticNavigation) {

                return;
            }

            currentNote.setTitle(
                    cleanTitle(
                            noteTitle.getText()
                    )
            );

            currentNote.setBody(
                    editor.getText()
            );

            noteRepository.update(
                    currentNote
            );

            notes.refresh();

            status.setText(
                    "Saved locally"
            );
        });

        noteTitle.textProperty()
                .addListener(
                        (observable,
                         oldValue,
                         newValue) -> {

                            if (loadingNote ||
                                    programmaticNavigation ||
                                    currentNote == null) {

                                return;
                            }

                            status.setText(
                                    "Saving..."
                            );

                            autoSaveTimer
                                    .playFromStart();
                        });

        editor.getTextArea()
                .textProperty()
                .addListener(
                        (observable,
                         oldValue,
                         newValue) -> {

                            if (loadingNote ||
                                    programmaticNavigation ||
                                    currentNote == null) {

                                return;
                            }

                            status.setText(
                                    "Saving..."
                            );

                            autoSaveTimer
                                    .playFromStart();
                        });

        // =========================================================
        // NOTEBOOK SELECTION
        // =========================================================

        notebooks.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable,
                         oldNotebook,
                         selectedNotebook) -> {

                            if (programmaticNavigation) {
                                return;
                            }

                            deleteNotebook.setDisable(
                                    selectedNotebook == null
                            );

                            saveImmediately(
                                    noteTitle,
                                    editor
                            );

                            currentNote = null;

                            notes.getSelectionModel()
                                    .clearSelection();

                            notes.getItems()
                                    .clear();

                            clearEditor(
                                    noteTitle,
                                    editor
                            );

                            deleteNote.setDisable(
                                    true
                            );

                            if (selectedNotebook != null) {

                                notes.getItems()
                                        .setAll(
                                                noteRepository
                                                        .findByNotebook(
                                                                selectedNotebook
                                                                        .getId()
                                                        )
                                        );

                                status.setText(
                                        "Notebook selected"
                                );

                            } else {

                                status.setText(
                                        "Select a notebook"
                                );
                            }
                        });

        // =========================================================
        // NOTE SELECTION
        // =========================================================

        notes.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable,
                         oldNote,
                         selectedNote) -> {

                            if (programmaticNavigation) {
                                return;
                            }

                            if (oldNote != null &&
                                    oldNote == currentNote) {

                                saveImmediately(
                                        noteTitle,
                                        editor
                                );
                            }

                            loadNote(
                                    selectedNote,
                                    noteTitle,
                                    editor,
                                    deleteNote,
                                    status
                            );
                        });

        // =========================================================
        // NEW NOTEBOOK
        // =========================================================

        newNotebook.setOnAction(event -> {

            TextInputDialog dialog =
                    new TextInputDialog();

            dialog.initOwner(stage);

            dialog.setTitle(
                    "New Notebook"
            );

            dialog.setHeaderText(
                    "Create a notebook"
            );

            dialog.setContentText(
                    "Name:"
            );

            dialog.showAndWait()
                    .ifPresent(name -> {

                        String cleanName =
                                name.trim();

                        if (cleanName.isEmpty()) {
                            return;
                        }

                        Notebook notebook =
                                notebookRepository
                                        .create(
                                                cleanName
                                        );

                        notebooks.getItems()
                                .add(notebook);

                        notebooks
                                .getSelectionModel()
                                .select(notebook);
                    });
        });

        // =========================================================
        // DELETE NOTEBOOK
        // =========================================================

        deleteNotebook.setOnAction(event -> {

            Notebook selectedNotebook =
                    notebooks
                            .getSelectionModel()
                            .getSelectedItem();

            if (selectedNotebook == null) {
                return;
            }

            Alert confirmation =
                    new Alert(
                            Alert.AlertType.CONFIRMATION
                    );

            confirmation.initOwner(stage);

            confirmation.setTitle(
                    "Delete Notebook"
            );

            confirmation.setHeaderText(
                    "Delete \"" +
                            selectedNotebook.getName() +
                            "\"?"
            );

            confirmation.setContentText(
                    "All notes inside this notebook will also be permanently deleted."
            );

            confirmation.showAndWait()
                    .ifPresent(result -> {

                        if (result != ButtonType.OK) {
                            return;
                        }

                        autoSaveTimer.stop();

                        currentNote = null;

                        notebookRepository.delete(
                                selectedNotebook
                        );

                        notebooks.getItems()
                                .remove(
                                        selectedNotebook
                                );

                        notes.getItems()
                                .clear();

                        clearEditor(
                                noteTitle,
                                editor
                        );

                        deleteNote.setDisable(
                                true
                        );

                        deleteNotebook.setDisable(
                                true
                        );

                        status.setText(
                                "Notebook deleted"
                        );
                    });
        });

        // =========================================================
        // NEW NOTE
        // =========================================================

        newNote.setOnAction(event -> {

            Notebook selectedNotebook =
                    notebooks
                            .getSelectionModel()
                            .getSelectedItem();

            if (selectedNotebook == null) {

                status.setText(
                        "Select a notebook first"
                );

                return;
            }

            saveImmediately(
                    noteTitle,
                    editor
            );

            Note note =
                    noteRepository.create(
                            selectedNotebook
                                    .getId()
                    );

            notes.getItems()
                    .add(
                            0,
                            note
                    );

            notes.getSelectionModel()
                    .select(note);

            noteTitle.requestFocus();

            noteTitle.selectAll();
        });

        // =========================================================
        // DELETE NOTE
        // =========================================================

        deleteNote.setOnAction(event -> {

            Note selectedNote =
                    notes
                            .getSelectionModel()
                            .getSelectedItem();

            if (selectedNote == null) {
                return;
            }

            Alert confirmation =
                    new Alert(
                            Alert.AlertType.CONFIRMATION
                    );

            confirmation.initOwner(stage);

            confirmation.setTitle(
                    "Delete Note"
            );

            confirmation.setHeaderText(
                    "Delete \"" +
                            selectedNote.getTitle() +
                            "\"?"
            );

            confirmation.setContentText(
                    "This note will be permanently deleted."
            );

            confirmation.showAndWait()
                    .ifPresent(result -> {

                        if (result != ButtonType.OK) {
                            return;
                        }

                        autoSaveTimer.stop();

                        currentNote = null;

                        noteRepository.delete(
                                selectedNote
                        );

                        notes.getItems()
                                .remove(
                                        selectedNote
                                );

                        clearEditor(
                                noteTitle,
                                editor
                        );

                        deleteNote.setDisable(
                                true
                        );

                        status.setText(
                                "Note deleted"
                        );
                    });
        });

        // =========================================================
        // GLOBAL SEARCH
        // =========================================================

        TextField globalSearch =
                new TextField();

        globalSearch.setPromptText(
                "Search all notes..."
        );

        globalSearch.setPrefWidth(
                330
        );

        searchTimer.setOnFinished(event -> {

            String query =
                    globalSearch
                            .getText()
                            .trim();

            if (query.isEmpty()) {

                restoreSelectedNotebookNotes(
                        notebooks,
                        notes,
                        notesTitle
                );

                return;
            }

            /*
             * Make sure the FTS5 index contains anything
             * the user has just typed into the current note.
             */
            saveImmediately(
                    noteTitle,
                    editor
            );

            try {

                List<Note> results =
                        noteRepository
                                .searchAll(query);

                /*
                 * Changing the ListView contents can change its
                 * selection. Suppress the normal note listener
                 * while we display temporary search results.
                 */
                programmaticNavigation = true;

                try {

                    notes.getSelectionModel()
                            .clearSelection();

                    notes.getItems()
                            .setAll(results);

                } finally {

                    programmaticNavigation = false;
                }

                notesTitle.setText(
                        "SEARCH RESULTS (" +
                                results.size() +
                                ")"
                );

                status.setText(
                        results.size() +
                                " search result" +
                                (results.size() == 1
                                        ? ""
                                        : "s")
                );

            } catch (RuntimeException e) {

                status.setText(
                        "Search failed"
                );

                showError(
                        stage,
                        "Search Failed",
                        e
                );
            }
        });

        globalSearch.textProperty()
                .addListener(
                        (observable,
                         oldValue,
                         newValue) ->

                                searchTimer
                                        .playFromStart()
                );

        // =========================================================
        // OPEN SEARCH RESULT WITH MOUSE
        // =========================================================

        notes.setOnMouseClicked(event -> {

            if (globalSearch
                    .getText()
                    .isBlank()) {

                return;
            }

            Note result =
                    notes
                            .getSelectionModel()
                            .getSelectedItem();

            if (result == null) {
                return;
            }

            openSearchResult(
                    result,
                    notebooks,
                    notes,
                    noteTitle,
                    editor,
                    deleteNote,
                    deleteNotebook,
                    status,
                    globalSearch,
                    notesTitle
            );
        });

        // =========================================================
        // OPEN SEARCH RESULT WITH ENTER
        // =========================================================

        notes.setOnKeyPressed(event -> {

            if (event.getCode() !=
                    KeyCode.ENTER) {

                return;
            }

            if (globalSearch
                    .getText()
                    .isBlank()) {

                return;
            }

            Note result =
                    notes
                            .getSelectionModel()
                            .getSelectedItem();

            if (result == null) {
                return;
            }

            openSearchResult(
                    result,
                    notebooks,
                    notes,
                    noteTitle,
                    editor,
                    deleteNote,
                    deleteNotebook,
                    status,
                    globalSearch,
                    notesTitle
            );

            event.consume();
        });

        /*
         * Pressing Enter while still in the search box opens
         * the selected result, or the first result if nothing
         * has been selected yet.
         */
        globalSearch.setOnAction(event -> {

            if (notes.getItems()
                    .isEmpty()) {

                return;
            }

            Note result =
                    notes
                            .getSelectionModel()
                            .getSelectedItem();

            if (result == null) {

                result =
                        notes.getItems()
                                .getFirst();
            }

            openSearchResult(
                    result,
                    notebooks,
                    notes,
                    noteTitle,
                    editor,
                    deleteNote,
                    deleteNotebook,
                    status,
                    globalSearch,
                    notesTitle
            );
        });

        // =========================================================
        // FILE MENU
        // =========================================================

        Menu fileMenu =
                new Menu("File");

        MenuItem exportAll =
                new MenuItem(
                        "Export All to Markdown..."
                );

        MenuItem importMarkdown =
                new MenuItem(
                        "Import Markdown..."
                );

        MenuItem quit =
                new MenuItem("Quit");

        fileMenu.getItems()
                .addAll(
                        exportAll,
                        importMarkdown,
                        new SeparatorMenuItem(),
                        quit
                );

        // =========================================================
        // SETTINGS MENU
        // =========================================================

        Menu settingsMenu =
                new Menu("Settings");

        CheckMenuItem darkMode =
                new CheckMenuItem(
                        "Dark Mode"
                );

        darkMode.setSelected(
                AppSettings.isDarkMode()
        );

        Menu textSizeMenu =
                new Menu(
                        "Application Text Size"
                );

        RadioMenuItem normalText =
                new RadioMenuItem(
                        "Normal"
                );

        RadioMenuItem largeText =
                new RadioMenuItem(
                        "Large"
                );

        RadioMenuItem extraLargeText =
                new RadioMenuItem(
                        "Extra Large"
                );

        ToggleGroup textSizeGroup =
                new ToggleGroup();

        normalText.setToggleGroup(
                textSizeGroup
        );

        largeText.setToggleGroup(
                textSizeGroup
        );

        extraLargeText.setToggleGroup(
                textSizeGroup
        );

        switch (AppSettings.getTextSize()) {

            case NORMAL ->
                    normalText.setSelected(
                            true
                    );

            case LARGE ->
                    largeText.setSelected(
                            true
                    );

            case EXTRA_LARGE ->
                    extraLargeText.setSelected(
                            true
                    );
        }

        textSizeMenu.getItems()
                .addAll(
                        normalText,
                        largeText,
                        extraLargeText
                );

        settingsMenu.getItems()
                .addAll(
                        darkMode,
                        new SeparatorMenuItem(),
                        textSizeMenu
                );

        MenuBar menuBar =
                new MenuBar(
                        fileMenu,
                        settingsMenu
                );

        // =========================================================
        // EXPORT
        // =========================================================

        exportAll.setOnAction(event -> {

            saveImmediately(
                    noteTitle,
                    editor
            );

            DirectoryChooser chooser =
                    new DirectoryChooser();

            chooser.setTitle(
                    "Export Zorza Notes"
            );

            File destination =
                    chooser.showDialog(stage);

            if (destination == null) {
                return;
            }

            try {

                int count =
                        exportImportService
                                .exportAll(
                                        destination
                                                .toPath()
                                );

                status.setText(
                        "Exported " +
                                count +
                                " notes"
                );

                Alert complete =
                        new Alert(
                                Alert.AlertType.INFORMATION
                        );

                complete.initOwner(stage);

                complete.setTitle(
                        "Export Complete"
                );

                complete.setHeaderText(
                        "Zorza export complete"
                );

                complete.setContentText(
                        count +
                                " notes were exported to:\n\n" +
                                destination
                                        .getAbsolutePath()
                );

                complete.showAndWait();

            } catch (IOException e) {

                showError(
                        stage,
                        "Export Failed",
                        e
                );
            }
        });

        // =========================================================
        // IMPORT
        // =========================================================

        importMarkdown.setOnAction(event -> {

            DirectoryChooser chooser =
                    new DirectoryChooser();

            chooser.setTitle(
                    "Import Markdown Notebook"
            );

            File directory =
                    chooser.showDialog(stage);

            if (directory == null) {
                return;
            }

            try {

                int count =
                        exportImportService
                                .importDirectory(
                                        directory
                                                .toPath()
                                );

                refreshNotebooks(
                        notebooks
                );

                status.setText(
                        "Imported " +
                                count +
                                " notes"
                );

                Alert complete =
                        new Alert(
                                Alert.AlertType.INFORMATION
                        );

                complete.initOwner(stage);

                complete.setTitle(
                        "Import Complete"
                );

                complete.setHeaderText(
                        "Markdown imported"
                );

                complete.setContentText(
                        count +
                                " notes were imported."
                );

                complete.showAndWait();

            } catch (IOException |
                     RuntimeException e) {

                showError(
                        stage,
                        "Import Failed",
                        e
                );
            }
        });

        // =========================================================
        // QUIT
        // =========================================================

        quit.setOnAction(event -> {

            saveImmediately(
                    noteTitle,
                    editor
            );

            Platform.exit();
        });

        // =========================================================
        // BRANDING + SEARCH
        // =========================================================

        Label appName =
                new Label(
                        "ZORZA NOTES"
                );

        appName.getStyleClass()
                .add(
                        "app-name"
                );

        Label tagline =
                new Label(
                        "Your thoughts belong to you."
                );

        VBox branding =
                new VBox(
                        2,
                        appName,
                        tagline
                );

        Region headerSpacer =
                new Region();

        HBox.setHgrow(
                headerSpacer,
                Priority.ALWAYS
        );

        Label searchIcon =
                new Label("⌕");

        HBox searchBox =
                new HBox(
                        7,
                        searchIcon,
                        globalSearch
                );

        searchBox.setAlignment(
                Pos.CENTER_RIGHT
        );

        HBox header =
                new HBox(
                        20,
                        branding,
                        headerSpacer,
                        searchBox
                );

        header.setAlignment(
                Pos.CENTER_LEFT
        );

        header.setPadding(
                new Insets(
                        12,
                        20,
                        12,
                        20
                )
        );

        VBox top =
                new VBox(
                        menuBar,
                        header
                );

        // =========================================================
        // SPLIT PANE
        // =========================================================

        SplitPane splitPane =
                new SplitPane(
                        notebookPane,
                        notesPane,
                        editorPane
                );

        splitPane.setDividerPositions(
                0.18,
                0.42
        );

        BorderPane root =
                new BorderPane();

        root.setTop(top);

        root.setCenter(
                splitPane
        );

        Scene scene =
                new Scene(
                        root,
                        1250,
                        780
                );

        // =========================================================
        // THEME
        // =========================================================

        ThemeManager.apply(
                scene
        );

        darkMode.setOnAction(event -> {

            AppSettings.setDarkMode(
                    darkMode.isSelected()
            );

            ThemeManager.apply(
                    scene
            );
        });

        normalText.setOnAction(event -> {

            AppSettings.setTextSize(
                    AppSettings.TextSize.NORMAL
            );

            ThemeManager.apply(
                    scene
            );
        });

        largeText.setOnAction(event -> {

            AppSettings.setTextSize(
                    AppSettings.TextSize.LARGE
            );

            ThemeManager.apply(
                    scene
            );
        });

        extraLargeText.setOnAction(event -> {

            AppSettings.setTextSize(
                    AppSettings.TextSize.EXTRA_LARGE
            );

            ThemeManager.apply(
                    scene
            );
        });

        // =========================================================
        // STAGE
        // =========================================================

        stage.setTitle(
                "Zorza Notes"
        );

        stage.setMinWidth(
                950
        );

        stage.setMinHeight(
                600
        );

        stage.setScene(
                scene
        );

        stage.setOnCloseRequest(event ->

                saveImmediately(
                        noteTitle,
                        editor
                )
        );

        stage.show();
    }

    // =============================================================
    // OPEN GLOBAL SEARCH RESULT
    // =============================================================

    private void openSearchResult(
            Note searchResult,
            ListView<Notebook> notebooks,
            ListView<Note> notes,
            TextField noteTitle,
            MarkdownEditor editor,
            Button deleteNote,
            Button deleteNotebook,
            Label status,
            TextField globalSearch,
            Label notesTitle) {

        if (searchResult == null) {
            return;
        }

        /*
         * Save the currently open note BEFORE beginning the
         * controlled navigation.
         */
        saveImmediately(
                noteTitle,
                editor
        );

        Notebook targetNotebook =
                findNotebook(
                        notebooks,
                        searchResult.getNotebookId()
                );

        if (targetNotebook == null) {

            refreshNotebooks(
                    notebooks
            );

            targetNotebook =
                    findNotebook(
                            notebooks,
                            searchResult.getNotebookId()
                    );
        }

        if (targetNotebook == null) {

            status.setText(
                    "Unable to locate notebook"
            );

            return;
        }

        /*
         * Load the target notebook's normal note list before
         * touching the editor.
         */
        List<Note> notebookNotes =
                noteRepository
                        .findByNotebook(
                                targetNotebook
                                        .getId()
                        );

        Note targetNote =
                findNote(
                        notebookNotes,
                        searchResult.getId()
                );

        if (targetNote == null) {

            targetNote =
                    noteRepository
                            .findById(
                                    searchResult.getId()
                            );
        }

        if (targetNote == null) {

            status.setText(
                    "Unable to locate note"
            );

            return;
        }

        /*
         * Everything from here to the finally block is one
         * atomic UI navigation.
         *
         * Selection listeners are suppressed, so selecting
         * the notebook cannot clear the editor.
         */
        programmaticNavigation = true;

        searchTimer.stop();

        try {

            notebooks
                    .getSelectionModel()
                    .select(
                            targetNotebook
                    );

            deleteNotebook.setDisable(
                    false
            );

            notes.getSelectionModel()
                    .clearSelection();

            notes.getItems()
                    .setAll(
                            notebookNotes
                    );

            notes.getSelectionModel()
                    .select(
                            targetNote
                    );

            /*
             * Load the target directly.
             *
             * There is deliberately NO clearEditor() between
             * the old note and this one.
             */
            loadNote(
                    targetNote,
                    noteTitle,
                    editor,
                    deleteNote,
                    status
            );

            /*
             * Clear the search while listeners are suppressed.
             * The normal notebook list is already loaded.
             */
            globalSearch.clear();

            notesTitle.setText(
                    "NOTES"
            );

            status.setText(
                    "Loaded"
            );

        } finally {

            programmaticNavigation =
                    false;
        }

        /*
         * Give focus directly to the note body.
         */
        editor.requestEditorFocus();
    }

    // =============================================================
    // FIND NOTEBOOK
    // =============================================================

    private Notebook findNotebook(
            ListView<Notebook> notebooks,
            long notebookId) {

        for (Notebook notebook :
                notebooks.getItems()) {

            if (notebook.getId() ==
                    notebookId) {

                return notebook;
            }
        }

        return null;
    }

    // =============================================================
    // FIND NOTE
    // =============================================================

    private Note findNote(
            List<Note> notes,
            long noteId) {

        for (Note note : notes) {

            if (note.getId() ==
                    noteId) {

                return note;
            }
        }

        return null;
    }

    // =============================================================
    // RESTORE NORMAL NOTEBOOK VIEW
    // =============================================================

    private void restoreSelectedNotebookNotes(
            ListView<Notebook> notebooks,
            ListView<Note> notes,
            Label notesTitle) {

        Notebook selectedNotebook =
                notebooks
                        .getSelectionModel()
                        .getSelectedItem();

        notesTitle.setText(
                "NOTES"
        );

        if (selectedNotebook == null) {

            programmaticNavigation = true;

            try {

                notes.getSelectionModel()
                        .clearSelection();

                notes.getItems()
                        .clear();

            } finally {

                programmaticNavigation =
                        false;
            }

            return;
        }

        List<Note> notebookNotes =
                noteRepository
                        .findByNotebook(
                                selectedNotebook
                                        .getId()
                        );

        programmaticNavigation = true;

        try {

            notes.getSelectionModel()
                    .clearSelection();

            notes.getItems()
                    .setAll(
                            notebookNotes
                    );

            /*
             * If the current note belongs to this notebook,
             * keep it selected when search is merely cleared.
             */
            if (currentNote != null &&
                    currentNote.getNotebookId() ==
                            selectedNotebook.getId()) {

                Note matchingNote =
                        findNote(
                                notebookNotes,
                                currentNote.getId()
                        );

                if (matchingNote != null) {

                    notes.getSelectionModel()
                            .select(
                                    matchingNote
                            );
                }
            }

        } finally {

            programmaticNavigation =
                    false;
        }
    }

    // =============================================================
    // LOAD NOTE
    // =============================================================

    private void loadNote(
            Note note,
            TextField noteTitle,
            MarkdownEditor editor,
            Button deleteNote,
            Label status) {

        loadingNote =
                true;

        try {

            currentNote =
                    note;

            if (note == null) {

                noteTitle.clear();

                editor.clear();

                noteTitle.setDisable(
                        true
                );

                editor.setEditorDisabled(
                        true
                );

                deleteNote.setDisable(
                        true
                );

                status.setText(
                        "Select a note"
                );

                return;
            }

            noteTitle.setDisable(
                    false
            );

            editor.setEditorDisabled(
                    false
            );

            deleteNote.setDisable(
                    false
            );

            noteTitle.setText(
                    note.getTitle()
            );

            editor.setText(
                    note.getBody()
            );

            status.setText(
                    "Loaded"
            );

        } finally {

            loadingNote =
                    false;
        }
    }

    // =============================================================
    // CLEAR EDITOR
    // =============================================================

    private void clearEditor(
            TextField noteTitle,
            MarkdownEditor editor) {

        loadingNote =
                true;

        try {

            currentNote =
                    null;

            noteTitle.clear();

            editor.clear();

            noteTitle.setDisable(
                    true
            );

            editor.setEditorDisabled(
                    true
            );

        } finally {

            loadingNote =
                    false;
        }
    }

    // =============================================================
    // REFRESH NOTEBOOKS
    // =============================================================

    private void refreshNotebooks(
            ListView<Notebook> notebooks) {

        notebooks.getItems()
                .setAll(
                        notebookRepository
                                .findAll()
                );
    }

    // =============================================================
    // SAVE IMMEDIATELY
    // =============================================================

    private void saveImmediately(
            TextField title,
            MarkdownEditor editor) {

        autoSaveTimer.stop();

        if (currentNote == null ||
                loadingNote) {

            return;
        }

        currentNote.setTitle(
                cleanTitle(
                        title.getText()
                )
        );

        currentNote.setBody(
                editor.getText()
        );

        noteRepository.update(
                currentNote
        );
    }

    // =============================================================
    // CLEAN TITLE
    // =============================================================

    private String cleanTitle(
            String title) {

        if (title == null ||
                title.trim().isEmpty()) {

            return "Untitled Note";
        }

        return title.trim();
    }

    // =============================================================
    // ERROR DIALOG
    // =============================================================

    private void showError(
            Stage owner,
            String title,
            Exception exception) {

        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR
                );

        alert.initOwner(
                owner
        );

        alert.setTitle(
                title
        );

        alert.setHeaderText(
                title
        );

        alert.setContentText(
                exception.getMessage() == null
                        ? exception.toString()
                        : exception.getMessage()
        );

        alert.showAndWait();
    }

    // =============================================================
    // MAIN
    // =============================================================

    public static void main(
            String[] args) {

        launch(args);
    }
}