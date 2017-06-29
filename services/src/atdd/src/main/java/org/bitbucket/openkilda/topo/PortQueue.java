package org.bitbucket.openkilda.topo;

/**
 * PortQueue Identifies a queue on a port
 */
public class PortQueue implements ITopoSlug {

    private final Port parent;
    private final String id;
    private int priority = -1;
    private String slug;

    public PortQueue(Port parent, String id) {
        this.parent = parent;
        this.id = id;
    }

    public PortQueue(Port parent, String id, int priority) {
        this.parent = parent;
        this.id = id;
        this.priority = priority;
    }

    public Port getParent() {
        return parent;
    }

    public String getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public String getSlug() {
        if (slug == null)
            slug = TopoSlug.toString(this);
        return slug;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PortQueue portQueue = (PortQueue) o;

        if (priority != portQueue.priority) return false;
        if (!parent.equals(portQueue.parent)) return false;
        return id.equals(portQueue.id);
    }

    @Override
    public int hashCode() {
        int result = parent.hashCode();
        result = 31 * result + id.hashCode();
        result = 31 * result + priority;
        return result;
    }

    @Override
    public String toString() {
        return "PortQueue{" +
                "parent=" + parent +
                ", id='" + id + '\'' +
                ", priority=" + priority +
                '}';
    }
}
