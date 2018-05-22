package org.openkilda.atdd.staging.service.traffexam.model;

import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.openkilda.messaging.payload.flow.FlowPayload;

@Value
@Builder
public class Exam {

    private FlowPayload flow;

    private Host source;
    private Host dest;
    private Vlan sourceVlan;
    private Vlan destVlan;
    private Bandwidth bandwidthLimit;
    private int burstPkt;
    private TimeLimit timeLimitSeconds;

    @Setter
    @NonFinal
    private ExamResources resources;
}
