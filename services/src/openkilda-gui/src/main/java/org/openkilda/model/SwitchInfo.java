package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/**
 * The Class SwitchInfo.
 *
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"switch_id", "address", "hostname", "description"})
public class SwitchInfo implements Serializable {
    @JsonProperty("switch_id")
    private String switchId;
    @JsonProperty("address")
    private String address;
    @JsonProperty("hostname")
    private String hostname;
    @JsonProperty("description")
    private String description;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 6763064864461521069L;

    public String getSwitchId() {
        return switchId;
    }

    public void setSwitchId(String switchId) {
        this.switchId = switchId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "SwitchInfo [switchId=" + switchId + ", address=" + address + ", hostname="
                + hostname + ", description=" + description + "]";
    }

}
