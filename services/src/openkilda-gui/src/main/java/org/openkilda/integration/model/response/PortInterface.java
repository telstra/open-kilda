package org.openkilda.integration.model.response;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class PortInterface.
 * 
 * @author Gaurav Chugh
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"status", "mac", "name"})
public class PortInterface implements Serializable {

    /** The status. */
    @JsonProperty("status")
    private String status;

    /** The mac. */
    @JsonProperty("mac")
    private Object mac;

    /** The name. */
    @JsonProperty("name")
    private String name;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 947268727227706248L;

    /**
     * Gets the status.
     *
     * @return the status
     */
    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status.
     *
     * @param status the new status
     */
    @JsonProperty("status")
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets the mac.
     *
     * @return the mac
     */
    @JsonProperty("mac")
    public Object getMac() {
        return mac;
    }

    /**
     * Sets the mac.
     *
     * @param mac the new mac
     */
    @JsonProperty("mac")
    public void setMac(Object mac) {
        this.mac = mac;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the new name
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }
}
