package org.openkilda.messaging.ctrl.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;

import java.io.Serializable;
import java.util.Set;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkDump implements Serializable {
    @JsonProperty("switches")
    private Set<SwitchInfoData> switches;

    @JsonProperty("isls")
    private Set<IslInfoData> isls;

    @JsonCreator
    public NetworkDump(
            @JsonProperty("switches") Set<SwitchInfoData> switches,
            @JsonProperty("isls") Set<IslInfoData> isls) {
        this.switches = switches;
        this.isls = isls;
    }


    public Set<SwitchInfoData> getSwitches() {
        return switches;
    }

    public Set<IslInfoData> getIsls() {
        return isls;
    }
}
