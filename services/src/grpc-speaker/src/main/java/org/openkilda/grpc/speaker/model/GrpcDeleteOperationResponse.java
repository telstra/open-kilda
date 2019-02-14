package org.openkilda.grpc.speaker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GrpcDeleteOperationResponse {
    @JsonProperty("deleted")
    private Boolean deleted;
}
