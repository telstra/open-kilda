package org.bitbucket.openkilda.floodlight.switchmanager;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import net.floodlightcontroller.util.FlowModUtils;
import org.bitbucket.openkilda.floodlight.switchmanager.web.SwitchManagerWebRoutable;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_BCAST_PACKET_DST;
import static org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_PACKET_UDP_PORT;

/**
 * Created by jonv on 29/3/17.
 */
public class SwitchManager implements IFloodlightModule, IFloodlightService, ISwitchManager {
    private Logger logger;
    private IOFSwitchService ofSwitchService;
    private IStaticEntryPusherService staticEntryPusher;
    private IRestApiService restApiService;

    /*
     * IFloodlightModule Methods
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        services.add(ISwitchManager.class);
        return services;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<>();
        map.put(ISwitchManager.class, this);
        return map;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        services.add(IFloodlightProviderService.class);
        services.add(IOFSwitchService.class);
        services.add(IStaticEntryPusherService.class);
        services.add(IRestApiService.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        logger = LoggerFactory.getLogger(SwitchManager.class);
        ofSwitchService = context.getServiceImpl(IOFSwitchService.class);
        staticEntryPusher = context.getServiceImpl(IStaticEntryPusherService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Starting " + SwitchEventCollector.class.getCanonicalName());
        restApiService.addRestletRoutable(new SwitchManagerWebRoutable());
    }

    /*
     * ISwitchManager Methods
     */
    @Override
    public void installDefaultRules(final DatapathId dpid) {
        installDropFlow(dpid);
        installVerificationRule(dpid, true);
        installVerificationRule(dpid, false);
    }

