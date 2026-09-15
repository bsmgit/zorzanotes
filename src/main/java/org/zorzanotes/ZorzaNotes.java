package org.zorzanotes;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class ZorzaNotes extends Application {

    private final NotebookRepository notebookRepository =
            new NotebookRepository();

    private final NoteRepository noteRepository =
            new NoteRepository();

    private Note currentNote;

    @Override
    public void start(Stage stage) {

        Database.initialize();

        // ---------- NOTEBOOKS ----------

        Label notebooksTitle = new Label("NOTEBOOKS");

        ListView<Notebook> notebooks = new ListView<>();
        notebooks.getItems().addAll(notebookRepository.findAll());

        Button newNotebook = new Button("+ New Notebook");

        VBox notebookPane = new VBox(
                10,
                notebooksTitle,
                notebooks,
                newNotebook
        );

        notebookPane.setPadding(new Insets(20));
        notebookPane.setPrefWidth(220);
        VBox.setVgrow(notebooks, Priority.ALWAYS);


        // ---------- NOTES ----------

        Label notesTitle = new Label("NOTES");

        TextField search = new TextField();
        search.setPromptText("Search notes...");

        ListView<Note> notes = new ListView<>();

        Button newNote = new Button("+ New Note");

        VBox notesPane = new VBox(
                10,
                notesTitle,
                search,
                notes,
                newNote
        );

        notesPane.setPadding(new Insets(20));
        notesPane.setPrefWidth(300);
        VBox.setVgrow(notes, Priority.ALWAYS);


        // ---------- EDITOR ----------

        TextField noteTitle = new TextField();
        noteTitle.setPromptText("Note title");

        noteTitle.setStyle("""
                -fx-font-size: 24px;
                -fx-font-weight: bold;
                """);

        TextArea editor = new TextArea();
        editor.setWrapText(true);

        Button save = new Button("Save");

        Label status = new Label("Select a note");

        HBox editorFooter = new HBox(
                10,
                save,
                status
        );

        VBox editorPane = new VBox(
                15,
                noteTitle,
                editor,
                editorFooter
        );

        editorPane.setPadding(new Insets(25));
        VBox.setVgrow(editor, Priority.ALWAYS);


        // ---------- NOTEBOOK SELECTION ----------

        notebooks.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldNotebook, newNotebookValue) -> {

                    notes.getItems().clear();

                    currentNote = null;
                    noteTitle.clear();
                    editor.clear();

                    if (newNotebookValue != null) {

                        notes.getItems().addAll(
                                noteRepository.findByNotebook(
                                        newNotebookValue.getId()
                                )
                        );

                        status.setText("Notebook selected");
                    }
                });


        // ---------- NOTE SELECTION ----------

        notes.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldNote, newNoteValue) -> {

                    currentNote = newNoteValue;

                    if (newNoteValue != null) {

                        noteTitle.setText(
                                newNoteValue.getTitle()
                        );

                        editor.setText(
                                newNoteValue.getBody()
                        );

                        status.setText("Loaded");
                    }
                });


        // ---------- NEW NOTEBOOK ----------

        newNotebook.setOnAction(event -> {

            TextInputDialog dialog = new TextInputDialog();

            dialog.setTitle("New Notebook");
            dialog.setHeaderText("Create a notebook");
            dialog.setContentText("Name:");

            dialog.showAndWait().ifPresent(name -> {

                String trimmedName = name.trim();

                if (!trimmedName.isEmpty()) {

                    Notebook notebook =
                            notebookRepository.create(trimmedName);

                    notebooks.getItems().add(notebook);

                    notebooks.getSelectionModel()
                            .select(notebook);
                }
            });
        });


        // ---------- NEW NOTE ----------

        newNote.setOnAction(event -> {

            Notebook selectedNotebook =
                    notebooks.getSelectionModel()
                            .getSelectedItem();

            if (selectedNotebook == null) {

                status.setText(
                        "Select a notebook first"
                );

                return;
            }

            Note note =
                    noteRepository.create(
                            selectedNotebook.getId()
                    );

            notes.getItems().add(0, note);

            notes.getSelectionModel()
                    .select(note);

            noteTitle.requestFocus();
            noteTitle.selectAll();
        });


        // ---------- SAVE ----------

        save.setOnAction(event -> {

            if (currentNote == null) {
                return;
            }

            currentNote.setTitle(
                    noteTitle.getText().trim()
            );

            currentNote.setBody(
                    editor.getText()
            );

            noteRepository.update(currentNote);

            notes.refresh();

            status.setText("Saved locally");
        });


        // ---------- MAIN LAYOUT ----------

        SplitPane splitPane = new SplitPane(
                notebookPane,
                notesPane,
                editorPane
        );

        splitPane.setDividerPositions(
                0.18,
                0.42
        );


        // ---------- HEADER ----------

        Label appName = new Label("ZORZA NOTES");

        appName.setStyle("""
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                """);

        Label tagline =
                new Label("Your thoughts belong to you.");

        VBox branding = new VBox(
                2,
                appName,
                tagline
        );

        HBox header = new HBox(branding);

        header.setPadding(
                new Insets(15, 20, 15, 20)
        );


        // ---------- ROOT ----------

        BorderPane root = new BorderPane();

        root.setTop(header);
        root.setCenter(splitPane);


        // ---------- WINDOW ----------

        Scene scene =
                new Scene(root, 1200, 760);

        stage.setTitle("Zorza Notes");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}