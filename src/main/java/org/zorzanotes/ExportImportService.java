package org.zorzanotes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class ExportImportService {

    private final NotebookRepository notebookRepository;
    private final NoteRepository noteRepository;

    public ExportImportService(
            NotebookRepository notebookRepository,
            NoteRepository noteRepository) {

        this.notebookRepository = notebookRepository;
        this.noteRepository = noteRepository;
    }

    // =============================================================
    // EXPORT EVERYTHING
    // =============================================================

    public int exportAll(Path destination)
            throws IOException {

        Files.createDirectories(destination);

        List<Notebook> notebooks =
                notebookRepository.findAll();

        int exportedNotes = 0;

        for (Notebook notebook : notebooks) {

            String notebookDirectoryName =
                    safeFileName(
                            notebook.getName()
                    );

            Path notebookDirectory =
                    uniqueDirectory(
                            destination,
                            notebookDirectoryName
                    );

            Files.createDirectories(
                    notebookDirectory
            );

            List<Note> notes =
                    noteRepository.findByNotebook(
                            notebook.getId()
                    );

            for (Note note : notes) {

                String fileName =
                        safeFileName(
                                note.getTitle()
                        );

                if (fileName.isBlank()) {
                    fileName = "Untitled Note";
                }

                Path noteFile =
                        uniqueFile(
                                notebookDirectory,
                                fileName,
                                ".md"
                        );

                String markdown =
                        buildMarkdown(note);

                Files.writeString(
                        noteFile,
                        markdown,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW
                );

                exportedNotes++;
            }
        }

        return exportedNotes;
    }

    // =============================================================
    // EXPORT NOTE
    // =============================================================

    private String buildMarkdown(
            Note note) {

        String title =
                note.getTitle() == null ||
                        note.getTitle().isBlank()
                        ? "Untitled Note"
                        : note.getTitle().trim();

        String body =
                note.getBody() == null
                        ? ""
                        : note.getBody();

        return "# " +
                title +
                System.lineSeparator() +
                System.lineSeparator() +
                body +
                System.lineSeparator();
    }

    // =============================================================
    // IMPORT DIRECTORY AS NOTEBOOK
    // =============================================================

    public int importDirectory(
            Path directory)
            throws IOException {

        if (!Files.isDirectory(directory)) {

            throw new IOException(
                    "Import source is not a directory."
            );
        }

        String notebookName =
                directory.getFileName()
                        .toString();

        Notebook notebook =
                notebookRepository.create(
                        notebookName
                );

        int importedNotes = 0;

        try (var files =
                     Files.list(directory)) {

            List<Path> markdownFiles =
                    files
                            .filter(Files::isRegularFile)
                            .filter(path ->
                                    path.getFileName()
                                            .toString()
                                            .toLowerCase()
                                            .endsWith(".md")
                            )
                            .sorted()
                            .toList();

            for (Path file :
                    markdownFiles) {

                importMarkdownFile(
                        notebook,
                        file
                );

                importedNotes++;
            }
        }

        return importedNotes;
    }

    // =============================================================
    // IMPORT ONE MARKDOWN FILE
    // =============================================================

    private void importMarkdownFile(
            Notebook notebook,
            Path file)
            throws IOException {

        String contents =
                Files.readString(
                        file,
                        StandardCharsets.UTF_8
                );

        String fileName =
                file.getFileName()
                        .toString();

        String title =
                removeMarkdownExtension(
                        fileName
                );

        String body =
                contents;

        /*
         * Zorza exports:
         *
         * # Title
         *
         * body
         *
         * If that header exists, recover the title and
         * remove it from the editable body.
         */

        if (contents.startsWith("# ")) {

            int firstNewline =
                    contents.indexOf('\n');

            if (firstNewline >= 0) {

                title =
                        contents
                                .substring(
                                        2,
                                        firstNewline
                                )
                                .trim();

                body =
                        contents.substring(
                                firstNewline + 1
                        );

                if (body.startsWith("\r\n")) {

                    body =
                            body.substring(2);

                } else if (body.startsWith("\n")) {

                    body =
                            body.substring(1);
                }

            } else {

                title =
                        contents
                                .substring(2)
                                .trim();

                body = "";
            }
        }

        noteRepository.create(
                notebook.getId(),
                title,
                body
        );
    }

    // =============================================================
    // SAFE FILE NAME
    // =============================================================

    private String safeFileName(
            String name) {

        if (name == null) {
            return "Untitled";
        }

        String cleaned =
                name.trim()
                        .replaceAll(
                                "[\\\\/:*?\"<>|]",
                                "_"
                        );

        while (cleaned.endsWith(".")) {

            cleaned =
                    cleaned.substring(
                            0,
                            cleaned.length() - 1
                    );
        }

        if (cleaned.isBlank()) {
            return "Untitled";
        }

        return cleaned;
    }

    // =============================================================
    // UNIQUE DIRECTORY
    // =============================================================

    private Path uniqueDirectory(
            Path parent,
            String name) {

        Path candidate =
                parent.resolve(name);

        int counter = 2;

        while (Files.exists(candidate)) {

            candidate =
                    parent.resolve(
                            name +
                                    " (" +
                                    counter +
                                    ")"
                    );

            counter++;
        }

        return candidate;
    }

    // =============================================================
    // UNIQUE FILE
    // =============================================================

    private Path uniqueFile(
            Path directory,
            String baseName,
            String extension) {

        Path candidate =
                directory.resolve(
                        baseName +
                                extension
                );

        int counter = 2;

        while (Files.exists(candidate)) {

            candidate =
                    directory.resolve(
                            baseName +
                                    " (" +
                                    counter +
                                    ")" +
                                    extension
                    );

            counter++;
        }

        return candidate;
    }

    // =============================================================
    // REMOVE .MD
    // =============================================================

    private String removeMarkdownExtension(
            String fileName) {

        if (fileName.toLowerCase()
                .endsWith(".md")) {

            return fileName.substring(
                    0,
                    fileName.length() - 3
            );
        }

        return fileName;
    }
}