
package org.zorzanotes;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.prefs.Preferences;

public class ZorzaNotes extends Application {

    public static final String APP_NAME = "Zorza Notes";
    public static final String APP_VERSION = "2.0.8";
    public static final String APP_URL = "https://zorzanotes.com";

    private static final String LAST_NOTEBOOK =
            "lastSelectedNotebookId";

    private final Preferences preferences =
            Preferences.userNodeForPackage(ZorzaNotes.class);

    private final NotebookRepository books =
            new NotebookRepository();

    private final NoteRepository notes =
            new NoteRepository();

    private final ExportImportService importExport =
            new ExportImportService(books, notes);

    private final ExecutorService io =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "zorza-database");
                thread.setDaemon(true);
                return thread;
            });

    private final PauseTransition saveDelay =
            new PauseTransition(Duration.millis(650));

    private final PauseTransition searchDelay =
            new PauseTransition(Duration.millis(250));

    private final ComboBox<Notebook> notebookSelector =
            new ComboBox<>();

    private final ListView<Note> noteList =
            new ListView<>();

    private final ListView<Note> results =
            new ListView<>();

    private final TextField title = new TextField();
    private final TextField search = new TextField();
    private final Label status = new Label("Ready");

    private PlainTextEditor editor;
    private Stage mainStage;
    private Note current;

    private boolean loading;
    private boolean navigating;

    private long editRevision;
    private long searchRevision;
    private long queuedRevision = -1;
    private long queuedNoteId = -1;

    private Future<?> pendingSave;

    @Override
    public void start(Stage stage) {

        mainStage = stage;

        if (!unlock()) {
            Platform.exit();
            return;
        }

        try {
            Database.initialize();
        } catch (Exception ex) {
            error("Vault initialization failed", ex);
            Platform.exit();
            return;
        }

        editor = new PlainTextEditor(
                getHostServices(),
                new UrlShortener()
        );

        configureControls();

        Button addNotebook = new Button("+ Notebook");
        Button deleteNotebook = new Button("Delete Notebook");
        Button addNote = new Button("+ Note");

        addNotebook.setOnAction(e -> createNotebook());
        deleteNotebook.setOnAction(e -> deleteNotebook());
        addNote.setOnAction(e -> createNote());

        notebookSelector.setMaxWidth(Double.MAX_VALUE);

        HBox notebookBar = new HBox(
                10,
                new Label("Notebook:"),
                notebookSelector,
                addNotebook,
                deleteNotebook
        );

        HBox.setHgrow(notebookSelector, Priority.ALWAYS);

        VBox sidebar = new VBox(
                9,
                addNote,
                new Label("Notes"),
                noteList,
                new Label("Search Results"),
                results
        );

        sidebar.setPadding(new Insets(12));
        sidebar.setPrefWidth(290);
        sidebar.setMinWidth(200);

        VBox.setVgrow(noteList, Priority.ALWAYS);
        VBox.setVgrow(results, Priority.ALWAYS);

        VBox content = new VBox(
                10,
                title,
                editor,
                status
        );

        content.setPadding(new Insets(15));
        VBox.setVgrow(editor, Priority.ALWAYS);

        SplitPane split = new SplitPane(sidebar, content);
        split.setDividerPositions(0.27);

        MenuBar menuBar = buildMenu(
                addNotebook,
                deleteNotebook,
                addNote
        );

        // Simplified application header
        Label heading = new Label("ZORZA NOTES");
        heading.getStyleClass().add("app-name");

        VBox root = new VBox(
                10,
                menuBar,
                heading,
                notebookBar,
                search,
                split
        );

        root.setPadding(new Insets(12));
        VBox.setVgrow(split, Priority.ALWAYS);

        Scene scene = new Scene(root, 1180, 760);

        stage.setTitle(APP_NAME);
        stage.setScene(scene);

        applyFontSettings();

        stage.setOnCloseRequest(e -> flushSave());

        stage.show();

        reloadNotebooks(null);
    }

    private void configureControls() {

        notebookSelector.setCellFactory(v -> notebookCell());
        notebookSelector.setButtonCell(notebookCell());

        notebookSelector.valueProperty()
                .addListener((obs, oldBook, book) -> {

                    if (navigating) {
                        return;
                    }

                    if (book == null) {
                        noteList.getItems().clear();
                        current = null;
                        clearEditor();
                        return;
                    }

                    loadNotebook(book);

                    preferences.putLong(
                            LAST_NOTEBOOK,
                            book.getId()
                    );
                });

        noteList.setCellFactory(v -> noteCell());

        results.setCellFactory(v -> new ListCell<>() {

            @Override
            protected void updateItem(Note note, boolean empty) {
                super.updateItem(note, empty);

                setText(
                        empty || note == null
                                ? null
                                : note.toString()
                );
            }
        });

        noteList.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldNote, note) -> {

                    if (note != null && !navigating) {
                        openNote(note);
                    }
                });

        results.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldNote, note) -> {

                    if (note != null) {
                        openResult(note);
                    }
                });

        title.setPromptText("Note title");
        title.setDisable(true);

        title.textProperty()
                .addListener((obs, oldValue, newValue) ->
                        markDirty()
                );

        editor.area().textProperty()
                .addListener((obs, oldValue, newValue) ->
                        markDirty()
                );

        saveDelay.setOnFinished(e -> save());

        search.setPromptText("Search all notebooks");

        search.textProperty()
                .addListener((obs, oldValue, newValue) -> {
                    searchRevision++;
                    searchDelay.playFromStart();
                });

        searchDelay.setOnFinished(e ->
                runSearch(
                        search.getText(),
                        searchRevision
                )
        );
    }

    private ListCell<Notebook> notebookCell() {

        return new ListCell<>() {

            @Override
            protected void updateItem(
                    Notebook book,
                    boolean empty) {

                super.updateItem(book, empty);

                setText(
                        empty || book == null
                                ? null
                                : book.getName()
                );
            }
        };
    }

    private boolean unlock() {

        boolean newVault =
                !Database.databaseExists()
                        || Database.databaseIsPlaintext();

        while (true) {

            Dialog<char[]> dialog = new Dialog<>();

            configureDialog(
                    dialog,
                    newVault
                            ? "Create Encrypted Vault"
                            : "Unlock Encrypted Vault",
                    430,
                    newVault ? 250 : 210
            );

            PasswordField password = new PasswordField();
            PasswordField confirm = new PasswordField();

            password.setPromptText("Vault password");

            VBox form = new VBox(
                    12,
                    new Label(
                            newVault
                                    ? "Choose a password (minimum 8 characters)"
                                    : "Enter your vault password"
                    ),
                    password
            );

            form.setPadding(new Insets(10));

            if (newVault) {
                confirm.setPromptText("Confirm password");
                form.getChildren().add(confirm);
            }

            dialog.getDialogPane().setContent(form);

            ButtonType proceed = new ButtonType(
                    "Continue",
                    ButtonBar.ButtonData.OK_DONE
            );

            dialog.getDialogPane()
                    .getButtonTypes()
                    .addAll(proceed, ButtonType.CANCEL);

            dialog.setResultConverter(button ->
                    button == proceed
                            ? password.getText().toCharArray()
                            : null
            );

            Optional<char[]> result = dialog.showAndWait();

            if (result.isEmpty()) {
                return false;
            }

            char[] entered = result.get();

            try {

                if (entered.length == 0) {
                    throw new IllegalArgumentException(
                            "Password cannot be empty."
                    );
                }

                if (newVault) {

                    if (entered.length < 8) {
                        throw new IllegalArgumentException(
                                "Password must contain at least 8 characters."
                        );
                    }

                    char[] confirmation =
                            confirm.getText().toCharArray();

                    try {

                        if (!Arrays.equals(
                                entered,
                                confirmation
                        )) {
                            throw new IllegalArgumentException(
                                    "Passwords do not match."
                            );
                        }

                    } finally {
                        Arrays.fill(confirmation, '\0');
                    }
                }

                VaultService.unlock(entered);

                if (!newVault) {

                    try (Connection connection =
                                 Database.connect()) {

                        VaultService.verifyConnection(connection);
                    }
                }

                return true;

            } catch (Exception ex) {

                VaultService.clearPassword();
                error("Vault Password", ex);

            } finally {

                Arrays.fill(entered, '\0');
                password.clear();
                confirm.clear();
            }
        }
    }

    private void configureDialog(
            Dialog<?> dialog,
            String heading,
            double width,
            double height) {

        dialog.setTitle(heading);

        if (mainStage != null
                && mainStage.getScene() != null) {

            dialog.initOwner(mainStage);
            dialog.initModality(Modality.WINDOW_MODAL);

        } else {

            dialog.initModality(Modality.APPLICATION_MODAL);
        }

        DialogPane pane = dialog.getDialogPane();

        pane.setPrefSize(width, height);

        pane.setMinSize(
                Region.USE_COMPUTED_SIZE,
                Region.USE_COMPUTED_SIZE
        );

        pane.setMaxSize(
                Region.USE_COMPUTED_SIZE,
                Region.USE_COMPUTED_SIZE
        );

        dialog.setResizable(false);
    }

    private void info(String heading, String message) {

        Alert alert = new Alert(
                Alert.AlertType.INFORMATION
        );

        configureDialog(alert, heading, 460, 230);

        alert.setHeaderText(heading);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void error(String heading, Exception ex) {

        ex.printStackTrace();

        Alert alert = new Alert(
                Alert.AlertType.ERROR
        );

        configureDialog(alert, heading, 480, 250);

        alert.setHeaderText(heading);

        alert.setContentText(
                ex.getMessage() == null
                        ? ex.toString()
                        : ex.getMessage()
        );

        alert.showAndWait();
    }

    private void showAbout() {

        Alert dialog = new Alert(
                Alert.AlertType.INFORMATION
        );

        configureDialog(
                dialog,
                "About Zorza Notes",
                430,
                220
        );

        dialog.setHeaderText(
                "Zorza Notes " + APP_VERSION
        );

        Hyperlink website = new Hyperlink(APP_URL);

        website.setOnAction(e ->
                getHostServices().showDocument(APP_URL)
        );

        Label license = new Label(
                "Zorza Notes is open-source software "
                        + "licensed under the MIT License."
        );

        license.setWrapText(true);

        VBox content = new VBox(
                12,
                new Label("For more information, visit:"),
                website,
                license
        );

        content.setPadding(new Insets(8));

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private Notebook selectedBook() {
        return notebookSelector.getValue();
    }

    private void reloadNotebooks(Long preferredId) {

        flushSave();

        Long targetId = preferredId;

        if (targetId == null) {

            Notebook selected = selectedBook();

            targetId = selected == null
                    ? preferences.getLong(LAST_NOTEBOOK, -1)
                    : selected.getId();
        }

        navigating = true;

        try {

            List<Notebook> allBooks = books.findAll();

            notebookSelector.setItems(
                    FXCollections.observableArrayList(allBooks)
            );

            Notebook chosen = null;

            for (Notebook book : allBooks) {

                if (book.getId() == targetId) {
                    chosen = book;
                    break;
                }
            }

            if (chosen == null && !allBooks.isEmpty()) {
                chosen = allBooks.get(0);
            }

            notebookSelector.setValue(chosen);

        } catch (Exception ex) {

            error("Unable to load notebooks", ex);

        } finally {

            navigating = false;
        }

        Notebook selected = selectedBook();

        if (selected == null) {

            noteList.getItems().clear();
            current = null;
            clearEditor();

        } else {

            preferences.putLong(
                    LAST_NOTEBOOK,
                    selected.getId()
            );

            loadNotebook(selected);
        }
    }

    private void selectNotebook(long notebookId) {

        for (Notebook book : notebookSelector.getItems()) {

            if (book.getId() == notebookId) {

                notebookSelector.setValue(book);
                return;
            }
        }
    }

    private void createNotebook() {

        TextInputDialog dialog = new TextInputDialog();

        configureDialog(
                dialog,
                "New Notebook",
                420,
                200
        );

        dialog.setHeaderText(null);
        dialog.setContentText("Notebook name:");

        dialog.showAndWait()
                .map(String::strip)
                .filter(name -> !name.isBlank())
                .ifPresent(name -> {

                    try {

                        flushSave();

                        Notebook created = books.create(name);

                        reloadNotebooks(created.getId());

                    } catch (Exception ex) {

                        error(
                                "Unable to create notebook",
                                ex
                        );
                    }
                });
    }

    private void deleteNotebook() {

        Notebook book = selectedBook();

        if (book == null) {

            info(
                    "Delete Notebook",
                    "Select a notebook first."
            );

            return;
        }

        flushSave();

        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION
        );

        configureDialog(
                confirmation,
                "Delete Notebook",
                470,
                245
        );

        confirmation.setHeaderText(
                "Delete notebook \"" + book.getName() + "\"?"
        );

        confirmation.setContentText(
                "This permanently deletes the notebook "
                        + "and all notes inside it.\n\n"
                        + "This action cannot be undone."
        );

        ButtonType deleteButton = new ButtonType(
                "Delete Notebook",
                ButtonBar.ButtonData.OK_DONE
        );

        confirmation.getButtonTypes().setAll(
                deleteButton,
                ButtonType.CANCEL
        );

        if (confirmation.showAndWait()
                .orElse(ButtonType.CANCEL) != deleteButton) {
            return;
        }

        try {

            books.delete(book);

            current = null;
            clearEditor();

            preferences.remove(LAST_NOTEBOOK);

            reloadNotebooks(null);
            refreshSearch();

            status.setText("Notebook deleted");

        } catch (Exception ex) {

            error("Unable to delete notebook", ex);
        }
    }

    private void loadNotebook(Notebook book) {

        if (book == null) {
            return;
        }

        flushSave();

        navigating = true;

        try {

            noteList.setItems(
                    FXCollections.observableArrayList(
                            notes.findByNotebook(book.getId())
                    )
            );

            if (current != null
                    && current.getNotebookId() != book.getId()) {

                current = null;
                clearEditor();
            }

        } catch (Exception ex) {

            error("Unable to load notebook", ex);

        } finally {

            navigating = false;
        }
    }

    private Note findListedNote(long noteId) {

        for (Note note : noteList.getItems()) {

            if (note.getId() == noteId) {
                return note;
            }
        }

        return null;
    }

    private void createNote() {

        Notebook book = selectedBook();

        if (book == null) {

            info(
                    "New Note",
                    "Create or select a notebook first."
            );

            return;
        }

        flushSave();

        try {

            Note created = notes.create(book.getId());

            loadNotebook(book);

            Note listed = findListedNote(created.getId());

            if (listed == null) {

                listed = created;
                noteList.getItems().add(listed);
            }

            noteList.getSelectionModel().select(listed);
            openNote(listed);

            title.requestFocus();
            title.selectAll();

        } catch (Exception ex) {

            error("Unable to create note", ex);
        }
    }

    private void openNote(Note note) {

        if (note == null) {
            return;
        }

        if (current != null
                && current.getId() == note.getId()) {
            return;
        }

        flushSave();

        loading = true;

        try {

            current = note;

            title.setDisable(false);

            title.setText(
                    note.getTitle() == null
                            ? ""
                            : note.getTitle()
            );

            editor.area().replaceText(
                    note.getBody() == null
                            ? ""
                            : note.getBody()
            );

            status.setText("Editing encrypted note");

        } finally {

            loading = false;
        }
    }

    private void openResult(Note note) {

        if (note == null) {
            return;
        }

        flushSave();

        selectNotebook(note.getNotebookId());

        Note listed = findListedNote(note.getId());

        if (listed != null) {

            noteList.getSelectionModel().select(listed);
            openNote(listed);

        } else {

            openNote(note);
        }

        editor.showMatch(search.getText());
    }

    private void clearEditor() {

        loading = true;

        try {

            title.clear();
            title.setDisable(true);

            editor.area().clear();

            status.setText("Ready");

        } finally {

            loading = false;
        }
    }

    private void markDirty() {

        if (loading || current == null) {
            return;
        }

        editRevision++;

        status.setText("Saving...");

        Note listed = findListedNote(current.getId());

        if (listed != null) {

            listed.setTitle(
                    title.getText().isBlank()
                            ? "Untitled Note"
                            : title.getText()
            );

            noteList.refresh();
        }

        saveDelay.playFromStart();
    }

    private void save() {

        saveDelay.stop();

        if (loading || current == null) {
            return;
        }

        long noteId = current.getId();

        if (queuedRevision == editRevision
                && queuedNoteId == noteId) {
            return;
        }

        String noteTitle =
                title.getText().isBlank()
                        ? "Untitled Note"
                        : title.getText();

        Note snapshot = new Note(
                current.getId(),
                current.getUuid(),
                current.getNotebookId(),
                noteTitle,
                editor.area().getText()
        );

        long revision = editRevision;

        queuedRevision = revision;
        queuedNoteId = noteId;

        pendingSave = io.submit(() -> {

            try {

                notes.update(snapshot);

                Platform.runLater(() -> {

                    if (current != null
                            && current.getId() == snapshot.getId()
                            && revision == editRevision) {

                        Note listed = findListedNote(
                                snapshot.getId()
                        );

                        if (listed != null) {

                            listed.setTitle(snapshot.getTitle());
                            listed.setBody(snapshot.getBody());

                            noteList.refresh();
                        }

                        status.setText(
                                "Saved locally • encrypted"
                        );
                    }
                });

            } catch (Exception ex) {

                Platform.runLater(() -> {

                    if (queuedRevision == revision
                            && queuedNoteId == noteId) {

                        queuedRevision = -1;
                        queuedNoteId = -1;
                    }

                    status.setText("Save failed");
                    error("Save failed", ex);
                });
            }
        });
    }

    private void flushSave() {

        save();

        Future<?> task = pendingSave;

        if (task == null) {
            return;
        }

        try {

            task.get();

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();
            error("Save interrupted", ex);

        } catch (ExecutionException ex) {

            error(
                    "Save failed",
                    new RuntimeException(ex.getCause())
            );
        }
    }

    private ListCell<Note> noteCell() {

        ListCell<Note> cell = new ListCell<>() {

            @Override
            protected void updateItem(
                    Note note,
                    boolean empty) {

                super.updateItem(note, empty);

                setText(
                        empty || note == null
                                ? null
                                : note.toString()
                );
            }
        };

        MenuItem open = new MenuItem("Open");
        Menu move = new Menu("Move to Notebook");
        MenuItem delete = new MenuItem("Delete Note");

        open.setOnAction(e -> {

            if (cell.getItem() != null) {
                openNote(cell.getItem());
            }
        });

        delete.setOnAction(e -> {

            if (cell.getItem() != null) {
                deleteNote(cell.getItem());
            }
        });

        ContextMenu context = new ContextMenu(
                open,
                move,
                new SeparatorMenuItem(),
                delete
        );

        context.setOnShowing(e -> {

            move.getItems().clear();

            Note note = cell.getItem();

            if (note == null) {
                return;
            }

            for (Notebook book : books.findAll()) {

                if (book.getId() == note.getNotebookId()) {
                    continue;
                }

                MenuItem item = new MenuItem(book.getName());

                item.setOnAction(event ->
                        moveNote(note, book)
                );

                move.getItems().add(item);
            }

            move.setDisable(move.getItems().isEmpty());
        });

        cell.itemProperty()
                .addListener((obs, oldNote, note) ->
                        cell.setContextMenu(
                                note == null
                                        ? null
                                        : context
                        )
                );

        return cell;
    }

    private void deleteNote(Note note) {

        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION
        );

        configureDialog(
                confirmation,
                "Delete Note",
                430,
                210
        );

        confirmation.setHeaderText("Delete this note?");
        confirmation.setContentText(note.toString());

        confirmation.getButtonTypes().setAll(
                ButtonType.YES,
                ButtonType.NO
        );

        if (confirmation.showAndWait()
                .orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        flushSave();

        try {

            notes.delete(note);

            if (current != null
                    && current.getId() == note.getId()) {

                current = null;
                clearEditor();
            }

            loadNotebook(selectedBook());
            refreshSearch();

        } catch (Exception ex) {

            error("Unable to delete note", ex);
        }
    }

    private void moveNote(
            Note note,
            Notebook destination) {

        flushSave();

        try {

            notes.moveToNotebook(
                    note.getId(),
                    destination.getId()
            );

            if (current != null
                    && current.getId() == note.getId()) {

                current = null;
                clearEditor();
            }

            loadNotebook(selectedBook());
            refreshSearch();

        } catch (Exception ex) {

            error("Unable to move note", ex);
        }
    }

    private void refreshSearch() {

        runSearch(
                search.getText(),
                ++searchRevision
        );
    }

    private void runSearch(
            String query,
            long revision) {

        if (query == null || query.isBlank()) {

            results.getItems().clear();
            return;
        }

        io.submit(() -> {

            try {

                List<Note> found = notes.searchAll(query);

                Platform.runLater(() -> {

                    if (revision == searchRevision) {

                        results.setItems(
                                FXCollections.observableArrayList(
                                        found
                                )
                        );
                    }
                });

            } catch (Exception ex) {

                Platform.runLater(() ->
                        error("Search failed", ex)
                );
            }
        });
    }

    private void exportNotes() {

        flushSave();

        DirectoryChooser chooser = new DirectoryChooser();

        chooser.setTitle("Export All Zorza Notes");

        File destination = chooser.showDialog(mainStage);

        if (destination == null) {
            return;
        }

        try {

            int count = importExport.exportAll(
                    destination.toPath()
            );

            info(
                    "Export Complete",
                    count + " notes exported as Markdown.\n\n"
                            + destination.getAbsolutePath()
                            + "\n\nExported files are not encrypted."
            );

        } catch (Exception ex) {

            error("Export failed", ex);
        }
    }

    private void importNotes() {

        flushSave();

        DirectoryChooser chooser = new DirectoryChooser();

        chooser.setTitle("Import Markdown Notes");

        File selected = chooser.showDialog(mainStage);

        if (selected == null) {
            return;
        }

        try {

            Path directory = selected.toPath();
            int imported = 0;

            List<Path> subdirectories;

            try (var stream = Files.list(directory)) {

                subdirectories = stream
                        .filter(Files::isDirectory)
                        .sorted()
                        .toList();
            }

            if (containsMarkdown(directory)) {

                imported += importExport.importDirectory(
                        directory
                );
            }

            for (Path child : subdirectories) {

                if (containsMarkdown(child)) {

                    imported += importExport.importDirectory(
                            child
                    );
                }
            }

            reloadNotebooks(null);
            refreshSearch();

            info(
                    "Import Complete",
                    imported + " Markdown notes imported."
            );

        } catch (Exception ex) {

            error("Import failed", ex);
        }
    }

    private boolean containsMarkdown(Path directory)
            throws java.io.IOException {

        try (var stream = Files.list(directory)) {

            return stream.anyMatch(path ->
                    Files.isRegularFile(path)
                            && path.getFileName()
                            .toString()
                            .toLowerCase(Locale.ROOT)
                            .endsWith(".md")
            );
        }
    }

    private void showSettings() {

        Dialog<ButtonType> dialog = new Dialog<>();

        configureDialog(
                dialog,
                "Fonts and Appearance",
                500,
                360
        );

        ComboBox<String> noteFont = new ComboBox<>();
        ComboBox<String> appFont = new ComboBox<>();

        noteFont.getItems().setAll(Font.getFamilies());
        appFont.getItems().setAll(Font.getFamilies());

        noteFont.setValue(AppSettings.getNoteFont());
        appFont.setValue(AppSettings.getAppFont());

        Spinner<Integer> noteSize = new Spinner<>(
                10,
                48,
                Math.max(
                        10,
                        Math.min(
                                48,
                                AppSettings.getNoteFontSize()
                        )
                )
        );

        Spinner<Integer> appSize = new Spinner<>(
                10,
                28,
                Math.max(
                        10,
                        Math.min(
                                28,
                                AppSettings.getAppFontSize()
                        )
                )
        );

        noteSize.setEditable(true);
        appSize.setEditable(true);

        noteFont.setMaxWidth(Double.MAX_VALUE);
        appFont.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();

        grid.setHgap(14);
        grid.setVgap(16);
        grid.setPadding(new Insets(16));

        grid.add(new Label("Note editor font"), 0, 0);
        grid.add(noteFont, 1, 0);

        grid.add(new Label("Note editor size"), 0, 1);
        grid.add(noteSize, 1, 1);

        grid.add(new Label("Application font"), 0, 2);
        grid.add(appFont, 1, 2);

        grid.add(new Label("Application size"), 0, 3);
        grid.add(appSize, 1, 3);

        GridPane.setHgrow(noteFont, Priority.ALWAYS);
        GridPane.setHgrow(appFont, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);

        dialog.getDialogPane()
                .getButtonTypes()
                .addAll(
                        ButtonType.OK,
                        ButtonType.CANCEL
                );

        dialog.showAndWait().ifPresent(button -> {

            if (button != ButtonType.OK) {
                return;
            }

            try {

                noteSize.getValueFactory().setValue(
                        Integer.parseInt(
                                noteSize.getEditor().getText()
                        )
                );

                appSize.getValueFactory().setValue(
                        Integer.parseInt(
                                appSize.getEditor().getText()
                        )
                );

                AppSettings.setNoteFont(
                        noteFont.getValue()
                );

                AppSettings.setNoteFontSize(
                        noteSize.getValue()
                );

                AppSettings.setAppFont(
                        appFont.getValue()
                );

                AppSettings.setAppFontSize(
                        appSize.getValue()
                );

                applyFontSettings();

                status.setText(
                        "Appearance settings saved"
                );

            } catch (Exception ex) {

                error(
                        "Unable to save settings",
                        ex
                );
            }
        });
    }

    private void applyFontSettings() {

        if (mainStage != null
                && mainStage.getScene() != null) {

            Scene scene = mainStage.getScene();

            ThemeManager.apply(scene);

            scene.getRoot()
                    .getStyleClass()
                    .remove("zorza-dark");

            if (AppSettings.isDarkMode()) {

                scene.getRoot()
                        .getStyleClass()
                        .add("zorza-dark");
            }
        }

        if (editor != null) {

            editor.setEditorFont(
                    AppSettings.getNoteFont(),
                    AppSettings.getNoteFontSize()
            );
        }
    }

    private MenuBar buildMenu(
            Button addNotebook,
            Button deleteNotebook,
            Button addNote) {

        Menu file = new Menu("File");

        MenuItem newNotebook =
                new MenuItem("New Notebook");

        MenuItem newNote =
                new MenuItem("New Note");

        MenuItem importItem =
                new MenuItem("Import Markdown Folder...");

        MenuItem exportItem =
                new MenuItem("Export All Notes...");

        MenuItem exit =
                new MenuItem("Exit");

        newNotebook.setOnAction(e ->
                addNotebook.fire()
        );

        newNote.setOnAction(e ->
                addNote.fire()
        );

        importItem.setOnAction(e ->
                importNotes()
        );

        exportItem.setOnAction(e ->
                exportNotes()
        );

        exit.setOnAction(e ->
                mainStage.close()
        );

        file.getItems().addAll(
                newNotebook,
                newNote,
                new SeparatorMenuItem(),
                importItem,
                exportItem,
                new SeparatorMenuItem(),
                exit
        );

        Menu edit = new Menu("Edit");

        MenuItem undo = new MenuItem("Undo");
        MenuItem redo = new MenuItem("Redo");

        MenuItem deleteNoteItem =
                new MenuItem("Delete Note");

        MenuItem deleteNotebookItem =
                new MenuItem("Delete Notebook");

        undo.setOnAction(e ->
                editor.area().undo()
        );

        redo.setOnAction(e ->
                editor.area().redo()
        );

        deleteNoteItem.setOnAction(e -> {

            if (current != null) {
                deleteNote(current);
            }
        });

        deleteNotebookItem.setOnAction(e ->
                deleteNotebook.fire()
        );

        edit.getItems().addAll(
                undo,
                redo,
                new SeparatorMenuItem(),
                deleteNoteItem,
                deleteNotebookItem
        );

        Menu view = new Menu("View");

        CheckMenuItem darkMode =
                new CheckMenuItem("Dark Mode");

        darkMode.setSelected(
                AppSettings.isDarkMode()
        );

        darkMode.setOnAction(e -> {

            AppSettings.setDarkMode(
                    darkMode.isSelected()
            );

            applyFontSettings();
        });

        view.getItems().add(darkMode);

        Menu settings = new Menu("Settings");

        MenuItem appearance =
                new MenuItem("Fonts and Appearance...");

        appearance.setOnAction(e ->
                showSettings()
        );

        settings.getItems().add(appearance);

        Menu help = new Menu("Help");

        MenuItem about =
                new MenuItem("About Zorza Notes");

        about.setOnAction(e ->
                showAbout()
        );

        help.getItems().add(about);

        return new MenuBar(
                file,
                edit,
                view,
                settings,
                help
        );
    }

    @Override
    public void stop() {

        io.shutdown();

        try {

            if (!io.awaitTermination(
                    10,
                    TimeUnit.SECONDS
            )) {

                System.err.println(
                        "Pending database operations at shutdown"
                );
            }

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();

        } finally {

            VaultService.clearPassword();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
