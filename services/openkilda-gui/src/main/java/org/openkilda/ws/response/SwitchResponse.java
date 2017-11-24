package org.openkilda.ws.response;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class SwitchResponse.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"inetAddress", "connectedSince", "openFlowVersion", "switchDPID"})
public class SwitchResponse implements Serializable {

    /** The inet address. */
    @JsonProperty("inetAddress")
    private String inetAddress;

    /** The connected since. */
    @JsonProperty("connectedSince")
    private int connectedSince;

    /** The open flow version. */
    @JsonProperty("openFlowVersion")
    private String openFlowVersion;

    /** The switch dpid. */
    @JsonProperty("switchDPID")
    private String switchDPID;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 1314010152898200597L;

    /**
     * Gets the inet address.
     *
     * @return the inet address
     */
    @JsonProperty("inetAddress")
    public String getInetAddress() {
        return inetAddress;
    }

    /**
     * Sets the inet address.
     *
     * @param inetAddress the new inet address
     */
    @JsonProperty("inetAddress")
    public void setInetAddress(String inetAddress) {
        this.inetAddress = inetAddress;
    }

    /**
     * Gets the connected since.
     *
     * @return the connected since
     */
    @JsonProperty("connectedSince")
    public int getConnectedSince() {
        return connectedSince;
    }

    /**
     * Sets the connected since.
     *
     * @param connectedSince the new connected since
     */
    @JsonProperty("connectedSince")
    public void setConnectedSince(int connectedSince) {
        this.connectedSince = connectedSince;
    }

    /**
     * Gets the open flow version.
     *
     * @return the open flow version
     */
    @JsonProperty("openFlowVersion")
    public String getOpenFlowVersion() {
        return openFlowVersion;
    }

    /**
     * Sets the open flow version.
     *
     * @param openFlowVersion the new open flow version
     */
    @JsonProperty("openFlowVersion")
    public void setOpenFlowVersion(String openFlowVersion) {
        this.openFlowVersion = openFlowVersion;
    }

    /**
     * Gets the switch dpid.
     *
     * @return the switch dpid
     */
    @JsonProperty("switchDPID")
    public String getSwitchDPID() {
        return switchDPID;
    }

    /**
     * Sets the switch dpid.
     *
     * @param switchDPID the new switch dpid
     */
    @JsonProperty("switchDPID")
    public void setSwitchDPID(String switchDPID) {
        this.switchDPID = switchDPID;
    }


}
