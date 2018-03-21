/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.floodlight.switchmanager;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_BCAST_PACKET_DST;
import static org.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_BCAST_PACKET_DST_MASK;
import static org.openkilda.messaging.Utils.ETH_TYPE;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_12;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_13;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_15;

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
import org.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.openkilda.floodlight.switchmanager.web.SwitchManagerWebRoutable;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.OFMessageListenerProxyWithCorrelationContext;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.payload.flow.OutputVlanType;
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
import org.projectfloodlight.openflow.protocol.match.Match.Builder;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
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
        // TODO: Ensure Kafka Topics are created..
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Starting " + SwitchEventCollector.class.getCanonicalName());
        restApiService.addRestletRoutable(new SwitchManagerWebRoutable());
        floodlightProvider.addOFMessageListener(OFType.ERROR, new OFMessageListenerProxyWithCorrelationContext(this));
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
                    new ErrorData(ErrorType.INTERNAL_ERROR, ((OFErrorMsg) msg).getErrType().toString(), null),
                    System.currentTimeMillis(), CorrelationContext.getId(), Destination.WFM_TRANSACTION);
            // TODO: Most/all commands are flow related, but not all. 'kilda.flow' might
            // not be the best place to send a generic error.
            kafkaProducer.postMessage("kilda.flow", error);
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
    public void installDefaultRules(final DatapathId dpid) throws SwitchOperationException {
        installDropFlow(dpid);
        installVerificationRule(dpid, true);
        IOFSwitch sw = lookupSwitch(dpid);
        if (sw.getOFFactory().getVersion().compareTo(OF_12) > 0) {
            logger.debug("installing unicast verification match for {}", dpid.toString());
            installVerificationRule(dpid, false);
        } else {
            logger.debug("not installing unicast verification match for {}", dpid.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long installIngressFlow(
            final DatapathId dpid, final String flowId,
            final Long cookie, final int inputPort, final int outputPort,
            final int inputVlanId, final int transitVlanId,
            final OutputVlanType outputVlanType, final long meterId) throws SwitchOperationException {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = lookupSwitch(dpid);

        // build match by input port and input vlan id
        Match match = matchFlow(sw, inputPort, inputVlanId);

        // build meter instruction
        OFInstructionMeter meter = null;
        if (meterId != 0L && !OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            if (sw.getOFFactory().getVersion().compareTo(OF_12) <= 0) {
                actionList.add(legacyMeterAction(sw, meterId));
            } else if (sw.getOFFactory().getVersion().compareTo(OF_15) == 0) {
                actionList.add(sw.getOFFactory().actions().buildMeter().setMeterId(meterId).build());
            } else /* OF_13, OF_14 */ {
                meter = sw.getOFFactory().instructions().buildMeter().setMeterId(meterId).build();
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

        return pushFlow(sw, "--InstallIngressFlow--", flowMod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long installEgressFlow(
            final DatapathId dpid, String flowId, final Long cookie,
            final int inputPort, final int outputPort,
            final int transitVlanId, final int outputVlanId,
            final OutputVlanType outputVlanType) throws SwitchOperationException {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = lookupSwitch(dpid);

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

        return pushFlow(sw, "--InstallEgressFlow--", flowMod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long installTransitFlow(
            final DatapathId dpid, final String flowId,
            final Long cookie, final int inputPort, final int outputPort,
            final int transitVlanId) throws SwitchOperationException {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = lookupSwitch(dpid);

        // build match by input port and transit vlan id
        Match match = matchFlow(sw, inputPort, transitVlanId);

        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(sw, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(sw, actionList);

        // build FLOW_MOD command, no meter
        OFFlowMod flowMod = buildFlowMod(sw, match, null, actions,
                cookie & FLOW_COOKIE_MASK, FlowModUtils.PRIORITY_VERY_HIGH);

        return pushFlow(sw, flowId, flowMod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long installOneSwitchFlow(
            final DatapathId dpid, final String flowId,
            final Long cookie, final int inputPort,
            final int outputPort, final int inputVlanId,
            final int outputVlanId,
            final OutputVlanType outputVlanType, final long meterId) throws SwitchOperationException {
        // TODO: As per other locations, how different is this to IngressFlow? Why separate code path?
        //          As with any set of tests, the more we test the same code path, the better.
        //          Based on brief glance, this looks 90% the same as IngressFlow.

        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = lookupSwitch(dpid);

        // build match by input port and transit vlan id
        Match match = matchFlow(sw, inputPort, inputVlanId);

        // build meter instruction
        OFInstructionMeter meter = null;
        if (meterId != 0L && !OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            if (sw.getOFFactory().getVersion().compareTo(OF_12) <= 0) {
                actionList.add(legacyMeterAction(sw, meterId));
            } else if (sw.getOFFactory().getVersion().compareTo(OF_15) == 0) {
                actionList.add(sw.getOFFactory().actions().buildMeter().setMeterId(meterId).build());
            } else /* OF_13, OF_14 */ {
                meter = sw.getOFFactory().instructions().buildMeter().setMeterId(meterId).build();
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

        pushFlow(sw, flowId, flowMod);

        return flowMod.getXid();
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
    public OFMeterConfigStatsReply dumpMeters(final DatapathId dpid) throws SwitchOperationException {
        OFMeterConfigStatsReply values = null;
        IOFSwitch sw = lookupSwitch(dpid);
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
    public long installMeter(final DatapathId dpid, final long bandwidth, final long burstSize, final long meterId)
            throws SwitchOperationException {
        if (meterId == 0) {
            logger.info("skip installing meter {} on switch {} width bandwidth {}",
                    new Object[] {meterId, dpid, bandwidth});
            return 0L;
        }

        IOFSwitch sw = lookupSwitch(dpid);

        if (OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            logger.info("skip installing meter {} on OVS switch {} width bandwidth {}",
                    new Object[] {meterId, dpid, bandwidth});
            return 0L;
        }

        if (sw.getOFFactory().getVersion().compareTo(OF_12) <= 0) {
            return installLegacyMeter(sw, dpid, bandwidth, burstSize, meterId);
        } else {
            return installMeter(sw, dpid, bandwidth, burstSize, meterId);
        }
    }

    @Override
    public Map<DatapathId, IOFSwitch> getAllSwitchMap()
    {
        return ofSwitchService.getAllSwitchMap();
    }


    private long installMeter(final IOFSwitch sw, final DatapathId dpid, final long bandwidth,
                                                     final long burstSize, final long meterId)
            throws OFInstallException {
        logger.debug("installing meter {} on switch {} width bandwidth {}", new Object[] {meterId, dpid, bandwidth});

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

        return pushFlow(sw, "--InstallMeter--", meterMod);
    }

    private long installLegacyMeter(
            final IOFSwitch sw, final DatapathId dpid,
            final long bandwidth, final long burstSize, final long meterId)
            throws OFInstallException {
        logger.debug("installing legacy meter {} on OVS switch {} width bandwidth {}",
                new Object[] {meterId, dpid, bandwidth});

        Set<OFLegacyMeterFlags> flags = new HashSet<>(Arrays.asList(OFLegacyMeterFlags.KBPS, OFLegacyMeterFlags.BURST));
        OFFactory ofFactory = sw.getOFFactory();

        OFLegacyMeterBandDrop.Builder bandBuilder = ofFactory.legacyMeterBandDrop(bandwidth, burstSize).createBuilder();

        OFLegacyMeterMod meterMod = ofFactory.buildLegacyMeterMod()
                .setMeterId(meterId)
                .setCommand(OFLegacyMeterModCommand.ADD)
                .setMeters(singletonList(bandBuilder.build()))
                .setFlags(flags)
                .build();

        return pushFlow(sw, "--InstallMeter", meterMod);
    }

    // Utility Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public long deleteFlow(final DatapathId dpid, final String flowId, final Long cookie)
            throws SwitchOperationException {
        logger.info("deleting flows {} from switch {}", flowId, dpid.toString());

        IOFSwitch sw = lookupSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();
        OFFlowDelete flowDelete = ofFactory.buildFlowDelete()
                .setCookie(U64.of(cookie))
                .setCookieMask(NON_SYSTEM_MASK)
                .build();

        return pushFlow(sw, "--DeleteFlow--", flowDelete);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long deleteMeter(final DatapathId dpid, final long meterId)
            throws SwitchOperationException {
        if (meterId == 0) {
            logger.info("skip deleting meter {} from switch {}", meterId, dpid);
            return 0L;
        }

        IOFSwitch sw = lookupSwitch(dpid);
        if (OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            logger.info("skip deleting meter {} from OVS switch {}", meterId, dpid);
            return 0L;
        }

        if (sw.getOFFactory().getVersion().compareTo(OF_12) <= 0) {
            return deleteLegacyMeter(sw, dpid, meterId);
        } else {
            return deleteMeter(sw, dpid, meterId);
        }
    }

    public long deleteMeter(IOFSwitch sw, final DatapathId dpid, final long meterId)
            throws OFInstallException {
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

        return pushFlow(sw, "--DeleteMeter--", meterDelete);
    }

    public long deleteLegacyMeter(final IOFSwitch sw, final DatapathId dpid, final long meterId)
            throws OFInstallException {
        logger.debug("deleting legacy meter {} from switch {}", meterId, dpid);

        OFFactory ofFactory = sw.getOFFactory();

        OFLegacyMeterMod meterDelete = ofFactory.buildLegacyMeterMod()
                .setMeterId(meterId)
                .setMeters(emptyList())
                .setCommand(OFLegacyMeterModCommand.DELETE)
                .build();

        return pushFlow(sw, "--DeleteMeter--", meterDelete);
    }

    /**
     * Creates a Match based on an inputPort and VlanID.
     * NB1: that this match only matches on the outer most tag which must be of ether-type 0x8100.
     * NB2: vlanId of 0 means match on port, not vlan
     *
     * @param sw        switch object
     * @param inputPort input port for the match
     * @param vlanId    vlanID to match on; 0 means match on port
     * @return {@link Match}
     */
    private Match matchFlow(final IOFSwitch sw, final int inputPort, final int vlanId) {
        Match.Builder mb = sw.getOFFactory().buildMatch();
        //
        // Extra emphasis: vlan of 0 means match on port on not VLAN.
        //
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
        OFFactory factory = sw.getOFFactory();
        OFOxms oxms = factory.oxms();
        OFActions actions = factory.actions();

        if (OF_12.compareTo(factory.getVersion()) == 0) {
            return actions.buildSetField().setField(oxms.buildVlanVid()
                    .setValue(OFVlanVidMatch.ofRawVid((short) newVlan))
                    .build()).build();
        } else {
            return actions.buildSetField().setField(oxms.buildVlanVid()
                    .setValue(OFVlanVidMatch.ofVlan(newVlan))
                    .build()).build();
        }
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
        MacAddress dstMac = isBroadcast ? MacAddress.of(VERIFICATION_BCAST_PACKET_DST) : dpidToMac(sw);
        Builder builder = sw.getOFFactory().buildMatch();
        if (OF_12.compareTo(sw.getOFFactory().getVersion()) == 0) {
            // some old swithes use mask 0x0 by default, so need to setup mask
            // and I can't use mask MacAddress.NO_MASK(0xFFFFFFFFFFFFFFFFl) because of
            // org.projectfloodlight.openflow.protocol.ver12.OFOxmEthDstMaskedVer12.getCanonical
            // see commit 661a2222194454e2c763547cc1b963e3fe4a818c in loxigen
            builder.setMasked(MatchField.ETH_DST, dstMac, MacAddress.of(VERIFICATION_BCAST_PACKET_DST_MASK));
        } else {
            builder.setExact(MatchField.ETH_DST, dstMac);
        }
        return builder.build();
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
     * @throws SwitchOperationException
     */
    private void installVerificationRule(final DatapathId dpid, final boolean isBroadcast)
            throws SwitchOperationException {
        IOFSwitch sw = lookupSwitch(dpid);
        logger.debug("installing verification rule for {}",
                dpid.toString());

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
        pushFlow(sw, flowname, flowMod);
    }

    /**
     * Installs a last-resort rule to drop all packets that don't match any match.
     *
     * @param dpid datapathId of switch
     * @throws SwitchOperationException
     */
    private void installDropFlow(final DatapathId dpid) throws SwitchOperationException {
        IOFSwitch sw = lookupSwitch(dpid);
        OFFlowMod flowMod = buildFlowMod(sw, null, null, null, DROP_COOKIE, 1);
        String flowName = "--DropRule--" + dpid.toString();
        pushFlow(sw, flowName, flowMod);
    }

    /**
     * Pushes a single flow modification command to the switch with the given datapath ID.
     *
     * @param sw      open flow switch descriptor
     * @param flowId  flow name, for logging
     * @param flowMod command to send
     * @return OF transaction Id (???)
     * @throws OFInstallException
     */
    private long pushFlow(final IOFSwitch sw, final String flowId, final OFMessage flowMod) throws OFInstallException {
        logger.info("installing {} flow: {}", flowId, flowMod);

        if (! sw.write(flowMod)) {
            throw new OFInstallException(sw.getId(), flowMod);
        }

        return flowMod.getXid();
    }

    /**
     * Wrap IOFSwitchService.getSwitch call to check protect from null return value.
     *
     * @param  dpId switch identifier
     * @return open flow switch descriptor
     * @throws SwitchOperationException
     */
    private IOFSwitch lookupSwitch(DatapathId dpId) throws SwitchOperationException {
        IOFSwitch swInfo = ofSwitchService.getSwitch(dpId);
        if (swInfo == null) {
            throw new SwitchOperationException(dpId);
        }

        return swInfo;
    }
}
