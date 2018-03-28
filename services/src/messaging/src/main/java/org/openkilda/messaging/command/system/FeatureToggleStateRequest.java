package org.openkilda.messaging.command.system;

import lombok.Builder;
import lombok.Value;
import org.openkilda.messaging.command.CommandData;

@Value
@Builder
public class FeatureToggleStateRequest extends CommandData {

    public FeatureToggleStateRequest() {
    }
}
