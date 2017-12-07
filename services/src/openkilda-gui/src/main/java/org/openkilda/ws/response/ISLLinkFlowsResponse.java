package org.openkilda.ws.response;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class ISLLinkFlowsResponse.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "switchrelation" })
public class ISLLinkFlowsResponse implements Serializable {

	/** The switchrelation. */
	@JsonProperty("switchrelation")
	private List<Switchrelation> switchrelation = null;

	/** The Constant serialVersionUID. */
	private final static long serialVersionUID = 3929984352486285689L;

	/**
	 * Gets the switchrelation.
	 *
	 * @return the switchrelation
	 */
	@JsonProperty("switchrelation")
	public List<Switchrelation> getSwitchrelation() {
		return switchrelation;
	}

	/**
	 * Sets the switchrelation.
	 *
	 * @param switchrelation
	 *            the new switchrelation
	 */
	@JsonProperty("switchrelation")
	public void setSwitchrelation(List<Switchrelation> switchrelation) {
		this.switchrelation = switchrelation;
	}

}
