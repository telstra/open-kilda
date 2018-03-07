package org.openkilda.atdd.staging.model.traffexam;

import java.io.Serializable;
import java.net.URI;
import java.util.UUID;

public class Host implements Serializable {
    private final UUID id;
    private final String iface;
    private final URI apiAddress;
    private final String name;

    public Host(UUID id, String iface, URI apiAddress, String name) {
        this.id = id;
        this.iface = iface;
        this.apiAddress = apiAddress;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getIface() {
        return iface;
    }

    public URI getApiAddress() {
        return apiAddress;
    }

    public String getName() {
        return name;
    }
}
