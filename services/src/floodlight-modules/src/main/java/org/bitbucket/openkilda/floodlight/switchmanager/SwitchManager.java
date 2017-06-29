package org.bitbucket.openkilda.floodlight.switchmanager;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_BCAST_PACKET_DST;
import static org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_PACKET_UDP_PORT;
import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.ETH_TYPE;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_12;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_13;

import org.bitbucket.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.bitbucket.openkilda.floodlight.switchmanager.web.SwitchManagerWebRoutable;
import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.payload.flow.OutputVlanType;

import com.google.common.util.concurrent.ListenableFuture;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.util.FlowModUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFLegacyMeterBandDrop;
import org.projectfloodlight.openflow.protocol.OFLegacyMeterFlags;
import org.projectfloodlight.openflow.protocol.OFLegacyMeterMod;
import org.projectfloodlight.openflow.protocol.OFLegacyMeterModCommand;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.OFType;
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
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by jonv on 29/3/17.
 */
public class SwitchManager implements IFloodlightModule, IFloodlightService, ISwitchManager, IOFMessageListener {
    public static final long FLOW_COOKIE_MASK = 0x60000000FFFFFFFFL;
    static final U64 NON_SYSTEM_MASK = U64.of(0x80000000FFFFFFFFL);
    private static final long DROP_COOKIE = 0x8000000000000001L;
    private static final Logger logger = LoggerFactory.getLogger(SwitchManager.class);
    private IFloodlightProviderService floodlightProvider;
    private IOFSwitchService ofSwitchService;
    private IRestApiService restApiService;
    private KafkaMessageProducer kafkaProducer;

    // IFloodlightModule Methods

    /**
     * Create an OFInstructionApplyActions which applies actions.
     *
     * @param sw         switch object
     * @param actionList OFAction list to apply
     * @return {@link OFInstructionApplyActions}
     */
    private static OFInstructionApplyActions buildInstructionApplyActions(IOFSwitch sw, List<OFAction> actionList) {
        return sw.getOFFactory().instructions().applyActions(actionList).createBuilder().build();
    }

    /**
     * Create an OFInstructionMeter which sets the meter ID.
     *
     * @param sw      switch object
     * @param meterId the meter ID
     * @return {@link OFInstructionMeter}
     */
    private static OFInstructionMeter buildInstructionMeter(final IOFSwitch sw, final long meterId) {
        if (meterId == 0L || sw.getOFFactory().getVersion().compareTo(OF_13) < 0
                || OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            return null;
        } else {
            return sw.getOFFactory().instructions().buildMeter().setMeterId(meterId).build();
        }
    }

