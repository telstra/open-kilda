package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/**
 * The Class PortInfo.
 *
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"assignmenttype", "interfacetype", "status", "crossconnect", "customeruuid",
        "switch_id", "port_name"})
public class PortInfo implements Serializable, Comparable<PortInfo> {

    /** The assignmenttype. */
    @JsonProperty("assignmenttype")
    private String assignmenttype;

    /** The interfacetype. */
    @JsonProperty("interfacetype")
    private String interfacetype;

    /** The status. */
    @JsonProperty("status")
    private String status;

    /** The crossconnect. */
    @JsonProperty("crossconnect")
    private String crossconnect;

    /** The customeruuid. */
    @JsonProperty("customeruuid")
    private String customeruuid;

    /** The switch name. */
    @JsonProperty("switch_id")
    private String switchName;

    /** The port name. */
    @JsonProperty("port_name")
    private String portName;

    /** The port number. */
    @JsonProperty("port_number")
    private String portNumber;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 6234209548424333879L;

    /**
     * Gets the assignmenttype.
     *
     * @return the assignmenttype
     */
    @JsonProperty("assignmenttype")
    public String getAssignmenttype() {
        return assignmenttype;
    }

    /**
     * Sets the assignmenttype.
     *
     * @param assignmenttype the new assignmenttype
     */
    @JsonProperty("assignmenttype")
    public void setAssignmenttype(final String assignmenttype) {
        this.assignmenttype = assignmenttype;
    }

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
    public void setInterfacetype(final String interfacetype) {
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
    public void setStatus(final String status) {
        this.status = status;
    }

    /**
     * Gets the crossconnect.
     *
     * @return the crossconnect
     */
    @JsonProperty("crossconnect")
    public String getCrossconnect() {
        return crossconnect;
    }

    /**
     * Sets the crossconnect.
     *
     * @param crossconnect the new crossconnect
     */
    @JsonProperty("crossconnect")
    public void setCrossconnect(final String crossconnect) {
        this.crossconnect = crossconnect;
    }

    /**
     * Gets the customeruuid.
     *
     * @return the customeruuid
     */
    @JsonProperty("customeruuid")
    public String getCustomeruuid() {
        return customeruuid;
    }

    /**
     * Sets the customeruuid.
     *
     * @param customeruuid the new customeruuid
     */
    @JsonProperty("customeruuid")
    public void setCustomeruuid(final String customeruuid) {
        this.customeruuid = customeruuid;
    }

    /**
     * Gets the switch name.
     *
     * @return the switch name
     */
    @JsonProperty("switch_id")
    public String getSwitchName() {
        return switchName;
    }

    /**
     * Sets the switch name.
     *
     * @param switchName the new switch name
     */
    @JsonProperty("switch_id")
    public void setSwitchName(final String switchName) {
        this.switchName = switchName;
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
    public void setPortName(final String portName) {
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
    public void setPortNumber(final String portNumber) {
        this.portNumber = portNumber;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final PortInfo port) {
        Integer portNumber1 = Integer.parseInt(portNumber);
        Integer portNumber2 = Integer.parseInt(port.portNumber);
        return portNumber1 - portNumber2;
    }

}