    @Override
    public void installIngressFlow(final DatapathId dpid, final int inputPort, final int outputPort,
                                   final int inputVlanId, final int transitVlanId) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        Match match = matchFlow(sw, inputPort, inputVlanId);
        List<OFAction> actionList = new ArrayList<>();
        OFFlowMod flowMod = buildFlowMod(sw, match, actionList, cookieMaker(), FlowModUtils.PRIORITY_VERY_HIGH);
        String flowname = "flow-" + dpid.toString() + "-" + inputPort + "-" + outputPort + "-" + transitVlanId;
        pushEntry(flowname, flowMod, dpid);
    }

    @Override
    public void installEgressFlow(final DatapathId dpid, final int inputPort, final int outputPort,
                                  final int transitVlanId, final int outputVlanId,
                                  final OutputVlanType outputVlanType) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        Match match = matchFlow(sw, inputPort, transitVlanId);
        List<OFAction> actionList = new ArrayList<>();
        actionList.add(actionPopVlan(sw));
        switch (outputVlanType) {
            case PUSH:      // No VLAN on packet so push a new one
                actionList.add(actionPushVlan(sw, 0x8100));
                actionList.add(actionReplaceVlan(sw, outputVlanId));
                break;
            case REPLACE:   // VLAN on packet but needs to be replaced
                actionList.add(actionReplaceVlan(sw, outputVlanId));
                break;
            case POP:   // VLAN on packet, so remove it
                        // TODO:  can i do this?  pop two vlan's back to back...
                actionList.add(actionPopVlan(sw));
                break;
            case NONE:
                break;
            default:
                logger.error("Unknown OutputVlanType: " + outputVlanType);
        }
        actionList.add(actionSetOutputPort(sw, outputPort));
        OFFlowMod flowMod = buildFlowMod(sw, match, actionList, cookieMaker(), FlowModUtils.PRIORITY_VERY_HIGH);
        String flowname = "flow-" + dpid.toString() + "-" + inputPort + "-" + outputPort + "-" + outputVlanId;
        pushEntry(flowname, flowMod, dpid);

    }

    @Override
    public void installTransitFlow(final DatapathId dpid, final int inputPort, final int outputPort,
                                   final int transitVlanId) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        Match match = matchFlow(sw, inputPort, transitVlanId);
        List<OFAction> actionList = new ArrayList<>();
        actionList.add(actionSetOutputPort(sw, outputPort));
        OFFlowMod flowMod = buildFlowMod(sw, match, actionList, cookieMaker(), FlowModUtils.PRIORITY_VERY_HIGH);
        String flowname = "flow-" + dpid.toString() + "-" + inputPort + "-" + outputPort + "-" + transitVlanId;
        pushEntry(flowname, flowMod, dpid);
    }

    @Override
    public void dumpFlowTable() {

    }

    @Override
    public void dumpMeters() {

    }

    // Utility Methods

    /**
     * cookieMaker - TODO: this needs to do something...
     *
     * @return long
     */
    private long cookieMaker() {
        return 123L;
    }

    /**
     * matchFlow - Creates a Match based on an inputPort and VlanID.  Note that this match only matches on the outer
     * most tag which must be of ether-type 0x8100.
     *
     * @param sw - switch object
     * @param inputPort - input port for the match
     * @param vlanId - vlanID to match on
     * @return Match
     */
    private Match matchFlow(final IOFSwitch sw, final int inputPort, final int vlanId) {
        Match.Builder mb = sw.getOFFactory().buildMatch();
        if (vlanId > 0) {
            mb.setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                    .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(vlanId));
        } else {
            mb.setExact(MatchField.IN_PORT, OFPort.of(inputPort));
        }
        return mb.build();
    }

    /**
     * actionSetOutputPort - Create an OFAction which sets the output port.
     *
     * @param sw - switch object
     * @param outputPort - port to set in the action
     * @return OFAction
     */
    private OFAction actionSetOutputPort(final IOFSwitch sw, final int outputPort) {
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildOutput().setMaxLen(0xFFFFFFFF).setPort(OFPort.of(outputPort)).build();
    }

    /**
     * actionReplaceVlan - Create an OFAction to change the outer most vlan.
     *
     * @param sw - switch object
     * @param newVlan - final VLAN to be set on the packet
     * @return OFAction
     */
    private OFAction actionReplaceVlan(final IOFSwitch sw, final int newVlan) {
        OFOxms oxms = sw.getOFFactory().oxms();
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildSetField()
                .setField(oxms.buildVlanVid().setValue(OFVlanVidMatch.ofVlan(newVlan)).build()).build();
    }

    /**
     * actionPushVlan - Create an OFAction to add a VLAN header.
     * @param sw - switch object
     * @param etherType - ethernet type of the new VLAN header
     * @return OFAction
     */
    private OFAction actionPushVlan(final IOFSwitch sw, final int etherType) {
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildPushVlan().setEthertype(EthType.of(etherType)).build();
    }

    /**
     * actionPopVlan - Create an OFAction to remove the outer most VLAN.
     *
     * @param sw - switch object
     * @return OFAction
     */
    private OFAction actionPopVlan(final IOFSwitch sw) {
        OFActions actions = sw.getOFFactory().actions();
        return actions.popVlan();
    }

    /**
     * buildFlowMod - Create an OFFlowMod that can be passed to StaticEntryPusher.
     *
     * @param sw - switch object
     * @param match - match for the flow
     * @param actionList - actions for the flow
     * @param cookie - cookie for the flow
     * @param priority - priority to set on the flow
     * @return OFFlowMod
     */
    private OFFlowMod buildFlowMod(final IOFSwitch sw, final Match match, final List<OFAction> actionList,
                                   final long cookie, final int priority) {
        OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
        fmb.setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT);
        fmb.setHardTimeout(FlowModUtils.INFINITE_TIMEOUT);
        fmb.setBufferId(OFBufferId.NO_BUFFER);
        fmb.setCookie(U64.of(cookie));
        fmb.setPriority(priority);

        if (actionList != null) {       // If no action then Drops packet
            fmb.setActions(actionList);
        }

        if (match != null) {            // If no then match everything
            fmb.setMatch(match);
        }
        return fmb.build();
    }

    /**
     * dpidToMac - Create a MAC address based on the DPID.
     *
     * @param sw - switch object
     * @return MacAddress
     */

    public MacAddress dpidToMac(final IOFSwitch sw) {
        return MacAddress.of(Arrays.copyOfRange(sw.getId().getBytes(), 2, 8));
    }

    /**
     * buildVerificationMatch - Create a match object for the verification packets
     *
     * @param sw - siwtch object
     * @param isBroadcast - if broadcast then set a generic match; else specific to switch Id
     * @return Match
     */

    private Match matchVerificaiton(final IOFSwitch sw, final boolean isBroadcast) {
        MacAddress dstMac = MacAddress.of(VERIFICATION_BCAST_PACKET_DST);
        if (!isBroadcast) {
            dstMac = dpidToMac(sw);
        }
        Match.Builder mb = sw.getOFFactory().buildMatch();
        mb.setExact(MatchField.ETH_DST, dstMac).setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_DST, TransportPort.of(VERIFICATION_PACKET_UDP_PORT))
                .setExact(MatchField.UDP_SRC, TransportPort.of(VERIFICATION_PACKET_UDP_PORT));
        return mb.build();
    }

    /**
     * actionSendToController - Create an action to send packet to the controller.
     *
     * @param sw - switch object
     * @return OFAction
     */
    private OFAction actionSendToController(final IOFSwitch sw) {
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildOutput().setMaxLen(0xFFffFFff).setPort(OFPort.CONTROLLER)
                .build();
    }

    /**
     * actionSetMac - Create an action to set the DstMac of a packet
     *
     * @param sw - switch object
     * @param macAddress - MacAddress to set
     * @return OFAction
     */
    private OFAction actionSetDstMac(final IOFSwitch sw, final MacAddress macAddress) {
        OFOxms oxms = sw.getOFFactory().oxms();
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildSetField()
                .setField(oxms.buildEthDst().setValue(macAddress).build()).build();
    }

    /**
     * installVerificatioRule - Installs the verification rule
     *
     * @param dpid - datapathId of switch
     * @param isBroadcast - if boradcast then set a generic match; else specific to switch Id
     */
    private void installVerificationRule(final DatapathId dpid, final boolean isBroadcast) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        Match match = matchVerificaiton(sw, isBroadcast);
        ArrayList<OFAction> actionList = new ArrayList<>();
        actionList.add(actionSendToController(sw));
        actionList.add(actionSetDstMac(sw, dpidToMac(sw)));
        OFFlowMod flowMod = buildFlowMod(sw, match, actionList, cookieMaker(), FlowModUtils.PRIORITY_VERY_HIGH);

        String flowname = (isBroadcast) ? "Broadcast" : "Unicast";
        flowname += "--VerificationFlow--" + dpid.toString();
        pushEntry(flowname, flowMod, dpid);
    }

    /**
     * installDropFlow - Installs a last-resort rule to drop all packets that don't match any match.
     * @param dpid - datapathId of switch
     */
    private void installDropFlow(final DatapathId dpid) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        OFFlowMod flowMod = buildFlowMod(sw, null, null, 1L, 1);
        String flowname = "--DropRule--" + dpid.toString();
        pushEntry(flowname, flowMod, dpid);
    }

    private void pushEntry(final String entryName, final OFFlowMod flowMod, final DatapathId dpid) {
        logger.info("installing flow: " + entryName);
        staticEntryPusher.addFlow(entryName, flowMod, dpid);
    }
}
