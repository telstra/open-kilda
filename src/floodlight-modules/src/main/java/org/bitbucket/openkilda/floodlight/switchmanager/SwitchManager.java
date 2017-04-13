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
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;
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

import java.util.*;

import static java.util.Collections.singletonList;
import static org.bitbucket.openkilda.floodlight.message.command.Utils.ETH_TYPE;
import static org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_BCAST_PACKET_DST;
import static org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_PACKET_UDP_PORT;
import static org.projectfloodlight.openflow.protocol.OFMeterFlags.BURST;
import static org.projectfloodlight.openflow.protocol.OFMeterFlags.KBPS;
import static org.projectfloodlight.openflow.protocol.OFMeterModCommand.ADD;

/**
 * Created by jonv on 29/3/17.
 */
public class SwitchManager implements IFloodlightModule, IFloodlightService, ISwitchManager {
    private static final Logger logger = LoggerFactory.getLogger(SwitchManager.class);
    private IOFSwitchService ofSwitchService;
    private IStaticEntryPusherService staticEntryPusher;
    private IRestApiService restApiService;

    // IFloodlightModule Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        services.add(ISwitchManager.class);
        return services;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<>();
        map.put(ISwitchManager.class, this);
        return map;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        services.add(IFloodlightProviderService.class);
        services.add(IOFSwitchService.class);
        services.add(IStaticEntryPusherService.class);
        services.add(IRestApiService.class);
        return services;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        ofSwitchService = context.getServiceImpl(IOFSwitchService.class);
        staticEntryPusher = context.getServiceImpl(IStaticEntryPusherService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Starting " + SwitchEventCollector.class.getCanonicalName());
        restApiService.addRestletRoutable(new SwitchManagerWebRoutable());
    }

