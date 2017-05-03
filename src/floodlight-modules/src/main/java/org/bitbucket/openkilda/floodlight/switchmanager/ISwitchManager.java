package org.bitbucket.openkilda.floodlight.switchmanager;

import com.google.common.util.concurrent.ListenableFuture;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;

/**
 * Created by jonv on 29/3/17.
 */
public interface ISwitchManager extends IFloodlightService {
    /** OVS software switch manufacturer constant value. */
    String OVS_MANUFACTURER = "Nicira, Inc.";

    /**
     * installDefaultRules - Adds default rules to install verification rules and final drop rule.
     *
     * @param dpid - datapathId of switch
     */
    boolean installDefaultRules(DatapathId dpid);

    /**
     * installIngressFlow - Installs an flow on ingress switch.
     *
     * @param dpid - datapathId of the switch
     * @param cookie - flow cookie
     * @param inputPort - port to expect the packet on
     * @param outputPort - port to forward the packet out
     * @param inputVlanId - input vlan to match on, 0 means not to match on vlan
     * @param transitVlanId - vlan to add before outputing on outputPort
     */
    boolean installIngressFlow(DatapathId dpid, String cookie, int inputPort, int outputPort, int inputVlanId,
                            int transitVlanId, OutputVlanType outputVlanType, long meterid);

    /**
     * installEgressFlow - Install flow on egress swtich.
     *
     * @param dpid - datapathId of the switch
     * @param cookie - flow cookie
     * @param inputPort - port to expect the packet on
     * @param outputPort - port to forward the packet out
     * @param transitVlanId - vlan to match on the ingressPort
     * @param outputVlanId - set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType - type of action to apply to the outputVlanId if greater than 0
     */
    boolean installEgressFlow(DatapathId dpid, String cookie, int inputPort, int outputPort, int transitVlanId,
                           int outputVlanId, OutputVlanType outputVlanType);

    /**
     * installTransitFlow - install flow on a transit switch.
     *
     * @param dpid - datapathId of the switch
     * @param cookie - flow cookie
     * @param inputPort - port to expect packet on
     * @param outputPort - port to forward packet out
     * @param transitVlanId - vlan to match on inputPort
     */
    boolean installTransitFlow(DatapathId dpid, String cookie, int inputPort, int outputPort, int transitVlanId);

    /**
     * installOneSwitchFlow - install flow through one switch.
     *
     * @param dpid - datapathId of the switch
     * @param cookie - flow cookie
     * @param inputPort - port to expect packet on
     * @param outputPort - port to forward packet out
     * @param inputVlanId - vlan to match on inputPort
     * @param outputVlanId - set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType - type of action to apply to the outputVlanId if greater than 0
     */
    boolean installOneSwitchFlow(DatapathId dpid, String cookie, int inputPort, int outputPort, int inputVlanId, int outputVlanId,
                              OutputVlanType outputVlanType, long meterId);

    /**
     *
     */
    void dumpFlowTable();

    /**
     *
     */
    void dumpMeters();

    /**
     * installMeter - Installs a meter on ingress switch.
     * TODO: describe params meaning in accordance with OF
     *
     * @param dpid - datapath ID of the switch
     * @param bandwidth - the bandwidth limit value
     * @param burstSize - the size of the burst
     * @param meterId - the meter ID
     */
    boolean installMeter(DatapathId dpid, long bandwidth, long burstSize, long meterId);

    /**
     * Deletes the flow from the switch
     *
     * @param dpid datapath ID of the switch
     * @param cookie flow cookie
     */
    boolean deleteFlow(DatapathId dpid, String cookie);

    /**
     * Deletes the meter from the switch
     *
     * @param dpid datapath ID of the switch
     * @param meterId meter identifier
     */
    boolean deleteMeter(DatapathId dpid, long meterId);

    /**
     * Retrieves statistics for all ports of a switch with the given datapath ID.
     *
     * @param dpid datapath ID of the switch
     * @return statistics for all ports of a switch with the given datapath ID
     */
    ListenableFuture<List<OFPortStatsReply>> requestPortStats(DatapathId dpid);

    /**
     * Retrieves statistics for all non-system flows of a switch with the given datapath ID.
     *
     * @param dpid datapath ID of the switch
     * @return statistics for all non-system flows of a switch with the given datapath ID
     */
    ListenableFuture<List<OFFlowStatsReply>> requestFlowStats(DatapathId dpid);

    /**
     * Retrieves all meters configurations on a switch with the given datapath ID.
     *
     * @param dpid datapath ID of the switch
     * @return all meters configurations on a switch with the given datapath ID
     */
    ListenableFuture<List<OFMeterConfigStatsReply>> requestMeterConfigStats(DatapathId dpid);
}
