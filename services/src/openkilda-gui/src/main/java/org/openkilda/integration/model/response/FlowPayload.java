package org.openkilda.integration.model.response;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"forward", "reverse"})
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowPayload implements Serializable {

    private static final long serialVersionUID = -611895293779307399L;

    @JsonProperty("forward")
    private FlowPathInfoData forward;
    @JsonProperty("reverse")
    private FlowPathInfoData reverse;

    @JsonProperty("forward")
    public FlowPathInfoData getForward() {
        return forward;
    }

    @JsonProperty("forward")
    public void setForward(FlowPathInfoData forward) {
        this.forward = forward;
    }

    @JsonProperty("reverse")
    public FlowPathInfoData getReverse() {
        return reverse;
    }

    @JsonProperty("reverse")
    public void setReverse(FlowPathInfoData reverse) {
        this.reverse = reverse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getForward(), getReverse());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        FlowPayload that = (FlowPayload) object;
        return Objects.equals(getForward(), that.getForward())
                && Objects.equals(getReverse(), that.getReverse());
    }

    @Override
    public String toString() {
        return "FlowPayload [forward=" + forward + ", reverse=" + reverse + "]";
    }

}
