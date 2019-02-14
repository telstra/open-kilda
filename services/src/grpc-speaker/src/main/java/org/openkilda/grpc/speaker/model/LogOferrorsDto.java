package org.openkilda.grpc.speaker.model;

import org.openkilda.messaging.model.grpc.OnOffState;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
public class LogOferrorsDto {
    @NonNull
    private OnOffState state;
}
