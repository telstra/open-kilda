/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.List;

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
public class PortDetail implements Serializable {

    
    @JsonProperty("peer_features")
    private List<Object> peerFeatures = null;

    
    @JsonProperty("current_features")
    private List<String> currentFeatures = null;

    
    @JsonProperty("hardware_address")
    private String hardwareAddress;

    
    @JsonProperty("curr_speed")
    private String currSpeed;

    
    @JsonProperty("port_number")
    private String portNumber;

    
    @JsonProperty("supported_features")
    private List<Object> supportedFeatures = null;

    
    @JsonProperty("name")
    private String name;

    
    @JsonProperty("max_speed")
    private String maxSpeed;

    
    @JsonProperty("state")
    private List<String> state = null;

    
    @JsonProperty("config")
    private List<String> config = null;

    
    @JsonProperty("advertised_features")
    private List<Object> advertisedFeatures = null;

    
    private static final long serialVersionUID = 9111195547914952199L;

    /**
     * Gets the peer features.
     *
     * @return the peer features
     */
    
    public List<Object> getPeerFeatures() {
        return peerFeatures;
    }

    /**
     * Sets the peer features.
     *
     * @param peerFeatures the new peer features
     */
    
    public void setPeerFeatures(final List<Object> peerFeatures) {
        this.peerFeatures = peerFeatures;
    }

    /**
     * Gets the current features.
     *
     * @return the current features
     */
    
    public List<String> getCurrentFeatures() {
        return currentFeatures;
    }

    /**
     * Sets the current features.
     *
     * @param currentFeatures the new current features
     */
    
    public void setCurrentFeatures(final List<String> currentFeatures) {
        this.currentFeatures = currentFeatures;
    }

    /**
     * Gets the hardware address.
     *
     * @return the hardware address
     */
    
    public String getHardwareAddress() {
        return hardwareAddress;
    }

    /**
     * Sets the hardware address.
     *
     * @param hardwareAddress the new hardware address
     */
    
    public void setHardwareAddress(final String hardwareAddress) {
        this.hardwareAddress = hardwareAddress;
    }

    /**
     * Gets the curr speed.
     *
     * @return the curr speed
     */
    
    public String getCurrSpeed() {
        return currSpeed;
    }

    /**
     * Sets the curr speed.
     *
     * @param currSpeed the new curr speed
     */
    
    public void setCurrSpeed(final String currSpeed) {
        this.currSpeed = currSpeed;
    }

    /**
     * Gets the port number.
     *
     * @return the port number
     */
    
    public String getPortNumber() {
        return portNumber;
    }

    /**
     * Sets the port number.
     *
     * @param portNumber the new port number
     */
    
    public void setPortNumber(final String portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * Gets the supported features.
     *
     * @return the supported features
     */
    
    public List<Object> getSupportedFeatures() {
        return supportedFeatures;
    }

    /**
     * Sets the supported features.
     *
     * @param supportedFeatures the new supported features
     */
    
    public void setSupportedFeatures(final List<Object> supportedFeatures) {
        this.supportedFeatures = supportedFeatures;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the new name
     */
    
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Gets the max speed.
     *
     * @return the max speed
     */
    
    public String getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * Sets the max speed.
     *
     * @param maxSpeed the new max speed
     */
    
    public void setMaxSpeed(final String maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    /**
     * Gets the state.
     *
     * @return the state
     */
    
    public List<String> getState() {
        return state;
    }

    /**
     * Sets the state.
     *
     * @param state the new state
     */
    
    public void setState(final List<String> state) {
        this.state = state;
    }

    /**
     * Gets the config.
     *
     * @return the config
     */
    
    public List<String> getConfig() {
        return config;
    }

    /**
     * Sets the config.
     *
     * @param config the new config
     */
    
    public void setConfig(final List<String> config) {
        this.config = config;
    }

    /**
     * Gets the advertised features.
     *
     * @return the advertised features
     */
    
    public List<Object> getAdvertisedFeatures() {
        return advertisedFeatures;
    }

    /**
     * Sets the advertised features.
     *
     * @param advertisedFeatures the new advertised features
     */
    
    public void setAdvertisedFeatures(final List<Object> advertisedFeatures) {
        this.advertisedFeatures = advertisedFeatures;
    }

}
