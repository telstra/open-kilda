package org.openkilda.grpc.speaker.model;

import org.openkilda.messaging.model.grpc.OnOffState;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EnableLogMessagesResponse {
    @JsonProperty("enabled")
    private OnOffState enabled;
}
