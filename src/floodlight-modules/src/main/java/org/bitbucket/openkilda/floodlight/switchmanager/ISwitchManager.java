package org.bitbucket.openkilda.floodlight.switchmanager;

import org.bitbucket.openkilda.messaging.payload.flow.OutputVlanType;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.types.DatapathId;

/**
 * Created by jonv on 29/3/17.
 */
public interface ISwitchManager extends IFloodlightService {
    /** OVS software switch manufacturer constant value. */
    String OVS_MANUFACTURER = "Nicira, Inc.";

    /**
     * Adds default rules to install verification rules and final drop rule.
     *
     * @param dpid datapathId of switch
     * @return true if the command was accepted to be sent to switch, false otherwise - switch is disconnected or in
     * SLAVE mode
     */
    boolean installDefaultRules(final DatapathId dpid);

    /**
     * Installs an flow on ingress switch.
     *
     * @param dpid          datapathId of the switch
     * @param flowId        flow id
     * @param inputPort     port to expect the packet on
     * @param outputPort    port to forward the packet out
     * @param inputVlanId   input vlan to match on, 0 means not to match on vlan
     * @param transitVlanId vlan to add before outputing on outputPort
     * @return {@link ImmutablePair}<Long, Boolean>, where key is OF transaction id and value is true if the command was
     * accepted to be sent to switch, false otherwise - switch is disconnected or in SLAVE mode
     */
    ImmutablePair<Long, Boolean> installIngressFlow(final DatapathId dpid, final String flowId, final Long cookie,
                                                    final int inputPort, final int outputPort, final int inputVlanId,
                                                    final int transitVlanId, final OutputVlanType outputVlanType,
                                                    final long meterId);

    /**
     * Installs flow on egress swtich.
     *
     * @param dpid           datapathId of the switch
     * @param flowId         flow id
     * @param inputPort      port to expect the packet on
     * @param outputPort     port to forward the packet out
     * @param transitVlanId  vlan to match on the ingressPort
     * @param outputVlanId   set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @return {@link ImmutablePair}<Long, Boolean>, where key is OF transaction id and value is true if the command was
     * accepted to be sent to switch, false otherwise - switch is disconnected or in SLAVE mode
     */
    ImmutablePair<Long, Boolean> installEgressFlow(final DatapathId dpid, final String flowId, final Long cookie,
                                                   final int inputPort, final int outputPort, final int transitVlanId,
                                                   final int outputVlanId, final OutputVlanType outputVlanType);

    /**
     * Installs flow on a transit switch.
     *
     * @param dpid          datapathId of the switch
     * @param flowId        flow id
     * @param inputPort     port to expect packet on
     * @param outputPort    port to forward packet out
     * @param transitVlanId vlan to match on inputPort
     * @return {@link ImmutablePair}<Long, Boolean>, where key is OF transaction id and value is true if the command was
     * accepted to be sent to switch, false otherwise - switch is disconnected or in SLAVE mode
     */
    ImmutablePair<Long, Boolean> installTransitFlow(final DatapathId dpid, final String flowId, final Long cookie,
                                                    final int inputPort, final int outputPort, final int transitVlanId);

    /**
     * Installs flow through one switch.
     *
     * @param dpid           datapathId of the switch
     * @param flowId         flow id
     * @param inputPort      port to expect packet on
     * @param outputPort     port to forward packet out
     * @param inputVlanId    vlan to match on inputPort
     * @param outputVlanId   set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @return {@link ImmutablePair}<Long, Boolean>, where key is OF transaction id and value is true if the command was
     * accepted to be sent to switch, false otherwise - switch is disconnected or in SLAVE mode
     */
    ImmutablePair<Long, Boolean> installOneSwitchFlow(final DatapathId dpid, final String flowId, final Long cookie,
                                                      final int inputPort, final int outputPort, int inputVlanId,
                                                      int outputVlanId, final OutputVlanType outputVlanType,
                                                      final long meterId);

    /**
     * Returns list of installed flows
     *
     * @param dpid switch id
     * @return OF flow stats entries
     */
    OFFlowStatsReply dumpFlowTable(final DatapathId dpid);

    /**
     * Returns list of installed meters
     *
     * @param dpid switch id
     * @return OF meter config stats entries
     */
    OFMeterConfigStatsReply dumpMeters(final DatapathId dpid);

    /**
     * Installs a meter on ingress switch OF_13.
     * TODO: describe params meaning in accordance with OF
     *
     * @param dpid      datapath ID of the switch
     * @param bandwidth the bandwidth limit value
     * @param burstSize the size of the burst
     * @param meterId   the meter ID
     * @return {@link ImmutablePair}<Long, Boolean>, where key is OF transaction id and value is true if the command was
     * accepted to be sent to switch, false otherwise - switch is disconnected or in SLAVE mode
     */
    ImmutablePair<Long, Boolean> installMeter(final DatapathId dpid, final long bandwidth, final long burstSize,
                                              final long meterId);

    /**
     * Installs a meter on ingress switch OF_12.
     * TODO: describe params meaning in accordance with OF
     *
     * @param dpid      datapath ID of the switch
     * @param bandwidth the bandwidth limit value
     * @param burstSize the size of the burst
     * @param meterId   the meter ID
     * @return {@link ImmutablePair}<Long, Boolean>, where key is OF transaction id and value is true if the command was
     * accepted to be sent to switch, false otherwise - switch is disconnected or in SLAVE mode
     */
    ImmutablePair<Long, Boolean> installLegacyMeter(final DatapathId dpid, final long bandwidth, final long burstSize,
                                                    final long meterId);

    /**
     * Deletes the flow from the switch
     *
     * @param dpid   datapath ID of the switch
     * @param flowId flow id
     * @return {@link ImmutablePair}<Long, Boolean>, where key is OF transaction id and value is true if the command was
     * accepted to be sent to switch, false otherwise - switch is disconnected or in SLAVE mode
     */
    ImmutablePair<Long, Boolean> deleteFlow(final DatapathId dpid, final String flowId, final Long cookie);

    /**
     * Deletes the meter from the switch OF_13.
     *
     * @param dpid    datapath ID of the switch
     * @param meterId meter identifier
     * @return {@link ImmutablePair}<Long, Boolean>, where key is OF transaction id and value is true if the command was
     * accepted to be sent to switch, false otherwise - switch is disconnected or in SLAVE mode
     */
    ImmutablePair<Long, Boolean> deleteMeter(final DatapathId dpid, final long meterId);

    /**
     * Deletes the meter from the switch OF_12.
     *
     * @param dpid    datapath ID of the switch
     * @param meterId meter identifier
     * @return {@link ImmutablePair}<Long, Boolean>, where key is OF transaction id and value is true if the command was
     * accepted to be sent to switch, false otherwise - switch is disconnected or in SLAVE mode
     */
    ImmutablePair<Long, Boolean> deleteLegacyMeter(final DatapathId dpid, final long meterId);
}
