package org.openkilda.atdd.staging.model.traffexam;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.InputMismatchException;

public final class EndpointAddress implements Serializable {
    public final Inet4Address address;
    public final int port;

    public EndpointAddress(Inet4Address address, int port) {
        this.address = address;
        this.port = port;
    }

    @JsonCreator
    public EndpointAddress(
            @JsonProperty("address") String address,
            @JsonProperty("port") int port) {
        try {
            this.address = (Inet4Address) Inet4Address.getByName(address);
        } catch (UnknownHostException e) {
            throw new InputMismatchException(String.format("Invalid value for \"address\" property: %s", address));
        }
        this.port = port;
    }
}
