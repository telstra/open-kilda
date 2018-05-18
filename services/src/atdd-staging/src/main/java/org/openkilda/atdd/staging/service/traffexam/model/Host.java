package org.openkilda.atdd.staging.service.traffexam.model;

import lombok.Value;
import lombok.experimental.NonFinal;

import java.io.Serializable;
import java.net.URI;
import java.util.UUID;

@Value
@NonFinal
public class Host implements Serializable {

    private UUID id;
    private String iface;
    private URI apiAddress;
    private String name;
}
