package org.openkilda.model;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class PortSwitch.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"interface", "connected", "name", "dpid"})
public class PortSwitch implements Serializable {

    /** The _interface. */
    @JsonProperty("interface")
    private List<PortInterface> _interface = null;

    /** The connected. */
    @JsonProperty("connected")
    private boolean connected;

    /** The name. */
    @JsonProperty("name")
    private String name;

    /** The dpid. */
    @JsonProperty("dpid")
    private String dpid;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 4107274108964223204L;

    /**
     * Gets the interface.
     *
     * @return the interface
     */
    @JsonProperty("interface")
    public List<PortInterface> getInterface() {
        return _interface;
    }

    /**
     * Sets the interface.
     *
     * @param _interface the new interface
     */
    @JsonProperty("interface")
    public void setInterface(List<PortInterface> _interface) {
        this._interface = _interface;
    }

    /**
     * Checks if is connected.
     *
     * @return true, if is connected
     */
    @JsonProperty("connected")
    public boolean isConnected() {
        return connected;
    }

    /**
     * Sets the connected.
     *
     * @param connected the new connected
     */
    @JsonProperty("connected")
    public void setConnected(boolean connected) {
        this.connected = connected;
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

    /**
     * Gets the dpid.
     *
     * @return the dpid
     */
    @JsonProperty("dpid")
    public String getDpid() {
        return dpid;
    }

    /**
     * Sets the dpid.
     *
     * @param dpid the new dpid
     */
    @JsonProperty("dpid")
    public void setDpid(String dpid) {
        this.dpid = dpid;
    }

}
