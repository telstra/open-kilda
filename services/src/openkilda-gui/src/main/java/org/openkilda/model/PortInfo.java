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

    
    @JsonProperty("assignmenttype")
    private String assignmenttype;

    
    @JsonProperty("interfacetype")
    private String interfacetype;

    
    @JsonProperty("status")
    private String status;

    
    @JsonProperty("crossconnect")
    private String crossconnect;

    
    @JsonProperty("customeruuid")
    private String customeruuid;

    
    @JsonProperty("switch_id")
    private String switchName;

    
    @JsonProperty("port_name")
    private String portName;

    
    @JsonProperty("port_number")
    private String portNumber;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 6234209548424333879L;

    /**
     * Gets the assignmenttype.
     *
     * @return the assignmenttype
     */
    
    public String getAssignmenttype() {
        return assignmenttype;
    }

    /**
     * Sets the assignmenttype.
     *
     * @param assignmenttype the new assignmenttype
     */
    
    public void setAssignmenttype(final String assignmenttype) {
        this.assignmenttype = assignmenttype;
    }

    /**
     * Gets the interfacetype.
     *
     * @return the interfacetype
     */
    
    public String getInterfacetype() {
        return interfacetype;
    }

    /**
     * Sets the interfacetype.
     *
     * @param interfacetype the new interfacetype
     */
    
    public void setInterfacetype(final String interfacetype) {
        this.interfacetype = interfacetype;
    }

    /**
     * Gets the status.
     *
     * @return the status
     */
    
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status.
     *
     * @param status the new status
     */
    
    public void setStatus(final String status) {
        this.status = status;
    }

    /**
     * Gets the crossconnect.
     *
     * @return the crossconnect
     */
    
    public String getCrossconnect() {
        return crossconnect;
    }

    /**
     * Sets the crossconnect.
     *
     * @param crossconnect the new crossconnect
     */
    
    public void setCrossconnect(final String crossconnect) {
        this.crossconnect = crossconnect;
    }

    /**
     * Gets the customeruuid.
     *
     * @return the customeruuid
     */
    
    public String getCustomeruuid() {
        return customeruuid;
    }

    /**
     * Sets the customeruuid.
     *
     * @param customeruuid the new customeruuid
     */
    
    public void setCustomeruuid(final String customeruuid) {
        this.customeruuid = customeruuid;
    }

    /**
     * Gets the switch name.
     *
     * @return the switch name
     */
    
    public String getSwitchName() {
        return switchName;
    }

    /**
     * Sets the switch name.
     *
     * @param switchName the new switch name
     */
    
    public void setSwitchName(final String switchName) {
        this.switchName = switchName;
    }

    /**
     * Gets the port name.
     *
     * @return the port name
     */
    
    public String getPortName() {
        return portName;
    }

    /**
     * Sets the port name.
     *
     * @param portName the new port name
     */
    
    public void setPortName(final String portName) {
        this.portName = portName;
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
