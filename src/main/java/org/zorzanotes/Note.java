package org.zorzanotes;

public class Note {

    private final long id;
    private final String uuid;
    private final long notebookId;
    private String title;
    private String body;

    public Note(long id, String uuid, long notebookId,
                String title, String body) {

        this.id = id;
        this.uuid = uuid;
        this.notebookId = notebookId;
        this.title = title;
        this.body = body;
    }

    public long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public long getNotebookId() {
        return notebookId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return title.isBlank() ? "Untitled Note" : title;
    }
}