package org.openkilda.integration.model.response;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class PortDesc.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"peer_features", "current_features", "hardware_address", "curr_speed",
        "port_number", "supported_features", "name", "max_speed", "state", "config",
        "advertised_features"})
public class PortDesc implements Serializable {

    /** The peer features. */
    @JsonProperty("peer_features")
    private List<Object> peerFeatures = null;

    /** The current features. */
    @JsonProperty("current_features")
    private List<String> currentFeatures = null;

    /** The hardware address. */
    @JsonProperty("hardware_address")
    private String hardwareAddress;

    /** The curr speed. */
    @JsonProperty("curr_speed")
    private String currSpeed;

    /** The port number. */
    @JsonProperty("port_number")
    private String portNumber;

    /** The supported features. */
    @JsonProperty("supported_features")
    private List<Object> supportedFeatures = null;

    /** The name. */
    @JsonProperty("name")
    private String name;

    /** The max speed. */
    @JsonProperty("max_speed")
    private String maxSpeed;

    /** The state. */
    @JsonProperty("state")
    private List<String> state = null;

    /** The config. */
    @JsonProperty("config")
    private List<String> config = null;

    /** The advertised features. */
    @JsonProperty("advertised_features")
    private List<Object> advertisedFeatures = null;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 9111195547914952199L;

    /**
     * Gets the peer features.
     *
     * @return the peer features
     */
    @JsonProperty("peer_features")
    public List<Object> getPeerFeatures() {
        return peerFeatures;
    }

    /**
     * Sets the peer features.
     *
     * @param peerFeatures the new peer features
     */
    @JsonProperty("peer_features")
    public void setPeerFeatures(List<Object> peerFeatures) {
        this.peerFeatures = peerFeatures;
    }

    /**
     * Gets the current features.
     *
     * @return the current features
     */
    @JsonProperty("current_features")
    public List<String> getCurrentFeatures() {
        return currentFeatures;
    }

    /**
     * Sets the current features.
     *
     * @param currentFeatures the new current features
     */
    @JsonProperty("current_features")
    public void setCurrentFeatures(List<String> currentFeatures) {
        this.currentFeatures = currentFeatures;
    }

    /**
     * Gets the hardware address.
     *
     * @return the hardware address
     */
    @JsonProperty("hardware_address")
    public String getHardwareAddress() {
        return hardwareAddress;
    }

    /**
     * Sets the hardware address.
     *
     * @param hardwareAddress the new hardware address
     */
    @JsonProperty("hardware_address")
    public void setHardwareAddress(String hardwareAddress) {
        this.hardwareAddress = hardwareAddress;
    }

    /**
     * Gets the curr speed.
     *
     * @return the curr speed
     */
    @JsonProperty("curr_speed")
    public String getCurrSpeed() {
        return currSpeed;
    }

    /**
     * Sets the curr speed.
     *
     * @param currSpeed the new curr speed
     */
    @JsonProperty("curr_speed")
    public void setCurrSpeed(String currSpeed) {
        this.currSpeed = currSpeed;
    }

    /**
     * Gets the port number.
     *
     * @return the port number
     */
    @JsonProperty("port_number")
    public String getPortNumber() {
        return portNumber;
    }

    /**
     * Sets the port number.
     *
     * @param portNumber the new port number
     */
    @JsonProperty("port_number")
    public void setPortNumber(String portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * Gets the supported features.
     *
     * @return the supported features
     */
    @JsonProperty("supported_features")
    public List<Object> getSupportedFeatures() {
        return supportedFeatures;
    }

    /**
     * Sets the supported features.
     *
     * @param supportedFeatures the new supported features
     */
    @JsonProperty("supported_features")
    public void setSupportedFeatures(List<Object> supportedFeatures) {
        this.supportedFeatures = supportedFeatures;
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
     * Gets the max speed.
     *
     * @return the max speed
     */
    @JsonProperty("max_speed")
    public String getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * Sets the max speed.
     *
     * @param maxSpeed the new max speed
     */
    @JsonProperty("max_speed")
    public void setMaxSpeed(String maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    /**
     * Gets the state.
     *
     * @return the state
     */
    @JsonProperty("state")
    public List<String> getState() {
        return state;
    }

    /**
     * Sets the state.
     *
     * @param state the new state
     */
    @JsonProperty("state")
    public void setState(List<String> state) {
        this.state = state;
    }

    /**
     * Gets the config.
     *
     * @return the config
     */
    @JsonProperty("config")
    public List<String> getConfig() {
        return config;
    }

    /**
     * Sets the config.
     *
     * @param config the new config
     */
    @JsonProperty("config")
    public void setConfig(List<String> config) {
        this.config = config;
    }

    /**
     * Gets the advertised features.
     *
     * @return the advertised features
     */
    @JsonProperty("advertised_features")
    public List<Object> getAdvertisedFeatures() {
        return advertisedFeatures;
    }

    /**
     * Sets the advertised features.
     *
     * @param advertisedFeatures the new advertised features
     */
    @JsonProperty("advertised_features")
    public void setAdvertisedFeatures(List<Object> advertisedFeatures) {
        this.advertisedFeatures = advertisedFeatures;
    }

}
