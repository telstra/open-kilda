package org.openkilda.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class Port.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"interfacetype", "status", "port_name", "port_number"})
public class Port implements Serializable, Comparable<Port> {

    /** The interfacetype. */
    @JsonProperty("interfacetype")
    private String interfacetype;

    /** The status. */
    @JsonProperty("status")
    private String status;

    /** The port name. */
    @JsonProperty("port_name")
    private String portName;

    /** The port number. */
    @JsonProperty("port_number")
    private String portNumber;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 1709637893078199057L;

    /**
     * Gets the interfacetype.
     *
     * @return the interfacetype
     */
    @JsonProperty("interfacetype")
    public String getInterfacetype() {
        return interfacetype;
    }

    /**
     * Sets the interfacetype.
     *
     * @param interfacetype the new interfacetype
     */
    @JsonProperty("interfacetype")
    public void setInterfacetype(String interfacetype) {
        this.interfacetype = interfacetype;
    }

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
     * Gets the port name.
     *
     * @return the port name
     */
    @JsonProperty("port_name")
    public String getPortName() {
        return portName;
    }

    /**
     * Sets the port name.
     *
     * @param portName the new port name
     */
    @JsonProperty("port_name")
    public void setPortName(String portName) {
        this.portName = portName;
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

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(Port port) {
        Integer port1 = Integer.parseInt(this.portNumber);
        Integer port2 = Integer.parseInt(port.getPortNumber());
        return port1 - port2;
    }

}