    /**
     * Returns legacy meter action.
     *
     * @param sw      switch object
     * @param meterId meter id
     * @return {@link OFAction}
     */
    private static OFAction legacyMeterAction(final IOFSwitch sw, final long meterId) {
        return sw.getOFFactory().actions().buildNiciraLegacyMeter().setMeterId(meterId).build();
    }

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
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        ofSwitchService = context.getServiceImpl(IOFSwitchService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
        kafkaProducer = context.getServiceImpl(KafkaMessageProducer.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Starting " + SwitchEventCollector.class.getCanonicalName());
        restApiService.addRestletRoutable(new SwitchManagerWebRoutable());
        floodlightProvider.addOFMessageListener(OFType.ERROR, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        logger.debug("OF_ERROR: {}", msg);
        // TODO: track xid for flow id
        if (OFType.ERROR.equals(msg.getType())) {
            ErrorMessage error = new ErrorMessage(
                    new ErrorData(ErrorType.INTERNAL_ERROR, ((OFErrorMsg) msg).getErrType().toString(), ""),
                    System.currentTimeMillis(), DEFAULT_CORRELATION_ID, Destination.WFM_TRANSACTION);
            kafkaProducer.postMessage("kilda-test", error);
        }
        return Command.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "KildaSwitchManager";
    }

    // ISwitchManager Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        logger.trace("isCallbackOrderingPrereq for {} : {}", type, name);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        logger.trace("isCallbackOrderingPostreq for {} : {}", type, name);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean installDefaultRules(final DatapathId dpid) {
        final boolean dropFlow = installDropFlow(dpid);
        final boolean broadcastVerification = installVerificationRule(dpid, true);
        if (ofSwitchService.getSwitch(dpid).getOFFactory().getVersion().compareTo(OF_12) > 0) {
            logger.debug("installing unicast verification match for {}", dpid.toString());
            final boolean unicastVerification = installVerificationRule(dpid, false);
            return dropFlow & broadcastVerification & unicastVerification;
        } else {
            logger.debug("not installing unicast verification match for {}", dpid.toString());
            return dropFlow & broadcastVerification;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<Long, Boolean> installIngressFlow(final DatapathId dpid, final String flowId,
                                                           final Long cookie, final int inputPort, final int outputPort,
                                                           final int inputVlanId, final int transitVlanId,
                                                           final OutputVlanType outputVlanType, final long meterId) {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        // build match by input port and input vlan id
        Match match = matchFlow(sw, inputPort, inputVlanId);

        // build meter instruction
        OFInstructionMeter meter = null;
        if (meterId != 0L) {
            if (sw.getOFFactory().getVersion().compareTo(OF_13) < 0) {
                actionList.add(legacyMeterAction(sw, meterId));
            } else {
                meter = buildInstructionMeter(sw, meterId);
            }
        }

        // output action based on encap scheme
        actionList.addAll(inputVlanTypeToOFActionList(sw, transitVlanId, outputVlanType));

        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(sw, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(sw, actionList);

        // build FLOW_MOD command with meter
        OFFlowMod flowMod = buildFlowMod(sw, match, meter, actions,
                cookie & FLOW_COOKIE_MASK, FlowModUtils.PRIORITY_VERY_HIGH);

        // send FLOW_MOD to the switch
        boolean response = pushFlow(flowId, dpid, flowMod);
        return new ImmutablePair<>(flowMod.getXid(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<Long, Boolean> installEgressFlow(final DatapathId dpid, String flowId, final Long cookie,
                                                          final int inputPort, final int outputPort,
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
        OFFlowMod flowMod = buildFlowMod(sw, match, null, actions,
                cookie & FLOW_COOKIE_MASK, FlowModUtils.PRIORITY_VERY_HIGH);

        // send FLOW_MOD to the switch
        boolean response = pushFlow(flowId, dpid, flowMod);
        return new ImmutablePair<>(flowMod.getXid(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<Long, Boolean> installTransitFlow(final DatapathId dpid, final String flowId,
                                                           final Long cookie, final int inputPort, final int outputPort,
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
        OFFlowMod flowMod = buildFlowMod(sw, match, null, actions,
                cookie & FLOW_COOKIE_MASK, FlowModUtils.PRIORITY_VERY_HIGH);

        // send FLOW_MOD to the switch
        boolean response = pushFlow(flowId, dpid, flowMod);
        return new ImmutablePair<>(flowMod.getXid(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<Long, Boolean> installOneSwitchFlow(final DatapathId dpid, final String flowId,
                                                             final Long cookie, final int inputPort,
                                                             final int outputPort, final int inputVlanId,
                                                             final int outputVlanId,
                                                             final OutputVlanType outputVlanType, final long meterId) {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        // build match by input port and transit vlan id
        Match match = matchFlow(sw, inputPort, inputVlanId);

        // build meter instruction
        OFInstructionMeter meter = null;
        if (meterId != 0L) {
            if (sw.getOFFactory().getVersion().compareTo(OF_13) < 0) {
                actionList.add(legacyMeterAction(sw, meterId));
            } else {
                meter = buildInstructionMeter(sw, meterId);
            }
        }

        // output action based on encap scheme
        actionList.addAll(pushSchemeOutputVlanTypeToOFActionList(sw, outputVlanId, outputVlanType));
        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(sw, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(sw, actionList);

        // build FLOW_MOD command with meter
        OFFlowMod flowMod = buildFlowMod(sw, match, meter, actions,
                cookie & FLOW_COOKIE_MASK, FlowModUtils.PRIORITY_VERY_HIGH);

        // send FLOW_MOD to the switch
        boolean response = pushFlow(flowId, dpid, flowMod);
        return new ImmutablePair<>(flowMod.getXid(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OFFlowStatsReply dumpFlowTable(final DatapathId dpid) {
        OFFlowStatsReply values = null;
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        if (sw == null) {
            throw new IllegalArgumentException(String.format("Switch %s was not found", dpid.toString()));
        }

        OFFactory ofFactory = sw.getOFFactory();
        OFFlowStatsRequest flowRequest = ofFactory.buildFlowStatsRequest()
                .setMatch(sw.getOFFactory().matchWildcardAll())
                .setTableId(TableId.ALL)
                .setOutPort(OFPort.ANY)
                .setOutGroup(OFGroup.ANY)
                .setCookieMask(U64.ZERO)
                .build();

        try {
            ListenableFuture<OFFlowStatsReply> future = sw.writeRequest(flowRequest);
            values = future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            logger.error("Could not get flow stats: {}", e.getMessage());
        }

        return values;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OFMeterConfigStatsReply dumpMeters(final DatapathId dpid) {
        OFMeterConfigStatsReply values = null;
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        if (sw == null) {
            throw new IllegalArgumentException(String.format("Switch %s was not found", dpid.toString()));
        }

        OFFactory ofFactory = sw.getOFFactory();
        OFMeterConfigStatsRequest meterRequest = ofFactory.buildMeterConfigStatsRequest()
                .setMeterId(0xffffffff)
                .build();

        try {
            ListenableFuture<OFMeterConfigStatsReply> future = sw.writeRequest(meterRequest);
            values = future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            logger.error("Could not get meter config stats: {}", e.getMessage());
        }

        return values;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<Long, Boolean> installMeter(final DatapathId dpid, final long bandwidth, final long burstSize,
                                                     final long meterId) {
        if (meterId == 0) {
            logger.info("skip installing meter {} on switch {} width bandwidth {}", meterId, dpid, bandwidth);
            return new ImmutablePair<>(0L, true);
        }

        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        if (OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            logger.info("skip installing meter {} on switch {} width bandwidth {}", meterId, dpid, bandwidth);
            return new ImmutablePair<>(0L, true);
        }

        logger.debug("installing meter {} on OVS switch {} width bandwidth {}", meterId, dpid, bandwidth);

        Set<OFMeterFlags> flags = new HashSet<>(Arrays.asList(OFMeterFlags.KBPS, OFMeterFlags.BURST));
        OFFactory ofFactory = sw.getOFFactory();

        OFMeterBandDrop.Builder bandBuilder = ofFactory.meterBands()
                .buildDrop()
                .setRate(bandwidth)
                .setBurstSize(burstSize);

        OFMeterMod.Builder meterModBuilder = ofFactory.buildMeterMod()
                .setMeterId(meterId)
                .setCommand(OFMeterModCommand.ADD)
                .setFlags(flags);

        if (sw.getOFFactory().getVersion().compareTo(OF_13) > 0) {
            meterModBuilder.setBands(singletonList(bandBuilder.build()));
        } else {
            meterModBuilder.setMeters(singletonList(bandBuilder.build()));
        }

        OFMeterMod meterMod = meterModBuilder.build();

        boolean response = sw.write(meterMod);
        return new ImmutablePair<>(meterMod.getXid(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<Long, Boolean> installLegacyMeter(final DatapathId dpid, final long bandwidth,
                                                           final long burstSize, final long meterId) {
        if (meterId == 0) {
            logger.info("skip installing meter {} on switch {} width bandwidth {}", meterId, dpid, bandwidth);
            return new ImmutablePair<>(0L, true);
        }

        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        if (OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            logger.info("skip installing meter {} on switch {} width bandwidth {}", meterId, dpid, bandwidth);
            return new ImmutablePair<>(0L, true);
        }

        logger.debug("installing meter {} on OVS switch {} width bandwidth {}", meterId, dpid, bandwidth);

        Set<OFLegacyMeterFlags> flags = new HashSet<>(Arrays.asList(OFLegacyMeterFlags.KBPS, OFLegacyMeterFlags.BURST));
        OFFactory ofFactory = sw.getOFFactory();

        OFLegacyMeterBandDrop.Builder bandBuilder = ofFactory.legacyMeterBandDrop(bandwidth, burstSize).createBuilder();

        OFLegacyMeterMod meterMod = ofFactory.buildLegacyMeterMod()
                .setMeterId(meterId)
                .setCommand(OFLegacyMeterModCommand.ADD)
                .setMeters(singletonList(bandBuilder.build()))
                .setFlags(flags)
                .build();

        boolean response = sw.write(meterMod);
        return new ImmutablePair<>(meterMod.getXid(), response);
    }

    // Utility Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<Long, Boolean> deleteFlow(final DatapathId dpid, final String flowId, final Long cookie) {
        logger.info("deleting flows {} from switch {}", flowId, dpid.toString());

        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();
        OFFlowDelete flowDelete = ofFactory.buildFlowDelete()
                .setCookie(U64.of(cookie))
                .setCookieMask(NON_SYSTEM_MASK)
                .build();

        boolean response = sw.write(flowDelete);
        return new ImmutablePair<>(flowDelete.getXid(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<Long, Boolean> deleteMeter(final DatapathId dpid, final long meterId) {
        if (meterId == 0) {
            logger.info("skip deleting meter {} from switch {}", meterId, dpid);
            return new ImmutablePair<>(0L, true);
        }

        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        if (OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            logger.info("skip deleting meter {} from OVS switch {}", meterId, dpid);
            return new ImmutablePair<>(0L, true);
        }

        logger.debug("deleting meter {} from switch {}", meterId, dpid);

        OFFactory ofFactory = sw.getOFFactory();

        OFMeterMod.Builder meterDeleteBuilder = ofFactory.buildMeterMod()
                .setMeterId(meterId)
                .setCommand(OFMeterModCommand.DELETE);

        if (sw.getOFFactory().getVersion().compareTo(OF_13) > 0) {
            meterDeleteBuilder.setBands(emptyList());
        } else {
            meterDeleteBuilder.setMeters(emptyList());
        }

        OFMeterMod meterDelete = meterDeleteBuilder.build();

        boolean response = sw.write(meterDelete);
        return new ImmutablePair<>(meterDelete.getXid(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<Long, Boolean> deleteLegacyMeter(final DatapathId dpid, final long meterId) {
        if (meterId == 0) {
            logger.info("skip deleting meter {} from switch {}", meterId, dpid);
            return new ImmutablePair<>(0L, true);
        }

        IOFSwitch sw = ofSwitchService.getSwitch(dpid);

        if (OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            logger.info("skip deleting meter {} from OVS switch {}", meterId, dpid);
            return new ImmutablePair<>(0L, true);
        }

        logger.debug("deleting meter {} from switch {}", meterId, dpid);

        OFFactory ofFactory = sw.getOFFactory();

        OFLegacyMeterMod meterDelete = ofFactory.buildLegacyMeterMod()
                .setMeterId(meterId)
                .setMeters(emptyList())
                .setCommand(OFLegacyMeterModCommand.DELETE)
                .build();

        boolean response = sw.write(meterDelete);
        return new ImmutablePair<>(meterDelete.getXid(), response);
    }

    /**
     * Creates a Match based on an inputPort and VlanID.
     * Note that this match only matches on the outer most tag which must be of ether-type 0x8100.
     *
     * @param sw        switch object
     * @param inputPort input port for the match
     * @param vlanId    vlanID to match on
     * @return {@link Match}
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
     * Builds OFAction list based on flow parameters for replace scheme.
     *
     * @param sw             IOFSwitch instance
     * @param outputVlanId   set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @return list of {@link OFAction}
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
     * Builds OFAction list based on flow parameters for push scheme.
     *
     * @param sw             IOFSwitch instance
     * @param outputVlanId   set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @return list of {@link OFAction}
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
     * Chooses encapsulation scheme for building OFAction list.
     *
     * @param sw             IOFSwitch instance
     * @param outputVlanId   set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @return list of {@link OFAction}
     */
    private List<OFAction> outputVlanTypeToOFActionList(IOFSwitch sw, int outputVlanId, OutputVlanType outputVlanType) {
        return replaceSchemeOutputVlanTypeToOFActionList(sw, outputVlanId, outputVlanType);
    }

    /**
     * Chooses encapsulation scheme for building OFAction list.
     *
     * @param sw            {@link IOFSwitch} instance
     * @param transitVlanId set vlan on packet or replace it before forwarding via outputPort; 0 means not to set
     * @return list of {@link OFAction}
     */
    private List<OFAction> inputVlanTypeToOFActionList(IOFSwitch sw, int transitVlanId, OutputVlanType outputVlanType) {
        List<OFAction> actionList = new ArrayList<>(3);
        if (OutputVlanType.PUSH.equals(outputVlanType) || OutputVlanType.NONE.equals(outputVlanType)) {
            actionList.add(actionPushVlan(sw, ETH_TYPE));
        }
        actionList.add(actionReplaceVlan(sw, transitVlanId));
        return actionList;
    }

    /**
     * Create an OFAction which sets the output port.
     *
     * @param sw         switch object
     * @param outputPort port to set in the action
     * @return {@link OFAction}
     */
    private OFAction actionSetOutputPort(final IOFSwitch sw, final int outputPort) {
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildOutput().setMaxLen(0xFFFFFFFF).setPort(OFPort.of(outputPort)).build();
    }

    /**
     * Create an OFAction to change the outer most vlan.
     *
     * @param sw      switch object
     * @param newVlan final VLAN to be set on the packet
     * @return {@link OFAction}
     */
    private OFAction actionReplaceVlan(final IOFSwitch sw, final int newVlan) {
        OFOxms oxms = sw.getOFFactory().oxms();
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildSetField()
                .setField(oxms.buildVlanVid().setValue(OFVlanVidMatch.ofVlan(newVlan)).build()).build();
    }

    /**
     * Create an OFAction to add a VLAN header.
     *
     * @param sw        switch object
     * @param etherType ethernet type of the new VLAN header
     * @return {@link OFAction}
     */
    private OFAction actionPushVlan(final IOFSwitch sw, final int etherType) {
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildPushVlan().setEthertype(EthType.of(etherType)).build();
    }

    /**
     * Create an OFAction to remove the outer most VLAN.
     *
     * @param sw - switch object
     * @return {@link OFAction}
     */
    private OFAction actionPopVlan(final IOFSwitch sw) {
        OFActions actions = sw.getOFFactory().actions();
        return actions.popVlan();
    }

    /**
     * Create an OFFlowMod that can be passed to StaticEntryPusher.
     *
     * @param sw       switch object
     * @param match    match for the flow
     * @param meter    meter for the flow
     * @param actions  actions for the flow
     * @param cookie   cookie for the flow
     * @param priority priority to set on the flow
     * @return {@link OFFlowMod}
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
     * Create a MAC address based on the DPID.
     *
     * @param sw switch object
     * @return {@link MacAddress}
     */
    private MacAddress dpidToMac(final IOFSwitch sw) {
        return MacAddress.of(Arrays.copyOfRange(sw.getId().getBytes(), 2, 8));
    }

    /**
     * Create a match object for the verification packets
     *
     * @param sw          siwtch object
     * @param isBroadcast if broadcast then set a generic match; else specific to switch Id
     * @return {@link Match}
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
     * Create an action to send packet to the controller.
     *
     * @param sw switch object
     * @return {@link OFAction}
     */
    private OFAction actionSendToController(final IOFSwitch sw) {
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildOutput().setMaxLen(0xFFffFFff).setPort(OFPort.CONTROLLER)
                .build();
    }

    /**
     * Create an action to set the DstMac of a packet
     *
     * @param sw         switch object
     * @param macAddress MacAddress to set
     * @return {@link OFAction}
     */
    private OFAction actionSetDstMac(final IOFSwitch sw, final MacAddress macAddress) {
        OFOxms oxms = sw.getOFFactory().oxms();
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildSetField()
                .setField(oxms.buildEthDst().setValue(macAddress).build()).build();
    }

    /**
     * Installs the verification rule
     *
     * @param dpid        datapathId of switch
     * @param isBroadcast if broadcast then set a generic match; else specific to switch Id
     * @return true if the command is accepted to be sent to switch, false otherwise - switch is disconnected or in
     * SLAVE mode
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
        OFFlowMod flowMod = buildFlowMod(sw, match, null, instructionApplyActions,
                cookie, FlowModUtils.PRIORITY_VERY_HIGH);
        String flowname = (isBroadcast) ? "Broadcast" : "Unicast";
        flowname += "--VerificationFlow--" + dpid.toString();
        return pushFlow(flowname, dpid, flowMod);
    }

    /**
     * Installs a last-resort rule to drop all packets that don't match any match.
     *
     * @param dpid datapathId of switch
     * @return true if the command is accepted to be sent to switch, false otherwise - switch is disconnected or in
     * SLAVE mode
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
     * @param flowId  flow name, for logging
     * @param dpid    switch datapath ID
     * @param flowMod command to send
     * @return true if the command is accepted to be sent to switch, false otherwise - switch is disconnected or in
     * SLAVE mode
     */
    private boolean pushFlow(final String flowId, final DatapathId dpid, final OFFlowMod flowMod) {
        logger.info("installing flow: {}", flowId);
        return ofSwitchService.getSwitch(dpid).write(flowMod);
    }
}
