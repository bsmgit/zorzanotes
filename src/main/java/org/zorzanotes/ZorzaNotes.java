package org.zorzanotes;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;

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

    private boolean loadingNote = false;

    @Override
    public void start(
            Stage stage) {

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

        TextField search =
                new TextField();

        search.setPromptText(
                "Search notes..."
        );

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
                        search,
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

        noteTitle.setStyle("""
                -fx-font-size: 24px;
                -fx-font-weight: bold;
                """);

        MarkdownEditor editor =
                new MarkdownEditor();

        /*
         * Allows the automatically detected
         * hyperlinks to open in the default browser.
         */

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

        editor.setEditorDisabled(
                true
        );

        // =========================================================
        // AUTOSAVE
        // =========================================================

        autoSaveTimer.setOnFinished(
                event -> {

                    if (currentNote == null) {
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

                            saveImmediately(
                                    noteTitle,
                                    editor
                            );

                            notes.getItems()
                                    .clear();

                            currentNote = null;

                            loadingNote = true;

                            noteTitle.clear();
                            editor.clear();

                            loadingNote = false;

                            noteTitle.setDisable(
                                    true
                            );

                            editor.setEditorDisabled(
                                    true
                            );

                            deleteNote.setDisable(
                                    true
                            );

                            deleteNotebook.setDisable(
                                    selectedNotebook == null
                            );

                            if (selectedNotebook != null) {

                                notes.getItems()
                                        .addAll(
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

                            if (oldNote != null &&
                                    oldNote == currentNote) {

                                saveImmediately(
                                        noteTitle,
                                        editor
                                );
                            }

                            currentNote =
                                    selectedNote;

                            loadingNote = true;

                            if (selectedNote != null) {

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
                                        selectedNote
                                                .getTitle()
                                );

                                editor.setText(
                                        selectedNote
                                                .getBody()
                                );

                                status.setText(
                                        "Loaded"
                                );

                            } else {

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
                            }

                            loadingNote = false;
                        });

        // =========================================================
        // NEW NOTEBOOK
        // =========================================================

        newNotebook.setOnAction(
                event -> {

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
                            .ifPresent(
                                    name -> {

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

                                        notebooks
                                                .getItems()
                                                .add(
                                                        notebook
                                                );

                                        notebooks
                                                .getSelectionModel()
                                                .select(
                                                        notebook
                                                );
                                    });
                });

        // =========================================================
        // DELETE NOTEBOOK
        // =========================================================

        deleteNotebook.setOnAction(
                event -> {

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

                    confirmation.initOwner(
                            stage
                    );

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
                            .ifPresent(
                                    result -> {

                                        if (result !=
                                                ButtonType.OK) {
                                            return;
                                        }

                                        autoSaveTimer.stop();

                                        currentNote = null;

                                        notebookRepository
                                                .delete(
                                                        selectedNotebook
                                                );

                                        notebooks
                                                .getItems()
                                                .remove(
                                                        selectedNotebook
                                                );

                                        notes
                                                .getItems()
                                                .clear();

                                        loadingNote = true;

                                        noteTitle.clear();
                                        editor.clear();

                                        loadingNote = false;

                                        noteTitle.setDisable(
                                                true
                                        );

                                        editor.setEditorDisabled(
                                                true
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

        newNote.setOnAction(
                event -> {

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
                            noteRepository
                                    .create(
                                            selectedNotebook
                                                    .getId()
                                    );

                    notes.getItems()
                            .add(
                                    0,
                                    note
                            );

                    notes
                            .getSelectionModel()
                            .select(
                                    note
                            );

                    noteTitle.requestFocus();
                    noteTitle.selectAll();
                });

        // =========================================================
        // DELETE NOTE
        // =========================================================

        deleteNote.setOnAction(
                event -> {

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

                    confirmation.initOwner(
                            stage
                    );

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
                            .ifPresent(
                                    result -> {

                                        if (result !=
                                                ButtonType.OK) {
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

                                        loadingNote = true;

                                        noteTitle.clear();
                                        editor.clear();

                                        loadingNote = false;

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
                                                "Note deleted"
                                        );
                                    });
                });

        // =========================================================
        // SEARCH
        // =========================================================

        search.textProperty()
                .addListener(
                        (observable,
                         oldValue,
                         newValue) -> {

                            Notebook selectedNotebook =
                                    notebooks
                                            .getSelectionModel()
                                            .getSelectedItem();

                            if (selectedNotebook == null) {
                                return;
                            }

                            String query =
                                    newValue
                                            .trim()
                                            .toLowerCase();

                            notes.getItems()
                                    .clear();

                            for (Note note :
                                    noteRepository
                                            .findByNotebook(
                                                    selectedNotebook
                                                            .getId()
                                            )) {

                                if (query.isEmpty() ||
                                        note.getTitle()
                                                .toLowerCase()
                                                .contains(query) ||
                                        note.getBody()
                                                .toLowerCase()
                                                .contains(query)) {

                                    notes.getItems()
                                            .add(note);
                                }
                            }
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
        // EXPORT
        // =========================================================

        exportAll.setOnAction(
                event -> {

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
                            chooser.showDialog(
                                    stage
                            );

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

                        complete.initOwner(
                                stage
                        );

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

        importMarkdown.setOnAction(
                event -> {

                    DirectoryChooser chooser =
                            new DirectoryChooser();

                    chooser.setTitle(
                            "Import Markdown Notebook"
                    );

                    File directory =
                            chooser.showDialog(
                                    stage
                            );

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

                        complete.initOwner(
                                stage
                        );

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

        quit.setOnAction(
                event -> {

                    saveImmediately(
                            noteTitle,
                            editor
                    );

                    Platform.exit();
                });

        MenuBar menuBar =
                new MenuBar(
                        fileMenu
                );

        // =========================================================
        // HEADER
        // =========================================================

        Label appName =
                new Label(
                        "ZORZA NOTES"
                );

        appName.setStyle("""
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                """);

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

        branding.setPadding(
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
                        branding
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

        // =========================================================
        // ROOT
        // =========================================================

        BorderPane root =
                new BorderPane();

        root.setTop(top);
        root.setCenter(splitPane);

        Scene scene =
                new Scene(
                        root,
                        1250,
                        780
                );

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

        stage.setOnCloseRequest(
                event ->
                        saveImmediately(
                                noteTitle,
                                editor
                        )
        );

        stage.show();
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
    // SAVE
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
    // TITLE
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
    // ERROR
    // =============================================================

    private void showError(
            Stage owner,
            String title,
            Exception exception) {

        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR
                );

        alert.initOwner(owner);

        alert.setTitle(title);

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