package org.bitbucket.openkilda.topo;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Port implements ITopoSlug {

    /** The PortQueue.getId() is used as the key */
	private final Map<String, PortQueue> portQueues = new ConcurrentHashMap<String, PortQueue>(10);
    private final String id;
    private final Switch parent;
    private String slug;

    public Port(Switch parent, String id) {
        this.id = id;
        this.parent = parent;
    }

    public Map<String, PortQueue> getPortQueues() {
        return portQueues;
    }

    public String getId() {
        return id;
    }

    public Switch getParent() {
        return parent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Port port = (Port) o;

        // TODO: does portQueues.equals work if the objects are equivalent (ie not same obj)
        if (portQueues != null ? !portQueues.equals(port.portQueues) : port.portQueues != null)
            return false;
        if (id != null ? !id.equals(port.id) : port.id != null) return false;
        return parent != null ? parent.equals(port.parent) : port.parent == null;
    }

    @Override
    public int hashCode() {
        int result = portQueues != null ? portQueues.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (parent != null ? parent.hashCode() : 0);
        return result;
    }

    @Override
    public String getSlug() {
        if (slug == null)
            slug = TopoSlug.toString(this);
        return slug;
    }
}
