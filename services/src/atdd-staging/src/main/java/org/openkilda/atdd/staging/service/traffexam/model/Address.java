package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.InputMismatchException;
import java.util.UUID;

@Value
public class Address extends HostResource {

    @JsonSerialize(using = VlanJsonSerializer.class)
    private Vlan vlan;
    private Inet4Address address;
    private int prefix;

    public Address(Inet4Address address, int prefix, Vlan vlan) {
        super(null);
        this.address = address;
        this.prefix = prefix;
        this.vlan = vlan;
    }

    @JsonCreator
    public Address(
            @JsonProperty("idnr") UUID id,
            @JsonProperty("address") String address,
            @JsonProperty("prefix") int prefix,
            @JsonProperty("vlan")
            @JsonDeserialize(using = VlanJsonDeserializer.class) Vlan vlan) {
        super(id);

        try {
            this.address = (Inet4Address) Inet4Address.getByName(address);
        } catch (UnknownHostException e) {
            throw new InputMismatchException(String.format("Invalid value for \"address\" property: %s", address));
        }

        this.prefix = prefix;
        this.vlan = vlan;
    }
}
