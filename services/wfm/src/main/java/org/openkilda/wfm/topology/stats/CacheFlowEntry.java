package org.openkilda.wfm.topology.stats;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.io.Serializable;

@Value
@NonFinal
public class CacheFlowEntry implements Serializable {

    @NonNull
    private String flowId;
    @NonNull
    private String destinationSwitch;
}
