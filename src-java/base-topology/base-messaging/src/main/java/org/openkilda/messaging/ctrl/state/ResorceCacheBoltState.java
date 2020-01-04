package org.openkilda.messaging.ctrl.state;

import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.visitor.DumpStateVisitor;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Map;
import java.util.Set;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResorceCacheBoltState extends AbstractDumpState {

    @JsonProperty("meters")
    Map<SwitchId, Set<Integer>> meters;

    @JsonProperty("vlans")
    Set<Integer> vlans;

    @JsonProperty("cookies")
    Set<Integer> cookies;

    @JsonCreator
    public ResorceCacheBoltState(
            @JsonProperty("meters") Map<SwitchId, Set<Integer>> meters,
            @JsonProperty("vlans") Set<Integer> vlans,
            @JsonProperty("cookies") Set<Integer> cookies) {
        this.meters = meters;
        this.vlans = vlans;
        this.cookies = cookies;
    }


    public void accept(DumpStateVisitor visitor) {
        visitor.visit(this);
    }
}
