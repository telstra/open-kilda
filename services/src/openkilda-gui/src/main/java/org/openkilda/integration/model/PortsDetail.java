package org.openkilda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.List;

/**
 * The Class PortDetailResponse.
 *
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"port_desc", "version"})
public class PortsDetail implements Serializable {

    private final static long serialVersionUID = -6962984701690909331L;

    @JsonProperty("port_desc")
    private List<PortDetail> portDetail = null;

    @JsonProperty("version")
    private String version;

    public List<PortDetail> getPortDetail() {
        return portDetail;
    }

    public void setPortDetail(final List<PortDetail> portDetail) {
        this.portDetail = portDetail;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }
}