package org.openkilda.integration.model.response;

import java.io.Serializable;
import java.util.List;

import org.openkilda.model.response.PortInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class SwitchInfo.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"controller", "hostname", "address", "name", "description", "state"})
public class SwitchInfo implements Serializable {

    /** The controller. */
    @JsonProperty("controller")
    private String controller;

    /** The hostname. */
    @JsonProperty("hostname")
    private String hostname;

    /** The address. */
    @JsonProperty("address")
    private String address;

    /** The name. */
    @JsonProperty("name")
    private String name;

    /** The description. */
    @JsonProperty("description")
    private String description;

    /** The state. */
    @JsonProperty("state")
    private String state;

    /** The port info. */
    @JsonProperty("ports")
    List<PortInfo> portInfo;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 6763064864461521069L;

    /**
     * Gets the controller.
     *
     * @return the controller
     */
    @JsonProperty("controller")
    public String getController() {
        return controller;
    }

    /**
     * Sets the controller.
     *
     * @param controller the new controller
     */
    @JsonProperty("controller")
    public void setController(String controller) {
        this.controller = controller;
    }

    /**
     * Gets the hostname.
     *
     * @return the hostname
     */
    @JsonProperty("hostname")
    public String getHostname() {
        return hostname;
    }

    /**
     * Sets the hostname.
     *
     * @param hostname the new hostname
     */
    @JsonProperty("hostname")
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * Gets the address.
     *
     * @return the address
     */
    @JsonProperty("address")
    public String getAddress() {
        return address;
    }

    /**
     * Sets the address.
     *
     * @param address the new address
     */
    @JsonProperty("address")
    public void setAddress(String address) {
        this.address = address;
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
     * Gets the description.
     *
     * @return the description
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the new description
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the state.
     *
     * @return the state
     */
    @JsonProperty("state")
    public String getState() {
        return state;
    }

    /**
     * Sets the state.
     *
     * @param state the new state
     */
    @JsonProperty("state")
    public void setState(String state) {
        this.state = state;
    }

    /**
     * Gets the port info.
     *
     * @return the port info
     */
    @JsonProperty("ports")
    public List<PortInfo> getPortInfo() {
        return portInfo;
    }

    /**
     * Sets the port info.
     *
     * @param portInfo the new port info
     */
    @JsonProperty("ports")
    public void setPortInfo(List<PortInfo> portInfo) {
        this.portInfo = portInfo;
    }


}
