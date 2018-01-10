package org.openkilda.integration.model.response;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class PortResponseData.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"name", "ports"})
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortResponseData implements Serializable {

    /** The name. */
    @JsonProperty("name")
    private String name;

    /** The ports. */
    @JsonProperty("ports")
    private List<Port> ports = null;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = -3592717857590516867L;

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

    /**
     * Gets the ports.
     *
     * @return the ports
     */
    @JsonProperty("ports")
    public List<Port> getPorts() {
        return ports;
    }

    /**
     * Sets the ports.
     *
     * @param ports the new ports
     */
    @JsonProperty("ports")
    public void setPorts(List<Port> ports) {
        this.ports = ports;
    }

}
