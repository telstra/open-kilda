package org.openkilda.integration.model.response;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class PortDetailResponse.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"port_desc", "version"})
public class PortDetailResponse implements Serializable {

    /** The port desc. */
    @JsonProperty("port_desc")
    private List<PortDesc> portDesc = null;

    /** The version. */
    @JsonProperty("version")
    private String version;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = -6962984701690909331L;

    /**
     * Gets the port desc.
     *
     * @return the port desc
     */
    @JsonProperty("port_desc")
    public List<PortDesc> getPortDesc() {
        return portDesc;
    }

    /**
     * Sets the port desc.
     *
     * @param portDesc the new port desc
     */
    @JsonProperty("port_desc")
    public void setPortDesc(List<PortDesc> portDesc) {
        this.portDesc = portDesc;
    }

    /**
     * Gets the version.
     *
     * @return the version
     */
    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

    /**
     * Sets the version.
     *
     * @param version the new version
     */
    @JsonProperty("version")
    public void setVersion(String version) {
        this.version = version;
    }

}
