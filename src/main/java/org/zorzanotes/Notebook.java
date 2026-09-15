package org.zorzanotes;

public class Notebook {

    private final long id;
    private final String uuid;
    private final String name;

    public Notebook(long id, String uuid, String name) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}