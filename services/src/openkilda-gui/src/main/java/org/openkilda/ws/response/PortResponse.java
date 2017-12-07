package org.openkilda.ws.response;

import java.io.Serializable;
import java.util.List;

import org.openkilda.model.PortSwitch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class PortResponse.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"switches"})
public class PortResponse implements Serializable {

    /** The switches. */
    @JsonProperty("switches")
    private List<PortSwitch> switches = null;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = 651743230782845020L;

    /**
     * Gets the switches.
     *
     * @return the switches
     */
    @JsonProperty("switches")
    public List<PortSwitch> getSwitches() {
        return switches;
    }

    /**
     * Sets the switches.
     *
     * @param switches the new switches
     */
    @JsonProperty("switches")
    public void setSwitches(List<PortSwitch> switches) {
        this.switches = switches;
    }

}
