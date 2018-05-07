package org.openkilda.atdd.staging.service.traffexam.model;

import org.openkilda.messaging.payload.flow.FlowPayload;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class FlowBidirectionalExam {
    private final FlowPayload flow;
    private final Exam forward;
    private final Exam reverse;

    public FlowBidirectionalExam(FlowPayload flow, Host source, Host dest, int bandwidth) {
        this.flow = flow;

        // burst value is hardcoded into floddlight-modules as 1000 kbit/sec, so to overcome this burst we need at least
        // 1024 * 1024 / 8 / 1500 = 87.3...

        forward = new Exam(source, dest)
                .withSourceVlan(new Vlan(flow.getSource().getVlanId()))
                .withDestVlan(new Vlan(flow.getDestination().getVlanId()))
                .withBandwidthLimit(new Bandwidth(bandwidth))
                .withBurstPkt(100);
        reverse = new Exam(dest, source)
                .withSourceVlan(new Vlan(flow.getDestination().getVlanId()))
                .withDestVlan(new Vlan(flow.getSource().getVlanId()))
                .withBandwidthLimit(new Bandwidth(bandwidth))
                .withBurstPkt(100);
    }

    public FlowPayload getFlow() {
        return flow;
    }

    public List<Exam> getExamPair() {
        return ImmutableList.of(forward, reverse);
    }

    public Exam getForward() {
        return forward;
    }

    public Exam getReverse() {
        return reverse;
    }
}
