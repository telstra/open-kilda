package org.openkilda.model;

import java.io.Serializable;

/**
 * The Class PortModel.
 * 
 * @author Gaurav Chugh
 */
public class PortModel implements Serializable {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The switch id. */
    private String switchId;

    /** The port name. */
    private String portName;

    /** The status. */
    private String status;

    /**
     * Gets the switch id.
     *
     * @return the switch id
     */
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets the switch id.
     *
     * @param switchId the new switch id
     */
    public void setSwitchId(String switchId) {
        this.switchId = switchId;
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
    public void setPortName(String portName) {
        this.portName = portName;
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
    public void setStatus(String status) {
        this.status = status;
    }

}