    // ISwitchManager Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public void installDefaultRules(final DatapathId dpid) {
        installDropFlow(dpid);
        installVerificationRule(dpid, true);
        installVerificationRule(dpid, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void installIngressFlow(final DatapathId dpid, final int inputPort, final int outputPort,
                                   final int inputVlanId, final int transitVlanId, final long meterId) {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        // build match by input port and input vlan id
        Match match = matchFlow(sw, inputPort, inputVlanId);

        // build meter instruction
        OFInstructionMeter meter = meterId != 0 ? buildInstructionMeter(sw, meterId) : null;

        // push transit vlan
        actionList.add(actionPushVlan(sw, ETH_TYPE));
        actionList.add(actionReplaceVlan(sw, transitVlanId));
        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(sw, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(sw, actionList);

        // build FLOW_MOD command with meter
        OFFlowMod flowMod = buildFlowMod(sw, match, meter, actions, cookieMaker(), FlowModUtils.PRIORITY_VERY_HIGH);

        // build flow name
        String flowName = buildFlowName(dpid, inputPort, outputPort, transitVlanId);

        // send FLOW_MOD to the switch
        pushEntry(flowName, flowMod, dpid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void installEgressFlow(final DatapathId dpid, final int inputPort, final int outputPort,
                                  final int transitVlanId, final int outputVlanId,
                                  final OutputVlanType outputVlanType) {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        // build match by input port and transit vlan id
        Match match = matchFlow(sw, inputPort, transitVlanId);

        // pop transit vlan
        actionList.add(actionPopVlan(sw));
        // output action based on flow type
        actionList.addAll(outputVlanTypeToOFActionList(sw, outputVlanId, outputVlanType));
        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(sw, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(sw, actionList);

        // build FLOW_MOD command, no meter
        OFFlowMod flowMod = buildFlowMod(sw, match, null, actions, cookieMaker(), FlowModUtils.PRIORITY_VERY_HIGH);

        // build flow name
        String flowName = buildFlowName(dpid, inputPort, outputPort, outputVlanId);

        // send FLOW_MOD to the switch
        pushEntry(flowName, flowMod, dpid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void installTransitFlow(final DatapathId dpid, final int inputPort, final int outputPort,
                                   final int transitVlanId) {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        // build match by input port and transit vlan id
        Match match = matchFlow(sw, inputPort, transitVlanId);

        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(sw, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(sw, actionList);

        // build FLOW_MOD command, no meter
        OFFlowMod flowMod = buildFlowMod(sw, match, null, actions, cookieMaker(), FlowModUtils.PRIORITY_VERY_HIGH);

        // build flow name
        String flowName = buildFlowName(dpid, inputPort, outputPort, transitVlanId);

        // send FLOW_MOD to the switch
        pushEntry(flowName, flowMod, dpid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void installOneSwitchFlow(DatapathId dpid, int inputPort, int outputPort, int inputVlanId, int outputVlanId,
                                     OutputVlanType outputVlanType, long meterId) {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        // build match by input port and transit vlan id
        Match match = matchFlow(sw, inputPort, inputVlanId);

        // build meter instruction
        OFInstructionMeter meter = meterId != 0 ? buildInstructionMeter(sw, meterId) : null;

        // output action based on flow type
        actionList.addAll(outputVlanTypeToOFActionList(sw, outputVlanId, outputVlanType));
        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(sw, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(sw, actionList);

        // build FLOW_MOD command with meter
        OFFlowMod flowMod = buildFlowMod(sw, match, meter, actions, cookieMaker(), FlowModUtils.PRIORITY_VERY_HIGH);

        // build flow name
        String flowName = buildFlowName(dpid, inputPort, outputPort, outputVlanId);

        // send FLOW_MOD to the switch
        pushEntry(flowName, flowMod, dpid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dumpFlowTable() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dumpMeters() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void installMeter(DatapathId dpid, long bandwidth, long burstSize, long meterId) {
        if (meterId == 0) {
            logger.info("skip installing meter {} on switch {} width bandwidth {}", meterId, dpid, bandwidth);
            return;
        }
        logger.debug("installing meter {} on switch {} width bandwidth {}", meterId, dpid, bandwidth);

        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();
        Set<OFMeterFlags> flags = new HashSet<>(Arrays.asList(KBPS, BURST));

        OFMeterBandDrop.Builder bandBuilder = ofFactory.meterBands()
                .buildDrop()
                .setRate(bandwidth)
                .setBurstSize(burstSize);

        OFMeterMod.Builder meterModBuilder = ofFactory.buildMeterMod()
                .setMeterId(meterId)
                .setCommand(ADD)
                .setMeters(singletonList(bandBuilder.build()))
                .setFlags(flags);

        sw.write(meterModBuilder.build());
    }

    // Utility Methods

    /**
     * cookieMaker - TODO: this needs to do something...
     *
     * @return long
     */
    protected static long cookieMaker() {
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
     * outputVlanTypeToOFActionList - Builds OFAction list based on flow output parameters
     *
     * @param sw - IOFSwitch instance
     * @param outputVlanId - set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType - type of action to apply to the outputVlanId if greater than 0
     * @return List<OFAction>
     */
    protected List<OFAction> outputVlanTypeToOFActionList(IOFSwitch sw, int outputVlanId, OutputVlanType outputVlanType) {
        List<OFAction> actionList = new ArrayList<>();

        switch (outputVlanType) {
            case PUSH:      // No VLAN on packet so push a new one
                actionList.add(actionPushVlan(sw, ETH_TYPE));
                actionList.add(actionReplaceVlan(sw, outputVlanId));
                break;
            case REPLACE:   // VLAN on packet but needs to be replaced
                actionList.add(actionReplaceVlan(sw, outputVlanId));
                break;
            case POP:       // VLAN on packet, so remove it
                            // TODO:  can i do this?  pop two vlan's back to back...
                actionList.add(actionPopVlan(sw));
                break;
            case NONE:
                break;
            default:
                logger.error("Unknown OutputVlanType: " + outputVlanType);
        }

        return actionList;
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
     *
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
     * @param meter - meter for the flow
     * @param actions - actions for the flow
     * @param cookie - cookie for the flow
     * @param priority - priority to set on the flow
     * @return OFFlowMod
     */
    private OFFlowMod buildFlowMod(final IOFSwitch sw, final Match match, final OFInstructionMeter meter,
                                   final OFInstructionApplyActions actions, final long cookie, final int priority) {
        OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
        fmb.setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT);
        fmb.setHardTimeout(FlowModUtils.INFINITE_TIMEOUT);
        fmb.setBufferId(OFBufferId.NO_BUFFER);
        fmb.setCookie(U64.of(cookie));
        fmb.setPriority(priority);
        List<OFInstruction> instructions = new ArrayList<>();

        if (meter != null) {              // If no meter then no bandwidth limit
            instructions.add(meter);
        }

        if (actions != null) {       // If no instruction then Drops packet
            instructions.add(actions);
        }

        if (match != null) {              // If no then match everything
            fmb.setMatch(match);
        }

        return fmb.setInstructions(instructions).build();
    }

    /**
     * dpidToMac - Create a MAC address based on the DPID.
     *
     * @param sw - switch object
     * @return MacAddress
     */
    private MacAddress dpidToMac(final IOFSwitch sw) {
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
        OFInstructionApplyActions instructionApplyActions = sw.getOFFactory().instructions()
                .applyActions(actionList).createBuilder().build();
        OFFlowMod flowMod = buildFlowMod(sw, match, null, instructionApplyActions, cookieMaker(), FlowModUtils.PRIORITY_VERY_HIGH);
        String flowname = (isBroadcast) ? "Broadcast" : "Unicast";
        flowname += "--VerificationFlow--" + dpid.toString();
        pushEntry(flowname, flowMod, dpid);
    }

    /**
     * installDropFlow - Installs a last-resort rule to drop all packets that don't match any match.
     *
     * @param dpid - datapathId of switch
     */
    private void installDropFlow(final DatapathId dpid) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        OFFlowMod flowMod = buildFlowMod(sw, null, null, null, 1L, 1);
        String flowname = "--DropRule--" + dpid.toString();
        pushEntry(flowname, flowMod, dpid);
    }

    /**
     * pushEntry -
     *
     * @param entryName
     * @param flowMod
     * @param dpid
     */
    private void pushEntry(final String entryName, final OFFlowMod flowMod, final DatapathId dpid) {
        logger.info("installing flow: " + entryName);
        staticEntryPusher.addFlow(entryName, flowMod, dpid);
    }

    /**
     * buildInstructionApplyActions - Create an OFInstructionApplyActions which applies actions.
     *
     * @param sw - switch object
     * @param actionList - OFAction list to apply
     * @return OFInstructionApplyActions
     */
    private static OFInstructionApplyActions buildInstructionApplyActions(IOFSwitch sw, List<OFAction> actionList) {
        return sw.getOFFactory().instructions().applyActions(actionList).createBuilder().build();
    }

    /**
     * buildInstructionMeter - Create an OFInstructionMeter which sets the meter ID.
     *
     * @param sw - switch object
     * @param meterId - the meter ID
     * @return OFInstructionMeter
     */
    private static OFInstructionMeter buildInstructionMeter(final IOFSwitch sw, final long meterId) {
        return sw.getOFFactory().instructions().buildMeter().setMeterId(meterId).build();
    }

    /**
     * buildFlowName - Builds name for the flow based on flow parameters
     *
     * @param dpid - datapathId of the switch
     * @param inputPort - port to expect packet on
     * @param outputPort - port to forward packet out
     * @param outputVlanId - set vlan on packet before forwarding via outputPort; 0 means not to set
     * @return Flow name
     */
    protected static String buildFlowName(DatapathId dpid, int inputPort, int outputPort, int outputVlanId) {
        return "flow-" + dpid.toString() + "-" + inputPort + "-" + outputPort + "-" + outputVlanId;
    }
}
