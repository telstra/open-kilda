package org.openkilda.messaging.payload.flow;


import java.io.Serializable;
import java.util.Objects;
import static org.openkilda.messaging.Utils.FLOW_ID;
import org.openkilda.messaging.error.ErrorData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "flowid", "cookie", "forward_ingress_flow",
		"forward_egress_flow", "reverse_ingress_flow", "reverse_egress_flow" })
public class FlowStats extends ErrorData implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	@JsonProperty("flowid")
	private String flowId;
	@JsonProperty("cookie")
	private Long cookie;
	@JsonProperty("forward_ingress_flow")
	private ForwardIngressFlow forwardIngressFlow;
	@JsonProperty("forward_egress_flow")
	private ForwardEgressFlow forwardEgressFlow;
	@JsonProperty("reverse_ingress_flow")
	private ReverseIngressFlow reverseIngressFlow;
	@JsonProperty("reverse_egress_flow")
	private ReverseEgressFlow reverseEgressFlow;

	public FlowStats(){
		
	}
	
	 /**
     * Instance constructor.
     *
     * @param id   flow id
     * @param path flow cookie
     * @param path flow Forward Ingress
     * @param path flow Forward Egress
     * @param path flow Reverse Ingress
     * @param path flow Reverse Egress
     * @throws IllegalArgumentException if flow id or flow stats is null or empty
     */
   // @JsonCreator
    public FlowStats(final String id,
                            final ForwardIngressFlow forwardIngressFlow,
                            final ForwardEgressFlow forwardEgressFlow,
                            final ReverseIngressFlow reverseIngressFlow,
                            final ReverseEgressFlow reverseEgressFlow) {
        setFlowId(id);
        setForwardEgressFlow(forwardEgressFlow);
        setForwardIngressFlow(forwardIngressFlow);
        setReverseEgressFlow(reverseEgressFlow);
        setReverseIngressFlow(reverseIngressFlow);
    }
    
    /**
     * Instance constructor.
     *
     * @param id   flow id
     * @param path flow cookie
     * @throws IllegalArgumentException if flow id or flow stats is null or empty
     */
    @JsonCreator
    public FlowStats(@JsonProperty(FLOW_ID) final String id,@JsonProperty("cookie") Long cookie ) {
        setFlowId(id);
        setCookie(cookie);
    }
    
	@JsonProperty("flowid")
	public String getFlowId() {
		return flowId;
	}

	@JsonProperty("flowid")
	public void setFlowId(String flowId) {
		this.flowId = flowId;
	}

	@JsonProperty("cookie")
	public Long getCookie() {
		return cookie;
	}

	@JsonProperty("cookie")
	public void setCookie(Long cookie) {
		this.cookie = cookie;
	}

	@JsonProperty("forward_ingress_flow")
	public ForwardIngressFlow getForwardIngressFlow() {
		return forwardIngressFlow;
	}

	@JsonProperty("forward_ingress_flow")
	public void setForwardIngressFlow(ForwardIngressFlow forwardIngressFlow) {
		this.forwardIngressFlow = forwardIngressFlow;
	}

	@JsonProperty("forward_egress_flow")
	public ForwardEgressFlow getForwardEgressFlow() {
		return forwardEgressFlow;
	}

	@JsonProperty("forward_egress_flow")
	public void setForwardEgressFlow(ForwardEgressFlow forwardEgressFlow) {
		this.forwardEgressFlow = forwardEgressFlow;
	}

	@JsonProperty("reverse_ingress_flow")
	public ReverseIngressFlow getReverseIngressFlow() {
		return reverseIngressFlow;
	}

	@JsonProperty("reverse_ingress_flow")
	public void setReverseIngressFlow(ReverseIngressFlow reverseIngressFlow) {
		this.reverseIngressFlow = reverseIngressFlow;
	}

	@JsonProperty("reverse_egress_flow")
	public ReverseEgressFlow getReverseEgressFlow() {
		return reverseEgressFlow;
	}

	@JsonProperty("reverse_egress_flow")
	public void setReverseEgressFlow(ReverseEgressFlow reverseEgressFlow) {
		this.reverseEgressFlow = reverseEgressFlow;
	}

	@Override
	public String toString() {
		return "FlowStats [flowId=" + flowId + ", cookie=" + cookie
				+ ", forwardIngressFlow=" + forwardIngressFlow
				+ ", forwardEgressFlow=" + forwardEgressFlow
				+ ", reverseIngressFlow=" + reverseIngressFlow
				+ ", reverseEgressFlow=" + reverseEgressFlow + "]";
	}
	/* *//**
     * {@inheritDoc}
     *//*
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || !(obj instanceof FlowStats)) {
            return false;
        }

        FlowStats that = (FlowStats) obj;
        return Objects.equals(getFlowId(), that.getFlowId())
                && Objects.equals(getForwardEgressFlow().getCookieValue(), that.getForwardEgressFlow().getCookieValue())
                && Objects.equals(getForwardEgressFlow(), that.getForwardEgressFlow())
                && Objects.equals(getForwardIngressFlow(), that.getForwardEgressFlow())
                && Objects.equals(getReverseEgressFlow(), that.getReverseEgressFlow())
                && Objects.equals(getReverseIngressFlow(), that.getReverseIngressFlow());
    }*/

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(flowId, cookie, forwardEgressFlow, forwardIngressFlow, reverseEgressFlow,
                reverseIngressFlow);
    }

}
