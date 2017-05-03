package org.bitbucket.openkilda.floodlight.switchmanager;

import com.google.common.util.concurrent.ListenableFuture;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;
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
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.bitbucket.openkilda.floodlight.message.command.Utils.ETH_TYPE;
import static org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_BCAST_PACKET_DST;
import static org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_PACKET_UDP_PORT;
import static org.projectfloodlight.openflow.protocol.OFMeterFlags.BURST;
import static org.projectfloodlight.openflow.protocol.OFMeterFlags.KBPS;
import static org.projectfloodlight.openflow.protocol.OFMeterModCommand.ADD;
import static org.projectfloodlight.openflow.protocol.OFMeterModCommand.DELETE;

/**
 * Created by jonv on 29/3/17.
 */
public class SwitchManager implements IFloodlightModule, IFloodlightService, ISwitchManager {
    static final U64 NON_SYSTEM_MASK = U64.of(0x7fffffffffffffffL);
    static final U64 SYSTEM_MASK = U64.of(0x8000000000000000L);
    static final long OFPM_ALL = 0xffffffffL;
    private static final Logger logger = LoggerFactory.getLogger(SwitchManager.class);
    private static final long DROP_COOKIE = 0x8000000000000001L;
    private IOFSwitchService ofSwitchService;
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
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(3);
        services.add(IFloodlightProviderService.class);
        services.add(IOFSwitchService.class);
        services.add(IRestApiService.class);
        return services;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        ofSwitchService = context.getServiceImpl(IOFSwitchService.class);
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
    public boolean installDefaultRules(final DatapathId dpid) {
        final boolean dropFlow = installDropFlow(dpid);
        final boolean broadcastVerification = installVerificationRule(dpid, true);
        final boolean unicastVerification = installVerificationRule(dpid, false);
        return dropFlow & broadcastVerification & unicastVerification;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean installIngressFlow(final DatapathId dpid, String cookie, final int inputPort, final int outputPort,
                                   final int inputVlanId, final int transitVlanId, final OutputVlanType outputVlanType,
                                   final long meterId) {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        // build match by input port and input vlan id
        Match match = matchFlow(sw, inputPort, inputVlanId);

        // build meter instruction
        OFInstructionMeter meter = meterId != 0 ? buildInstructionMeter(sw, meterId) : null;

        // output action based on encap scheme
        actionList.addAll(inputVlanTypeToOFActionList(sw, transitVlanId, outputVlanType));

        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(sw, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(sw, actionList);

        // build FLOW_MOD command with meter
        OFFlowMod flowMod = buildFlowMod(sw, match, meter, actions, U64.parseHex(cookie).getValue(), FlowModUtils.PRIORITY_VERY_HIGH);

        // send FLOW_MOD to the switch
        return pushFlow(cookie, dpid, flowMod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean installEgressFlow(final DatapathId dpid, String cookie, final int inputPort, final int outputPort,
                                  final int transitVlanId, final int outputVlanId,
                                  final OutputVlanType outputVlanType) {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        // build match by input port and transit vlan id
        Match match = matchFlow(sw, inputPort, transitVlanId);

        // output action based on encap scheme
        actionList.addAll(outputVlanTypeToOFActionList(sw, outputVlanId, outputVlanType));

        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(sw, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(sw, actionList);

        // build FLOW_MOD command, no meter
        OFFlowMod flowMod = buildFlowMod(sw, match, null, actions, U64.parseHex(cookie).getValue(), FlowModUtils.PRIORITY_VERY_HIGH);

        // send FLOW_MOD to the switch
        return pushFlow(cookie, dpid, flowMod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean installTransitFlow(final DatapathId dpid, String cookie, final int inputPort, final int outputPort,
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
        OFFlowMod flowMod = buildFlowMod(sw, match, null, actions, U64.parseHex(cookie).getValue(), FlowModUtils.PRIORITY_VERY_HIGH);

        // send FLOW_MOD to the switch
        return pushFlow(cookie, dpid, flowMod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean installOneSwitchFlow(DatapathId dpid, String cookie, int inputPort, int outputPort, int inputVlanId, int outputVlanId,
                                     OutputVlanType outputVlanType, long meterId) {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        // build match by input port and transit vlan id
        Match match = matchFlow(sw, inputPort, inputVlanId);

        // build meter instruction
        OFInstructionMeter meter = meterId != 0 ? buildInstructionMeter(sw, meterId) : null;

        // output action based on encap scheme
        actionList.addAll(pushSchemeOutputVlanTypeToOFActionList(sw, outputVlanId, outputVlanType));
        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(sw, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(sw, actionList);

        // build FLOW_MOD command with meter
        OFFlowMod flowMod = buildFlowMod(sw, match, meter, actions, U64.parseHex(cookie).getValue(), FlowModUtils.PRIORITY_VERY_HIGH);

        // send FLOW_MOD to the switch
        return pushFlow(cookie, dpid, flowMod);
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
    public boolean installMeter(DatapathId dpid, long bandwidth, long burstSize, long meterId) {
        if (meterId == 0) {
            logger.info("skip installing meter {} on switch {} width bandwidth {}", meterId, dpid, bandwidth);
            return true;
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

        return sw.write(meterModBuilder.build());
    }

    @Override
    public boolean deleteFlow(DatapathId dpid, String cookie) {
        final Masked<U64> masked = Masked.of(U64.parseHex(cookie), NON_SYSTEM_MASK);
        logger.info("deleting flows {} from switch {}", masked.toString(), dpid.toString());
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();
        OFFlowDelete flowDelete = ofFactory.buildFlowDelete()
                .setCookie(masked.getValue())
                .setCookieMask(masked.getMask())
                .setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM))
                .build();
        return sw.write(flowDelete);
    }

    @Override
    public boolean deleteMeter(DatapathId dpid, long meterId) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();
        OFMeterMod meterDelete = ofFactory.buildMeterMod()
                .setMeterId(meterId)
                .setCommand(DELETE)
                .build();
        return sw.write(meterDelete);
    }

    @Override
    public ListenableFuture<List<OFPortStatsReply>> requestPortStats(DatapathId dpid) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();
        OFPortStatsRequest request = ofFactory.buildPortStatsRequest().setPortNo(OFPort.ANY).build();
        return sw.writeStatsRequest(request);
    }

    @Override
    public ListenableFuture<List<OFFlowStatsReply>> requestFlowStats(DatapathId dpid) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();
        OFFlowStatsRequest request = ofFactory.buildFlowStatsRequest()
                .setCookie(U64.ZERO)
                .setCookieMask(SYSTEM_MASK)
                .build();
        return sw.writeStatsRequest(request);
    }

    @Override
    public ListenableFuture<List<OFMeterConfigStatsReply>> requestMeterConfigStats(DatapathId dpid) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();
        final OFMeterConfigStatsRequest request = ofFactory.buildMeterConfigStatsRequest()
                .setMeterId(OFPM_ALL)
                .build();
        return sw.writeStatsRequest(request);
    }

    // Utility Methods

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
     * replaceSchemeOutputVlanTypeToOFActionList - Builds OFAction list based on flow parameters for replace scheme
     *
     * @param sw - IOFSwitch instance
     * @param outputVlanId - set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType - type of action to apply to the outputVlanId if greater than 0
     * @return List<OFAction>
     */
    private List<OFAction> replaceSchemeOutputVlanTypeToOFActionList(IOFSwitch sw, int outputVlanId,
                                                                     OutputVlanType outputVlanType) {
        List<OFAction> actionList;

        switch (outputVlanType) {
            case PUSH:
            case REPLACE:
                actionList = singletonList(actionReplaceVlan(sw, outputVlanId));
                break;
            case POP:
            case NONE:
                actionList = singletonList(actionPopVlan(sw));
                break;
            default:
                actionList = emptyList();
                logger.error("Unknown OutputVlanType: " + outputVlanType);
        }

        return actionList;
    }

    /**
     * pushSchemeOutputVlanTypeToOFActionList - Builds OFAction list based on flow parameters for push scheme
     *
     * @param sw - IOFSwitch instance
     * @param outputVlanId - set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType - type of action to apply to the outputVlanId if greater than 0
     * @return List<OFAction>
     */
    private List<OFAction> pushSchemeOutputVlanTypeToOFActionList(IOFSwitch sw, int outputVlanId,
                                                                  OutputVlanType outputVlanType) {
        List<OFAction> actionList = new ArrayList<>(2);

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
     * outputVlanTypeToOFActionList - Chooses encapsulation scheme for building OFAction list
     *
     * @param sw - IOFSwitch instance
     * @param outputVlanId - set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType - type of action to apply to the outputVlanId if greater than 0
     * @return List<OFAction>
     */
    private List<OFAction> outputVlanTypeToOFActionList(IOFSwitch sw, int outputVlanId, OutputVlanType outputVlanType) {
        List<OFAction> actionList = new ArrayList<>(3);
        switch (sw.getSwitchDescription().getManufacturerDescription()) {
            case OVS_MANUFACTURER:
                actionList.addAll(replaceSchemeOutputVlanTypeToOFActionList(sw, outputVlanId, outputVlanType));
                break;
            default:
                // pop transit vlan
                actionList.add(actionPopVlan(sw));
                actionList.addAll(pushSchemeOutputVlanTypeToOFActionList(sw, outputVlanId, outputVlanType));
                break;
        }
        return actionList;
    }

    /**
     * inputVlanTypeToOFActionList - Chooses encapsulation scheme for building OFAction list
     *
     * @param sw - IOFSwitch instance
     * @param transitVlanId - set vlan on packet or replace it before forwarding via outputPort; 0 means not to set
     * @return List<OFAction>
     */
    private List<OFAction> inputVlanTypeToOFActionList(IOFSwitch sw, int transitVlanId, OutputVlanType outputVlanType) {
        List<OFAction> actionList = new ArrayList<>(3);
        if (!OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())
                || (OutputVlanType.PUSH.equals(outputVlanType) || OutputVlanType.NONE.equals(outputVlanType))) {
            actionList.add(actionPushVlan(sw, ETH_TYPE));
        }
        actionList.add(actionReplaceVlan(sw, transitVlanId));
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
        List<OFInstruction> instructions = new ArrayList<>(2);

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

    private Match matchVerification(final IOFSwitch sw, final boolean isBroadcast) {
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
     * installVerificationRule - Installs the verification rule
     *
     * @param dpid - datapathId of switch
     * @param isBroadcast - if broadcast then set a generic match; else specific to switch Id
     */
    private boolean installVerificationRule(final DatapathId dpid, final boolean isBroadcast) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        Match match = matchVerification(sw, isBroadcast);
        ArrayList<OFAction> actionList = new ArrayList<>(2);
        actionList.add(actionSendToController(sw));
        actionList.add(actionSetDstMac(sw, dpidToMac(sw)));
        OFInstructionApplyActions instructionApplyActions = sw.getOFFactory().instructions()
                .applyActions(actionList).createBuilder().build();
        final long cookie = isBroadcast ? 0x8000000000000002L : 0x8000000000000003L;
        OFFlowMod flowMod = buildFlowMod(sw, match, null, instructionApplyActions, cookie, FlowModUtils.PRIORITY_VERY_HIGH);
        String flowname = (isBroadcast) ? "Broadcast" : "Unicast";
        flowname += "--VerificationFlow--" + dpid.toString();
        return pushFlow(flowname, dpid, flowMod);
    }

    /**
     * installDropFlow - Installs a last-resort rule to drop all packets that don't match any match.
     *
     * @param dpid - datapathId of switch
     */
    private boolean installDropFlow(final DatapathId dpid) {
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        OFFlowMod flowMod = buildFlowMod(sw, null, null, null, DROP_COOKIE, 1);
        String flowname = "--DropRule--" + dpid.toString();
        return pushFlow(flowname, dpid, flowMod);
    }

    /**
     * Pushes a single flow modification command to the switch with the given datapath ID.
     *
     * @param flowName flow name, for logging
     * @param dpid switch datapath ID
     * @param flowMod command to send
     * @return true if the command is accepted to be sent to switch, false otherwise - switch is disconnected or in SLAVE mode
     */
    private boolean pushFlow(final String flowName, final DatapathId dpid, final OFFlowMod flowMod) {
        logger.info("installing flow: " + flowName);
        return ofSwitchService.getSwitch(dpid).write(flowMod);
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
}
