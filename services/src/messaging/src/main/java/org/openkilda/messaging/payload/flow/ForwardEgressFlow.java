package org.openkilda.messaging.payload.flow;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "switch_id", "cookie_value", "pkt_count", "byte_count" })
public class ForwardEgressFlow implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	@JsonProperty("switch_id")
	private String switchId;
	@JsonProperty("cookie_value")
	private String cookieValue;
	@JsonProperty("tx_pkt_count")
	private String txPktCount;
	@JsonProperty("rx_pkt_count")
	private String rxPktCount;
	@JsonProperty("tx_byte_count")
	private String txByteCount;
	@JsonProperty("rx_byte_count")
	private String rxByteCount;

	public ForwardEgressFlow() {

	}

	public ForwardEgressFlow(String switchId, String cookie, String txPktCount,
			String rxPktCount, String txByteCount, String rxByteCount) {
		this.switchId = switchId;
		this.cookieValue = cookie;
		this.txPktCount = txPktCount;
		this.rxPktCount = rxPktCount;
		this.txByteCount = txByteCount;
		this.rxByteCount = rxByteCount;

	}

	@JsonProperty("switch_id")
	public String getSwitchId() {
		return switchId;
	}

	@JsonProperty("switch_id")
	public void setSwitchId(String switchId) {
		this.switchId = switchId;
	}

	@JsonProperty("cookie_value")
	public String getCookieValue() {
		return cookieValue;
	}

	@JsonProperty("cookie_value")
	public void setCookieValue(String cookieValue) {
		this.cookieValue = cookieValue;
	}

	public String getTxPktCount() {
		return txPktCount;
	}

	public void setTxPktCount(String txPktCount) {
		this.txPktCount = txPktCount;
	}

	public String getRxPktCount() {
		return rxPktCount;
	}

	public void setRxPktCount(String rxPktCount) {
		this.rxPktCount = rxPktCount;
	}

	public String getTxByteCount() {
		return txByteCount;
	}

	public void setTxByteCount(String txByteCount) {
		this.txByteCount = txByteCount;
	}

	public String getRxByteCount() {
		return rxByteCount;
	}

	public void setRxByteCount(String rxByteCount) {
		this.rxByteCount = rxByteCount;
	}

	@Override
	public String toString() {
		return "ForwardEgressFlow [switchId=" + switchId + ", cookieValue="
				+ cookieValue + ", txPktCount=" + txPktCount + ", rxPktCount="
				+ rxPktCount + ", txByteCount=" + txByteCount
				+ ", rxByteCount=" + rxByteCount + "]";
	}

}
