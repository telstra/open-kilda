package org.bitbucket.openkilda.floodlight.message.command.encapsulation;

import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;

import java.util.Arrays;

import static java.util.Collections.singletonList;

/**
 * Created by atopilin on 14/04/2017.
 */
public class ReplaceSchemeOutputCommands extends PushSchemeOutputCommands {
    @Override
    public OFFlowAdd ingressMatchVlanIdFlowMod(int inputPort, int outputPort, int inputVlan, int transitVlan,
                                               int meterId, long cookie) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FlowModUtils.PRIORITY_VERY_HIGH)
                .setMatch(ofFactory.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                        .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(inputVlan))
                        .build())
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().buildMeter().setMeterId(meterId).build(),
                        ofFactory.instructions().applyActions(Arrays.asList(
                                ofFactory.actions().buildSetField()
                                        .setField(ofFactory.oxms().buildVlanVid()
                                                .setValue(OFVlanVidMatch.ofVlan(transitVlan))
                                                .build())
                                        .build(),
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.of(outputPort))
                                        .build()))
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();

    }

    @Override
    public OFFlowAdd egressPushFlowMod(int inputPort, int outputPort, int transitVlan, int outputVlan, long cookie) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FlowModUtils.PRIORITY_VERY_HIGH)
                .setMatch(ofFactory.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                        .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(transitVlan))
                        .build())
                .setInstructions(singletonList(
                        ofFactory.instructions().applyActions(Arrays.asList(
                                ofFactory.actions().buildSetField()
                                        .setField(ofFactory.oxms().buildVlanVid()
                                                .setValue(OFVlanVidMatch.ofVlan(outputVlan))
                                                .build())
                                        .build(),
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.of(outputPort))
                                        .build()))
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();
    }

    @Override
    public OFFlowAdd egressPopFlowMod(int inputPort, int outputPort, int transitVlan, long cookie) {
        return egressNoneFlowMod(inputPort, outputPort, transitVlan, cookie);
    }

    @Override
    public OFFlowAdd egressReplaceFlowMod(int inputPort, int outputPort, int inputVlan, int outputVlan, long cookie) {
        return egressPushFlowMod(inputPort, outputPort, inputVlan, outputVlan, cookie);
    }
}
