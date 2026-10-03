//package StructuralDesignPatterns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class FileSystemComponent {
    protected String name;

    public FileSystemComponent(String name) {
        this.name = name;
    }

    abstract void zip();
    abstract void rename(String newName);
    abstract int getSize();
    abstract void list();
}

class File extends FileSystemComponent {
    private String content;

    public File(String name, String content) {
        super(name);
        this.content = content == null ? "" : content;
    }

    public void append(String add) {
        this.content += add;
    }

    @Override
    void zip() {
        System.out.println("Zipping file: " + name);
    }

    @Override
    void rename(String newName) {
        this.name = newName;
        System.out.println("File renamed to: " + this.name);
    }

    @Override
    int getSize() {
        return content.length();
    }

    @Override
    void list() {
        System.out.println("File: " + name + " (size=" + getSize() + ")");
    }
}

class Folder extends FileSystemComponent {
    private final List<FileSystemComponent> children;
    private final Map<String, FileSystemComponent> lookupTable;

    public Folder(String name) {
        super(name);
        this.children = new ArrayList<>();
        this.lookupTable = new HashMap<>();
    }

    public void addComponent(FileSystemComponent component) {
        children.add(component);
        lookupTable.put(component.name, component);
    }

    public void addFile(String name, String content) {
        File file = new File(name, content);
        addComponent(file);
    }

    public void addFolder(String name) {
        Folder folder = new Folder(name);
        addComponent(folder);
    }

    public void remove(String name) {
        children.removeIf(child -> child.name.equals(name));
        lookupTable.remove(name);
    }

    @Override
    void zip() {
        System.out.println("Zipping folder: " + name);
        for (FileSystemComponent child : children) {
            child.zip();
        }
    }

    @Override
    void rename(String newName) {
        this.name = newName;
        System.out.println("Folder renamed to: " + this.name);
    }

    @Override
    int getSize() {
        int totalSize = 0;
        for (FileSystemComponent child : children) {
            totalSize += child.getSize();
        }
        return totalSize;
    }

    @Override
    void list() {
        System.out.println("Folder: " + name);
        for (FileSystemComponent child : children) {
            child.list();
        }
    }

    public FileSystemComponent search(String path) {
        if (path == null || path.isEmpty()) {
            return this;
        }

        String[] parts = path.split("/");
        if (parts.length == 0) {
            return null;
        }

        if (parts.length == 1) {
            return this.name.equals(parts[0]) ? this : lookupTable.get(parts[0]);
        }

        if (!this.name.equals(parts[0])) {
            return null;
        }

        FileSystemComponent current = this;
        for (int i = 1; i < parts.length; i++) {
            if (!(current instanceof Folder)) {
                return null;
            }

            Folder folder = (Folder) current;
            current = folder.lookupTable.get(parts[i]);
            if (current == null) {
                return null;
            }
        }

        return current;
    }
}

public class CompositeDesignPattern {
    public static void main(String[] args) {
        Folder root = new Folder("root");
        root.addFile("notes.txt", "hello world");

        Folder documents = new Folder("documents");
        documents.addFile("resume.txt", "My resume");
        documents.addFile("coverletter.txt", "Dear Hiring Manager");

        Folder images = new Folder("images");
        images.addFile("photo.jpg", "binary-data");

        root.addComponent(documents);
        root.addComponent(images);

        FileSystemComponent docFolder = root.search("root/documents");
        if (docFolder != null) {
            docFolder.list();
        }

        root.list();
        System.out.println("Total size: " + root.getSize() + " bytes");
        root.zip();

        root.rename("project-root");
        System.out.println("Searched item: " + root.search("project-root/documents"));
    }
}
