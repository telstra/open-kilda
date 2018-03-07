package org.openkilda.atdd.staging.model.traffexam;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.InputMismatchException;
import java.util.UUID;

public class Address extends HostResource {
    @JsonDeserialize(using = VlanJsonDeserializer.class)
    @JsonSerialize(using = VlanJsonSerializer.class)
    private Vlan vlan = null;

    private Inet4Address address;
    private final int prefix;

    public Address(Inet4Address address, int prefix) {
        this(null, address, prefix);
    }

    public Address(UUID id, Inet4Address address, int prefix) {
        super(id);
        this.address = address;
        this.prefix = prefix;
    }

    @JsonCreator
    public Address(
            @JsonProperty("idnr") UUID id,
            @JsonProperty("address") String address,
            @JsonProperty("prefix") int prefix) {
        this(id, (Inet4Address) null, prefix);
        try {
            this.address = (Inet4Address) Inet4Address.getByName(address);
        } catch (UnknownHostException e) {
            throw new InputMismatchException(String.format("Invalid value for \"address\" property: %s", address));
        }
    }

    public Vlan getVlan() {
        return vlan;
    }

    public void setVlan(Vlan vlan) {
        this.vlan = vlan;
    }

    public Inet4Address getAddress() {
        return address;
    }

    public int getPrefix() {
        return prefix;
    }
}
