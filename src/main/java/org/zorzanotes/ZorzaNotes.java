package org.zorzanotes;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZorzaNotes extends Application {

    public static final String APP_NAME = "Zorza Notes";
    public static final String APP_VERSION = "1.2.0";
    public static final String APP_URL = "https://zorzanotes.com";
    public static final String APP_TAGLINE = "Your thoughts belong to you.";
    public static final String APP_LICENSE = "MIT License";

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

    /*
     * Prevents programmatic changes to the global search field
     * from starting a new search after a result is opened.
     */
    private boolean suppressSearchRefresh = false;

    @Override
    public void start(Stage stage) {

        if (!unlockVault(stage)) {
            VaultService.clearPassword();
            Platform.exit();
            return;
        }

        try {
            Database.initialize();
        } catch (SQLException exception) {
            VaultService.clearPassword();
            showError(stage, "Unable to Open Zorza Vault", exception);
            Platform.exit();
            return;
        }

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
        // LIBRARY TREE
        // =========================================================

        Label libraryTitle =
                new Label("LIBRARY");

        TreeView<Object> libraryTree =
                new TreeView<>();

        libraryTree.setShowRoot(false);
        libraryTree.setPrefWidth(330);

        HBox libraryButtons =
                new HBox(
                        8,
                        newNotebook,
                        newNote
                );


        VBox libraryPane =
                new VBox(
                        10,
                        libraryTitle,
                        libraryTree,
                        libraryButtons
                );

        libraryPane.setPadding(
                new Insets(20)
        );

        libraryPane.setPrefWidth(330);
        libraryPane.setMinWidth(260);

        VBox.setVgrow(
                libraryTree,
                Priority.ALWAYS
        );

        rebuildLibraryTree(
                libraryTree
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
            libraryTree.refresh();

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

                            /*
                             * Keep the in-memory note and its TreeView item
                             * synchronized immediately while the user types.
                             * The repository write still happens through the
                             * normal autosave timer below.
                             */
                            currentNote.setTitle(
                                    cleanTitle(newValue)
                            );

                            updateNoteTitleInTree(
                                    libraryTree,
                                    currentNote.getId(),
                                    currentNote.getTitle()
                            );

                            notes.refresh();

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

                        rebuildLibraryTree(libraryTree);
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

                        rebuildLibraryTree(libraryTree);
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

            rebuildLibraryTree(libraryTree);
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

                        rebuildLibraryTree(libraryTree);
                    });
        });

        // =========================================================
        // EDITOR DELETE NOTE BUTTON
        // =========================================================

        editor.setOnDeleteNote(
                deleteNote::fire
        );

        // =========================================================
        // RIGHT-CLICK MENUS + DRAG/DROP NOTE MOVING
        // =========================================================

        libraryTree.setCellFactory(tree ->
                new TreeCell<>() {

                    @Override
                    protected void updateItem(
                            Object item,
                            boolean empty) {

                        super.updateItem(item, empty);

                        setContextMenu(null);
                        setOnDragDetected(null);
                        setOnDragOver(null);
                        setOnDragDropped(null);
                        setOnDragDone(null);

                        if (empty || item == null) {
                            setText(null);
                            return;
                        }

                        setText(item.toString());

                        if (item instanceof Note note) {

                            // -------------------------------------------------
                            // RIGHT CLICK: MOVE NOTE
                            // -------------------------------------------------

                            Menu moveMenu =
                                    new Menu("Move to Notebook");

                            for (Notebook targetNotebook : notebookRepository.findAll()) {

                                if (targetNotebook.getId() == note.getNotebookId()) {
                                    continue;
                                }

                                MenuItem targetItem =
                                        new MenuItem(targetNotebook.getName());

                                targetItem.setOnAction(event -> {

                                    libraryTree.getSelectionModel()
                                            .select(getTreeItem());

                                    moveNoteToNotebook(
                                            note,
                                            targetNotebook,
                                            libraryTree,
                                            notebooks,
                                            notes,
                                            noteTitle,
                                            editor,
                                            deleteNote,
                                            status
                                    );
                                });

                                moveMenu.getItems().add(targetItem);
                            }

                            if (moveMenu.getItems().isEmpty()) {
                                MenuItem nowhere =
                                        new MenuItem("No other notebooks");
                                nowhere.setDisable(true);
                                moveMenu.getItems().add(nowhere);
                            }

                            // -------------------------------------------------
                            // RIGHT CLICK: DELETE NOTE
                            // -------------------------------------------------

                            MenuItem deleteNoteItem =
                                    new MenuItem("Delete Note...");

                            deleteNoteItem.setOnAction(event -> {

                                libraryTree.getSelectionModel()
                                        .select(getTreeItem());

                                Alert confirmation =
                                        new Alert(Alert.AlertType.CONFIRMATION);

                                confirmation.initOwner(stage);
                                confirmation.setTitle("Delete Note");
                                confirmation.setHeaderText(
                                        "Delete \"" + note.getTitle() + "\"?"
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

                                            if (currentNote != null &&
                                                    currentNote.getId() == note.getId()) {

                                                currentNote = null;
                                                clearEditor(noteTitle, editor);
                                                deleteNote.setDisable(true);
                                            }

                                            noteRepository.delete(note);

                                            notes.getItems()
                                                    .removeIf(existing ->
                                                            existing.getId() == note.getId());

                                            status.setText("Note deleted");
                                            rebuildLibraryTree(libraryTree);
                                        });
                            });

                            setContextMenu(
                                    new ContextMenu(
                                            moveMenu,
                                            new SeparatorMenuItem(),
                                            deleteNoteItem
                                    )
                            );

                            // -------------------------------------------------
                            // DRAG NOTE
                            // -------------------------------------------------

                            setOnDragDetected(event -> {

                                Dragboard dragboard =
                                        startDragAndDrop(TransferMode.MOVE);

                                ClipboardContent content =
                                        new ClipboardContent();

                                content.putString(
                                        "zorza-note:" + note.getId()
                                );

                                dragboard.setContent(content);
                                event.consume();
                            });

                        } else if (item instanceof Notebook notebook) {

                            // -------------------------------------------------
                            // RIGHT CLICK: DELETE NOTEBOOK
                            // -------------------------------------------------

                            MenuItem deleteNotebookItem =
                                    new MenuItem("Delete Notebook...");

                            deleteNotebookItem.setOnAction(event -> {

                                libraryTree.getSelectionModel()
                                        .select(getTreeItem());

                                Alert confirmation =
                                        new Alert(Alert.AlertType.CONFIRMATION);

                                confirmation.initOwner(stage);
                                confirmation.setTitle("Delete Notebook");
                                confirmation.setHeaderText(
                                        "Delete \"" + notebook.getName() + "\"?"
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

                                            if (currentNote != null &&
                                                    currentNote.getNotebookId() == notebook.getId()) {

                                                currentNote = null;
                                                clearEditor(noteTitle, editor);
                                                deleteNote.setDisable(true);
                                            }

                                            notebookRepository.delete(notebook);

                                            notebooks.getItems()
                                                    .removeIf(existing ->
                                                            existing.getId() == notebook.getId());

                                            Notebook selectedNotebook =
                                                    notebooks.getSelectionModel()
                                                            .getSelectedItem();

                                            if (selectedNotebook != null &&
                                                    selectedNotebook.getId() == notebook.getId()) {

                                                notebooks.getSelectionModel()
                                                        .clearSelection();
                                            }

                                            notes.getItems().clear();
                                            deleteNotebook.setDisable(true);
                                            status.setText("Notebook deleted");
                                            rebuildLibraryTree(libraryTree);
                                        });
                            });

                            setContextMenu(
                                    new ContextMenu(deleteNotebookItem)
                            );

                            // -------------------------------------------------
                            // DROP NOTE ON NOTEBOOK
                            // -------------------------------------------------

                            setOnDragOver(event -> {

                                Dragboard dragboard =
                                        event.getDragboard();

                                if (dragboard.hasString() &&
                                        dragboard.getString().startsWith("zorza-note:")) {

                                    event.acceptTransferModes(TransferMode.MOVE);
                                }

                                event.consume();
                            });

                            setOnDragDropped(event -> {

                                Dragboard dragboard =
                                        event.getDragboard();

                                boolean completed = false;

                                if (dragboard.hasString() &&
                                        dragboard.getString().startsWith("zorza-note:")) {

                                    try {
                                        long noteId =
                                                Long.parseLong(
                                                        dragboard.getString()
                                                                .substring("zorza-note:".length())
                                                );

                                        Note draggedNote =
                                                noteRepository.findById(noteId);

                                        if (draggedNote != null) {

                                            if (draggedNote.getNotebookId() == notebook.getId()) {
                                                completed = true;
                                            } else {
                                                moveNoteToNotebook(
                                                        draggedNote,
                                                        notebook,
                                                        libraryTree,
                                                        notebooks,
                                                        notes,
                                                        noteTitle,
                                                        editor,
                                                        deleteNote,
                                                        status
                                                );
                                                completed = true;
                                            }
                                        }

                                    } catch (NumberFormatException ignored) {
                                        completed = false;
                                    }
                                }

                                event.setDropCompleted(completed);
                                event.consume();
                            });
                        }
                    }
                }
        );

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

                editor.clearSearchMatches();
                rebuildLibraryTree(libraryTree);

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

                int totalMatches =
                        countSearchMatches(
                                results,
                                query
                        );

                rebuildSearchTree(
                        libraryTree,
                        results,
                        query
                );

                notesTitle.setText(
                        "SEARCH RESULTS (" +
                                results.size() +
                                " NOTES · " +
                                totalMatches +
                                " MATCHES)"
                );

                status.setText(
                        results.size() +
                                " note" +
                                (results.size() == 1 ? "" : "s") +
                                " · " +
                                totalMatches +
                                " match" +
                                (totalMatches == 1 ? "" : "es")
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
                         newValue) -> {

                            if (suppressSearchRefresh) {
                                return;
                            }

                            searchTimer
                                    .playFromStart();
                        }
                );

        // =========================================================
        // LIBRARY TREE SELECTION
        // =========================================================

        libraryTree.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldItem, selectedItem) -> {

                    if (programmaticNavigation || selectedItem == null) {
                        return;
                    }

                    Object value = selectedItem.getValue();

                    if (value instanceof Notebook selectedNotebook) {

                        notebooks.getSelectionModel()
                                .select(selectedNotebook);

                        return;
                    }

                    if (!(value instanceof Note selectedNote)) {
                        return;
                    }

                    if (!globalSearch.getText().isBlank()) {

                        openSearchResult(
                                selectedNote,
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

                        rebuildLibraryTree(libraryTree);
                        return;
                    }

                    Notebook parentNotebook =
                            findNotebook(
                                    notebooks,
                                    selectedNote.getNotebookId()
                            );

                    if (parentNotebook == null) {
                        refreshNotebooks(notebooks);
                        parentNotebook =
                                findNotebook(
                                        notebooks,
                                        selectedNote.getNotebookId()
                                );
                    }

                    if (parentNotebook == null) {
                        return;
                    }

                    notebooks.getSelectionModel()
                            .select(parentNotebook);

                    Note noteToSelect =
                            findNote(
                                    notes.getItems(),
                                    selectedNote.getId()
                            );

                    if (noteToSelect != null) {
                        notes.getSelectionModel()
                                .select(noteToSelect);
                    }
                });

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

        // =========================================================
        // HELP MENU
        // =========================================================

        Menu helpMenu =
                new Menu("Help");

        MenuItem about =
                new MenuItem(
                        "About Zorza Notes"
                );

        helpMenu.getItems()
                .add(
                        about
                );

        // =========================================================
        // MENU BAR
        // =========================================================

        MenuBar menuBar =
                new MenuBar(
                        fileMenu,
                        settingsMenu,
                        helpMenu
                );

        /*
         * On macOS this moves the JavaFX menu into the normal
         * system menu bar at the top of the screen.
         *
         * Windows and Linux ignore this and display the menu
         * inside the application window.
         */
        menuBar.setUseSystemMenuBar(true);

        // =========================================================
        // ABOUT
        // =========================================================

        about.setOnAction(event ->
                showAboutWindow(
                        stage
                )
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

                rebuildLibraryTree(
                        libraryTree
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

            VaultService.clearPassword();
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
                        APP_TAGLINE
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
                        libraryPane,
                        editorPane
                );

        splitPane.setDividerPositions(
                0.26
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
                APP_NAME
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

        stage.setOnCloseRequest(event -> {

            saveImmediately(
                    noteTitle,
                    editor
            );

            VaultService.clearPassword();
        });

        stage.show();
    }

    // =============================================================
    // VAULT STARTUP
    // =============================================================

    private boolean unlockVault(Stage owner) {
        if (Database.databaseIsPlaintext()) {
            return createVaultPassword(owner, true);
        }

        if (!Database.databaseExists()) {
            return createVaultPassword(owner, false);
        }

        return requestVaultPassword(owner);
    }

    private boolean createVaultPassword(Stage owner, boolean migration) {
        while (true) {
            Dialog<char[]> dialog = new Dialog<>();
            dialog.setTitle("Create Zorza Vault");
            dialog.setHeaderText(
                    migration
                            ? "Protect your existing notes with encryption"
                            : "Create your encrypted Zorza vault"
            );

            ButtonType createButton = new ButtonType(
                    "Create Vault",
                    ButtonBar.ButtonData.OK_DONE
            );

            dialog.getDialogPane().getButtonTypes().addAll(
                    createButton,
                    ButtonType.CANCEL
            );

            PasswordField password = new PasswordField();
            password.setPromptText("Vault password");

            PasswordField confirm = new PasswordField();
            confirm.setPromptText("Confirm password");

            Label explanation = new Label(
                    migration
                            ? "Your existing notes will be copied into a new encrypted database. " +
                            "A plaintext safety backup will be retained until you delete it manually."
                            : "Your notes will be encrypted at rest. " +
                            "You will need this password whenever you open Zorza Notes."
            );
            explanation.setWrapText(true);

            Label warning = new Label(
                    "Important: Zorza does not store your password. " +
                            "If you forget it, Zorza cannot recover your encrypted notes."
            );
            warning.setWrapText(true);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(10, 0, 10, 0));
            grid.add(explanation, 0, 0, 2, 1);
            grid.add(new Label("Password:"), 0, 1);
            grid.add(password, 1, 1);
            grid.add(new Label("Confirm:"), 0, 2);
            grid.add(confirm, 1, 2);
            grid.add(warning, 0, 3, 2, 1);

            dialog.getDialogPane().setContent(grid);
            dialog.setResultConverter(button ->
                    button == createButton
                            ? password.getText().toCharArray()
                            : null
            );

            Platform.runLater(password::requestFocus);
            Optional<char[]> result = dialog.showAndWait();

            if (result.isEmpty()) {
                password.clear();
                confirm.clear();
                return false;
            }

            char[] supplied = result.get();
            char[] confirmation = confirm.getText().toCharArray();

            try {
                if (supplied.length < 8) {
                    showVaultMessage(
                            owner,
                            "Password Too Short",
                            "Use at least 8 characters for your Zorza vault password."
                    );
                    continue;
                }

                if (!Arrays.equals(supplied, confirmation)) {
                    showVaultMessage(
                            owner,
                            "Passwords Do Not Match",
                            "The two vault passwords do not match."
                    );
                    continue;
                }

                VaultService.unlock(supplied);
                return true;

            } finally {
                Arrays.fill(supplied, '\0');
                Arrays.fill(confirmation, '\0');
                password.clear();
                confirm.clear();
            }
        }
    }

    private boolean requestVaultPassword(Stage owner) {
        while (true) {
            Dialog<char[]> dialog = new Dialog<>();
            dialog.setTitle("Unlock Zorza Notes");
            dialog.setHeaderText("Unlock your encrypted Zorza vault");

            ButtonType unlockButton = new ButtonType(
                    "Unlock",
                    ButtonBar.ButtonData.OK_DONE
            );

            dialog.getDialogPane().getButtonTypes().addAll(
                    unlockButton,
                    ButtonType.CANCEL
            );

            PasswordField password = new PasswordField();
            password.setPromptText("Vault password");

            VBox content = new VBox(
                    10,
                    new Label("Enter your Zorza vault password."),
                    password
            );
            content.setPadding(new Insets(10, 0, 10, 0));
            dialog.getDialogPane().setContent(content);

            dialog.setResultConverter(button ->
                    button == unlockButton
                            ? password.getText().toCharArray()
                            : null
            );

            Platform.runLater(password::requestFocus);
            Optional<char[]> result = dialog.showAndWait();

            if (result.isEmpty()) {
                password.clear();
                return false;
            }

            char[] supplied = result.get();

            try {
                if (supplied.length == 0) {
                    showVaultMessage(
                            owner,
                            "Password Required",
                            "Enter your Zorza vault password."
                    );
                    continue;
                }

                VaultService.unlock(supplied);

                try (var connection = Database.connect()) {
                    VaultService.verifyConnection(connection);
                }

                return true;

            } catch (Exception exception) {
                VaultService.clearPassword();
                showVaultMessage(
                        owner,
                        "Unable to Unlock Vault",
                        "The password was not accepted. Please try again."
                );

            } finally {
                Arrays.fill(supplied, '\0');
                password.clear();
            }
        }
    }

    private void showVaultMessage(Stage owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // =============================================================
    // ABOUT WINDOW
    // =============================================================

    private void showAboutWindow(
            Stage owner) {

        Stage aboutStage =
                new Stage();

        aboutStage.initOwner(
                owner
        );

        aboutStage.initModality(
                Modality.WINDOW_MODAL
        );

        aboutStage.initStyle(
                StageStyle.UTILITY
        );

        aboutStage.setTitle(
                "About Zorza Notes"
        );

        aboutStage.setResizable(
                false
        );

        Label dawnSymbol =
                new Label("☀");

        dawnSymbol.setFont(
                Font.font(
                        54
                )
        );

        Label name =
                new Label(
                        "ZORZA NOTES"
                );

        name.setFont(
                Font.font(
                        "System",
                        FontWeight.BOLD,
                        25
                )
        );

        Label tagline =
                new Label(
                        APP_TAGLINE
                );

        Label version =
                new Label(
                        "Version " +
                                APP_VERSION
                );

        Hyperlink website =
                new Hyperlink(
                        "zorzanotes.com"
                );

        website.setOnAction(event ->
                getHostServices()
                        .showDocument(
                                APP_URL
                        )
        );

        Label license =
                new Label(
                        "Licensed under the " +
                                APP_LICENSE
                );

        Label copyright =
                new Label(
                        "Copyright © 2026 Zorza Notes"
                );

        Separator separator =
                new Separator();

        Button close =
                new Button(
                        "Close"
                );

        close.setDefaultButton(
                true
        );

        close.setOnAction(event ->
                aboutStage.close()
        );

        VBox content =
                new VBox(
                        10,
                        dawnSymbol,
                        name,
                        tagline,
                        new Region(),
                        version,
                        website,
                        license,
                        copyright,
                        separator,
                        close
                );

        content.setAlignment(
                Pos.CENTER
        );

        content.setPadding(
                new Insets(
                        28,
                        45,
                        24,
                        45
                )
        );

        content.setPrefWidth(
                430
        );

        Scene aboutScene =
                new Scene(
                        content
                );

        /*
         * Give the About window the same light/dark and
         * accessibility settings as the main application.
         */
        ThemeManager.apply(
                aboutScene
        );

        aboutStage.setScene(
                aboutScene
        );

        aboutStage.showAndWait();
    }

    // =============================================================
    // MOVE NOTE TO NOTEBOOK
    // =============================================================

    private void moveNoteToNotebook(
            Note note,
            Notebook targetNotebook,
            TreeView<Object> libraryTree,
            ListView<Notebook> notebooks,
            ListView<Note> notes,
            TextField noteTitle,
            MarkdownEditor editor,
            Button deleteNote,
            Label status) {

        if (note == null || targetNotebook == null) {
            return;
        }

        if (note.getNotebookId() == targetNotebook.getId()) {
            status.setText("Note is already in that notebook");
            return;
        }

        /* Save any unsaved editor changes before changing ownership. */
        if (currentNote != null && currentNote.getId() == note.getId()) {
            saveImmediately(noteTitle, editor);
        }

        noteRepository.moveToNotebook(
                note.getId(),
                targetNotebook.getId()
        );

        /*
         * Note.notebookId is mutable so the currently open note remains
         * internally consistent after a move.
         */
        if (currentNote != null && currentNote.getId() == note.getId()) {
            currentNote.setNotebookId(targetNotebook.getId());
        }

        note.setNotebookId(targetNotebook.getId());

        /* Keep the hidden compatibility ListViews synchronized. */
        refreshNotebooks(notebooks);

        Notebook refreshedTarget =
                findNotebook(notebooks, targetNotebook.getId());

        List<Note> targetNotes =
                noteRepository.findByNotebook(targetNotebook.getId());

        Note movedNote =
                findNote(targetNotes, note.getId());

        programmaticNavigation = true;

        try {
            if (refreshedTarget != null) {
                notebooks.getSelectionModel().select(refreshedTarget);
            }

            notes.getItems().setAll(targetNotes);

            if (movedNote != null) {
                notes.getSelectionModel().select(movedNote);
            }
        } finally {
            programmaticNavigation = false;
        }

        /*
         * The hidden ListView selection above is intentionally suppressed,
         * so its normal selection listener cannot load the moved note.
         * Load it explicitly here.  Without this, the TreeView can show the
         * note as selected after a move while the editor remains disabled or
         * continues showing the previous note.
         */
        if (movedNote != null) {
            loadNote(
                    movedNote,
                    noteTitle,
                    editor,
                    deleteNote,
                    status
            );
        }

        rebuildLibraryTree(libraryTree);
        selectNoteInTree(libraryTree, note.getId());

        status.setText(
                "Moved to " + targetNotebook.getName()
        );
    }

    private void selectNoteInTree(
            TreeView<Object> libraryTree,
            long noteId) {

        TreeItem<Object> root = libraryTree.getRoot();

        if (root == null) {
            return;
        }

        programmaticNavigation = true;

        try {
            for (TreeItem<Object> notebookItem : root.getChildren()) {
                for (TreeItem<Object> noteItem : notebookItem.getChildren()) {
                    Object value = noteItem.getValue();

                    if (value instanceof Note note && note.getId() == noteId) {
                        notebookItem.setExpanded(true);
                        libraryTree.getSelectionModel().select(noteItem);
                        libraryTree.scrollTo(libraryTree.getRow(noteItem));
                        return;
                    }
                }
            }
        } finally {
            programmaticNavigation = false;
        }
    }

    // =============================================================
    // LIBRARY TREE
    // =============================================================

    private void rebuildLibraryTree(
            TreeView<Object> libraryTree) {

        TreeItem<Object> root =
                new TreeItem<>("Library");

        for (Notebook notebook : notebookRepository.findAll()) {

            TreeItem<Object> notebookItem =
                    new TreeItem<>(notebook);

            notebookItem.setExpanded(true);

            for (Note note : noteRepository.findByNotebook(notebook.getId())) {
                notebookItem.getChildren()
                        .add(new TreeItem<>(note));
            }

            root.getChildren()
                    .add(notebookItem);
        }

        programmaticNavigation = true;

        try {
            libraryTree.setRoot(root);
        } finally {
            programmaticNavigation = false;
        }
    }

    /**
     * Updates the Note object stored in the visible TreeView without
     * rebuilding the tree. This preserves expansion and selection while
     * allowing note titles to change immediately as the user types.
     */
    private void updateNoteTitleInTree(
            TreeView<Object> libraryTree,
            long noteId,
            String title) {

        TreeItem<Object> root =
                libraryTree.getRoot();

        if (root == null) {
            return;
        }

        for (TreeItem<Object> notebookItem : root.getChildren()) {

            for (TreeItem<Object> noteItem : notebookItem.getChildren()) {

                Object value =
                        noteItem.getValue();

                if (value instanceof Note treeNote &&
                        treeNote.getId() == noteId) {

                    treeNote.setTitle(
                            cleanTitle(title)
                    );

                    libraryTree.refresh();
                    return;
                }
            }
        }
    }

    private void rebuildSearchTree(
            TreeView<Object> libraryTree,
            List<Note> results,
            String query) {

        TreeItem<Object> root =
                new TreeItem<>("Search Results");

        for (Notebook notebook : notebookRepository.findAll()) {

            TreeItem<Object> notebookItem =
                    new TreeItem<>(notebook);

            for (Note note : results) {

                if (note.getNotebookId() == notebook.getId()) {
                    notebookItem.getChildren()
                            .add(new TreeItem<>(note));
                }
            }

            if (!notebookItem.getChildren().isEmpty()) {
                notebookItem.setExpanded(true);
                root.getChildren().add(notebookItem);
            }
        }

        programmaticNavigation = true;

        try {
            libraryTree.setRoot(root);
        } finally {
            programmaticNavigation = false;
        }
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

        String searchQuery =
                globalSearch
                        .getText()
                        .trim();

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
            suppressSearchRefresh = true;

            try {
                globalSearch.clear();
            } finally {
                suppressSearchRefresh = false;
            }

            notesTitle.setText(
                    "NOTES"
            );

            int noteMatches =
                    editor.showSearchMatches(
                            searchQuery
                    );

            if (noteMatches > 0) {
                status.setText(
                        noteMatches +
                                " match" +
                                (noteMatches == 1 ? "" : "es") +
                                " in this note"
                );
            } else if (titleContainsSearch(
                    targetNote.getTitle(),
                    searchQuery)) {

                selectTitleSearchMatch(
                        noteTitle,
                        searchQuery
                );

                status.setText(
                        "Match found in note title"
                );
            } else {
                status.setText(
                        "Loaded"
                );
            }

        } finally {

            programmaticNavigation =
                    false;
        }
    }

    // =============================================================
    // SEARCH MATCH COUNTING
    // =============================================================

    private int countSearchMatches(
            List<Note> notes,
            String query) {

        int count = 0;

        for (Note note : notes) {
            count += countSearchMatchesInText(
                    note.getTitle(),
                    query
            );

            count += countSearchMatchesInText(
                    note.getBody(),
                    query
            );
        }

        return count;
    }

    private int countSearchMatchesInText(
            String text,
            String query) {

        if (text == null ||
                text.isBlank() ||
                query == null ||
                query.isBlank()) {
            return 0;
        }

        String cleaned =
                query.trim()
                        .replaceAll(
                                "[^\\p{L}\\p{N}_]+",
                                " "
                        );

        if (cleaned.isBlank()) {
            return 0;
        }

        int count = 0;
        Set<String> uniqueRanges =
                new LinkedHashSet<>();

        for (String term : cleaned.split("\\s+")) {

            if (term.isBlank()) {
                continue;
            }

            Pattern pattern =
                    Pattern.compile(
                            "(?iu)(?<![\\p{L}\\p{N}_])" +
                                    Pattern.quote(term) +
                                    "[\\p{L}\\p{N}_]*"
                    );

            Matcher matcher =
                    pattern.matcher(text);

            while (matcher.find()) {

                String range =
                        matcher.start() +
                                ":" +
                                matcher.end();

                if (uniqueRanges.add(range)) {
                    count++;
                }
            }
        }

        return count;
    }

    private boolean titleContainsSearch(
            String title,
            String query) {

        return countSearchMatchesInText(
                title,
                query
        ) > 0;
    }

    private void selectTitleSearchMatch(
            TextField noteTitle,
            String query) {

        String title =
                noteTitle.getText();

        if (title == null ||
                query == null) {
            return;
        }

        String cleaned =
                query.trim()
                        .replaceAll(
                                "[^\\p{L}\\p{N}_]+",
                                " "
                        );

        for (String term : cleaned.split("\\s+")) {

            if (term.isBlank()) {
                continue;
            }

            Pattern pattern =
                    Pattern.compile(
                            "(?iu)(?<![\\p{L}\\p{N}_])" +
                                    Pattern.quote(term) +
                                    "[\\p{L}\\p{N}_]*"
                    );

            Matcher matcher =
                    pattern.matcher(title);

            if (matcher.find()) {
                noteTitle.requestFocus();
                noteTitle.selectRange(
                        matcher.start(),
                        matcher.end()
                );
                return;
            }
        }
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

            editor.clearSearchMatches();

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