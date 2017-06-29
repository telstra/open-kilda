package org.bitbucket.openkilda.topo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Switch implements ITopoSlug {

    /** The Port.getId() is used as the key */
    @JsonIgnore
    private final Map<String, Port> ports = new ConcurrentHashMap<String, Port>(48);
    private final String id;
    @JsonIgnore
	private String slug;

    public Switch(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Map<String, Port> getPorts() {
        return ports;
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
        if (!(o instanceof Switch)) return false;

        Switch aSwitch = (Switch) o;

        if (!ports.equals(aSwitch.ports)) return false;
        return id != null ? id.equals(aSwitch.id) : aSwitch.id == null;
    }

    @Override
    public int hashCode() {
        int result = ports.hashCode();
        result = 31 * result + (id != null ? id.hashCode() : 0);
        return result;
    }
}
