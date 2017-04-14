package org.bitbucket.openkilda.floodlight.switchmanager;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.types.DatapathId;

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
    void installDefaultRules(DatapathId dpid);

    /**
     * installIngressFlow - Installs an flow on ingress switch.
     *
     * @param dpid - datapathId of the switch
     * @param inputPort - port to expect the packet on
     * @param outputPort - port to forward the packet out
     * @param inputVlanId - input vlan to match on, 0 means not to match on vlan
     * @param transitVlanId - vlan to add before outputing on outputPort
     */
    void installIngressFlow(DatapathId dpid, int inputPort, int outputPort, int inputVlanId, int transitVlanId,
                            OutputVlanType outputVlanType, long meterid);

    /**
     * installEgressFlow - Install flow on egress swtich.
     *
     * @param dpid - datapathId of the switch
     * @param inputPort - port to expect the packet on
     * @param outputPort - port to forward the packet out
     * @param transitVlanId - vlan to match on the ingressPort
     * @param outputVlanId - set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType - type of action to apply to the outputVlanId if greater than 0
     */
    void installEgressFlow(DatapathId dpid, int inputPort, int outputPort, int transitVlanId, int outputVlanId,
                           OutputVlanType outputVlanType);

    /**
     * installTransitFlow - install flow on a transit switch.
     *
     * @param dpid - datapathId of the switch
     * @param inputPort - port to expect packet on
     * @param outputPort - port to forward packet out
     * @param transitVlanId - vlan to match on inputPort
     */
    void installTransitFlow(DatapathId dpid, int inputPort, int outputPort, int transitVlanId);

    /**
     * installOneSwitchFlow - install flow through one switch.
     *
     * @param dpid - datapathId of the switch
     * @param inputPort - port to expect packet on
     * @param outputPort - port to forward packet out
     * @param inputVlanId - vlan to match on inputPort
     * @param outputVlanId - set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType - type of action to apply to the outputVlanId if greater than 0
     */
    void installOneSwitchFlow(DatapathId dpid, int inputPort, int outputPort, int inputVlanId, int outputVlanId,
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
     * @param dpid - datapathId of the switch
     * @param bandwidth - the bandwidth limit value
     * @param burstSize - the size of the burst
     * @param meterId - the meter ID
     */
    void installMeter(DatapathId dpid, long bandwidth, long burstSize, long meterId);
}
