package org.openkilda.messaging.command.discovery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.command.CommandData;

import java.util.List;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiscoveryFilterPopulateData extends CommandData {
    @JsonProperty("filter")
    private final List<DiscoveryFilterEntity> filter;

    @JsonCreator
    public DiscoveryFilterPopulateData(
            @JsonProperty("filter") List<DiscoveryFilterEntity> filter) {
        this.filter = filter;
    }

    public List<DiscoveryFilterEntity> getFilter() {
        return filter;
    }
}
