package org.bitbucket.openkilda.floodlight.switchmanager;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import net.floodlightcontroller.util.FlowModUtils;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bitbucket.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.bitbucket.openkilda.floodlight.message.InfoMessage;
import org.bitbucket.openkilda.floodlight.message.Message;
import org.bitbucket.openkilda.floodlight.message.info.InfoData;
import org.bitbucket.openkilda.floodlight.message.info.PortInfoData;
import org.bitbucket.openkilda.floodlight.message.info.SwitchInfoData;
import org.bitbucket.openkilda.floodlight.message.info.SwitchInfoData.SwitchEventType;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_BCAST_PACKET_DST;
import static org.bitbucket.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_PACKET_UDP_PORT;

public class SwitchEventCollector implements IFloodlightModule, IOFSwitchListener {

    private IOFSwitchService switchService;
    private Logger logger;
    private Properties kafkaProps;
    private String topic;
    private KafkaMessageProducer kafkaProducer;
    private IStaticEntryPusherService sfpService;

    /**
     * IOFSwitchListener methods
     */

    @Override
    public void switchAdded(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.ADDED);
        postMessage(topic, message);
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.REMOVED);
        postMessage(topic, message);
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.ACTIVATED);
        installDefaultRules(switchId);
        postMessage(topic, message);

        IOFSwitch sw = switchService.getSwitch(switchId);
        if (sw.getEnabledPortNumbers() != null) {
            for (OFPort p : sw.getEnabledPortNumbers()) {
                postMessage(topic, buildPortMessage(sw.getId(), p, PortChangeType.UP));
            }
        }
    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        Message message = buildPortMessage(switchId, port, type);
        postMessage(topic, message);
    }

    @Override
    public void switchChanged(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.CHANGED);
        postMessage(topic, message);
    }

    @Override
    public void switchDeactivated(DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchEventType.DEACTIVATED);
        postMessage(topic, message);
    }

    /**
     * IFloodlightModule methods
     */

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        services.add(IFloodlightProviderService.class);
        services.add(IOFSwitchService.class);
        services.add(KafkaMessageProducer.class);
        services.add(IStaticEntryPusherService.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        IFloodlightProviderService floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        kafkaProducer = context.getServiceImpl(KafkaMessageProducer.class);
        sfpService = context.getServiceImpl(IStaticEntryPusherService.class);
        logger = LoggerFactory.getLogger(SwitchEventCollector.class);

        Map<String, String> configParameters = context.getConfigParams(this);
        kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", configParameters.get("bootstrap-servers"));
        kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        topic = configParameters.get("topic");
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Starting " + SwitchEventCollector.class.getCanonicalName());
        switchService.addOFSwitchListener(this);
    }

    /**
     * Utility functions
     */

    private Message buildSwitchMessage(DatapathId dpid, SwitchEventType eventType) {
        InfoData data = new SwitchInfoData()
                .withSwitchId(dpid.toString())
                .withState(eventType);
        return buildMessage(data);
    }

    private Message buildMessage(InfoData data) {
        return new InfoMessage()
                .withData(data)
                .withTimestamp(System.currentTimeMillis());
    }

    private Message buildPortMessage(DatapathId switchId, OFPort port, PortChangeType type) {
        InfoData data = new PortInfoData()
                .withSwitchId(switchId.toString())
                .withPortNo(port.getPortNumber())
                .withState(type);
        return(buildMessage(data));
    }

    private Message buildPortMessage(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        InfoData data = new PortInfoData()
                .withSwitchId(switchId.toString())
                .withPortNo(port.getPortNo().getPortNumber())
                .withState(type);
        return (buildMessage(data));
    }

    private void postMessage(String topic, Message message) {
        try {
            kafkaProducer.send(new ProducerRecord<String, String>(topic, message.toJson()));
        } catch (JsonProcessingException e) {
            logger.error("error", e);
        }
    }

    private void installDropFlow(DatapathId switchId) {
        IOFSwitch sw = switchService.getSwitch(switchId);
        OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
        fmb.setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT);
        fmb.setHardTimeout(FlowModUtils.INFINITE_TIMEOUT);
        fmb.setBufferId(OFBufferId.NO_BUFFER);
        fmb.setCookie(U64.of(1L));
        fmb.setPriority(FlowModUtils.PRIORITY_MIN);
        OFFlowMod flowMod  = fmb.build();

        logger.debug("Adding drop flow to {}.", switchId);
        String flowname = "--DropRule--" + switchId.toString();
        sfpService.addFlow(flowname, flowMod, switchId);
    }

    public MacAddress dpidToMac(IOFSwitch sw) {
        return MacAddress.of(Arrays.copyOfRange(sw.getId().getBytes(), 2, 8));
    }

    protected Match buildVerificationMatch(IOFSwitch sw, boolean isBroadcast) {
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

    protected List<OFAction> buildSendToControllerAction(IOFSwitch sw) {
        ArrayList<OFAction> actionList = new ArrayList<>();
        OFActions actions = sw.getOFFactory().actions();
        OFActionOutput output = actions.buildOutput().setMaxLen(0xFFffFFff).setPort(OFPort.CONTROLLER)
                .build();
        actionList.add(output);

        // Set Destination MAC to own DPID
        OFOxms oxms = sw.getOFFactory().oxms();
        OFActionSetField dstMac = actions.buildSetField()
                .setField(oxms.buildEthDst().setValue(dpidToMac(sw)).build()).build();
        actionList.add(dstMac);
        return actionList;
    }

    protected OFFlowMod buildFlowMod(IOFSwitch sw, Match match, List<OFAction> actionList) {
        OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
        fmb.setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT);
        fmb.setHardTimeout(FlowModUtils.INFINITE_TIMEOUT);
        fmb.setBufferId(OFBufferId.NO_BUFFER);
        fmb.setCookie(U64.of(123L));
        fmb.setPriority(FlowModUtils.PRIORITY_VERY_HIGH);
        fmb.setActions(actionList);
        fmb.setMatch(match);
        return fmb.build();
    }

    public void installVerificationRule(DatapathId switchId, boolean isBroadcast) {
        IOFSwitch sw = switchService.getSwitch(switchId);

        Match match = buildVerificationMatch(sw, isBroadcast);
        ArrayList<OFAction> actionList = (ArrayList<OFAction>) buildSendToControllerAction(sw);
        OFFlowMod flowMod = buildFlowMod(sw, match, actionList);

        logger.debug("Adding verification flow to {}.", switchId);
        String flowname = (isBroadcast) ? "Broadcast" : "Unicast";
        flowname += "--VerificationFlow--" + switchId.toString();
        logger.debug("adding: " + flowname + " " + flowMod.toString() + "--" + switchId.toString());
        sfpService.addFlow(flowname, flowMod, switchId);
    }

    public void installDefaultRules(DatapathId switchId) {
        installDropFlow(switchId);
        installVerificationRule(switchId, true);
        installVerificationRule(switchId, false);
    }
}
