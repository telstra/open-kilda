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

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.converter.IofSwitchConverter;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.TableFeatures;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.apache.commons.lang3.StringUtils;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropExperimenter;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropExperimenterMiss;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class SwitchTrackingService implements IOFSwitchListener, IService {
    private static final Logger logger = LoggerFactory.getLogger(SwitchTrackingService.class);

    private final ReadWriteLock discoveryLock = new ReentrantReadWriteLock();

    private IKafkaProducerService producerService;
    private ISwitchManager switchManager;
    private FeatureDetectorService featureDetector;

    private String discoveryTopic;
    private String region;

    /**
     * Send dump contain all connected at this moment switches.
     */
    public void dumpAllSwitches(String correlationId) {
        discoveryLock.writeLock().lock();
        try {
            dumpAllSwitchesAction(correlationId);
        } finally {
            discoveryLock.writeLock().unlock();
        }
    }

    /**
     * Desired to be used on the end of switch activation to send discovery messages.
     */
    public void completeSwitchActivation(DatapathId dpId) {
        discoveryLock.readLock().lock();
        try {
            switchDiscoveryAction(dpId, SwitchChangeType.ACTIVATED);
        } finally {
            discoveryLock.readLock().unlock();
        }

        try {
            IOFSwitch sw = switchManager.lookupSwitch(dpId);

            dumpSwitchDetails(sw);
        } catch (SwitchNotFoundException e) {
            logger.error("Lost connection with {} during activation phase", dpId);
        }
    }

    @Override
    @NewCorrelationContextRequired
    public void switchAdded(final DatapathId switchId) {
        logSwitchEvent(switchId, SwitchChangeType.ADDED);
        switchDiscovery(switchId, SwitchChangeType.ADDED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchRemoved(final DatapathId switchId) {
        logSwitchEvent(switchId, SwitchChangeType.REMOVED);

        // TODO(surabujin): must figure out events order/set during lost connection
        switchManager.deactivate(switchId);
        switchDiscovery(switchId, SwitchChangeType.REMOVED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchActivated(final DatapathId switchId) {
        logSwitchEvent(switchId, SwitchChangeType.ACTIVATED);

        try {
            switchManager.activate(switchId);
        } catch (SwitchOperationException e) {
            logger.error("OF switch event ({} - {}): {}", switchId, SwitchChangeType.ACTIVATED, e.getMessage());
        }
    }

    @Override
    @NewCorrelationContextRequired
    public void switchDeactivated(final DatapathId switchId) {
        logSwitchEvent(switchId, SwitchChangeType.DEACTIVATED);

        switchManager.deactivate(switchId);
        switchDiscovery(switchId, SwitchChangeType.DEACTIVATED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchChanged(final DatapathId switchId) {
        logSwitchEvent(switchId, SwitchChangeType.CHANGED);
        switchDiscovery(switchId, SwitchChangeType.CHANGED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchPortChanged(final DatapathId switchId, final OFPortDesc portDesc, final PortChangeType type) {
        logPortEvent(switchId, portDesc, type);

        if (ISwitchManager.isPhysicalPort(portDesc)) {
            portDiscovery(switchId, portDesc.getPortNo(), type);
        }
    }

    @Override
    public void setup(FloodlightModuleContext context) {
        producerService = context.getServiceImpl(IKafkaProducerService.class);
        switchManager = context.getServiceImpl(ISwitchManager.class);
        featureDetector = context.getServiceImpl(FeatureDetectorService.class);
        KafkaChannel kafkaChannel = context.getServiceImpl(KafkaUtilityService.class).getKafkaChannel();
        discoveryTopic = kafkaChannel.getTopoDiscoTopic();
        region = kafkaChannel.getRegion();

        if (switchManager.isTrackingEnabled()) {
            context.getServiceImpl(IOFSwitchService.class).addOFSwitchListener(this);
        }
    }

    private void dumpAllSwitchesAction(String correlationId) {
        producerService.enableGuaranteedOrder(discoveryTopic);
        try {
            Collection<IOFSwitch> iofSwitches = switchManager.getAllSwitchMap().values();
            int switchCounter = 0;
            for (IOFSwitch sw : iofSwitches) {
                try {
                    NetworkDumpSwitchData swData = new NetworkDumpSwitchData(buildSwitch(sw));
                    producerService.sendMessageAndTrack(discoveryTopic,
                                                    correlationId,
                                                    new ChunkedInfoMessage(swData, System.currentTimeMillis(),
                                                            correlationId, switchCounter, iofSwitches.size(), region));
                } catch (Exception e) {
                    logger.error("Failed to send network dump for {}", sw.getId());
                }
                switchCounter++;
            }

        } finally {
            producerService.disableGuaranteedOrder(discoveryTopic);
        }
    }

    private void switchDiscovery(DatapathId dpId, SwitchChangeType state) {
        discoveryLock.readLock().lock();
        try {
            switchDiscoveryAction(dpId, state);
        } finally {
            discoveryLock.readLock().unlock();
        }
    }

    private void portDiscovery(DatapathId dpId, OFPort port, PortChangeType changeType) {
        discoveryLock.readLock().lock();
        try {
            portDiscoveryAction(dpId, port, changeType);
        } finally {
            discoveryLock.readLock().unlock();
        }
    }

    private void switchDiscoveryAction(DatapathId dpId, SwitchChangeType event) {
        logger.info("Send switch discovery ({} - {})", dpId, event);
        Message message = null;
        if (SwitchChangeType.DEACTIVATED != event && SwitchChangeType.REMOVED != event) {
            try {
                IOFSwitch sw = switchManager.lookupSwitch(dpId);
                SpeakerSwitchView switchView = buildSwitch(sw);
                message = buildSwitchMessage(sw, switchView, event);
            } catch (SwitchNotFoundException e) {
                logger.error(
                        "Switch {} is not in management state now({}), switch ISL discovery details will be degraded.",
                        dpId, e.getMessage());
            }
        }
        if (message == null) {
            message = buildSwitchMessage(dpId, event);
        }

        producerService.sendMessageAndTrack(discoveryTopic, dpId.toString(), message);
    }

    private void portDiscoveryAction(DatapathId dpId, OFPort port, PortChangeType changeType) {
        logger.info("Send port discovery ({}-{} - {})", dpId, port, changeType);
        Message message = buildPortMessage(dpId, port, changeType);
        producerService.sendMessageAndTrack(discoveryTopic, dpId.toString(), message);
    }

    private Boolean obtainPortEnableStatus(DatapathId dpId, OFPort port, PortChangeType changeType) {
        Boolean result = null;
        switch (changeType) {
            case UP:
                result = true;
                break;
            case DOWN:
                result = false;
                break;
            case ADD:
                result = obtainPortEnableStatus(dpId, port);
                break;
            case DELETE:
                break;
            default:
                logger.error("Unable to obtain port-enable-status {}_{} - change-type:{} is not supported",
                        dpId, port.getPortNumber(), changeType);
        }

        return result;
    }

    private Boolean obtainPortEnableStatus(DatapathId dpId, OFPort port) {
        try {
            IOFSwitch sw = switchManager.lookupSwitch(dpId);
            return sw.portEnabled(port);
        } catch (SwitchNotFoundException e) {
            logger.error("Unable to obtain port-enable-status {}_{}: {}", dpId, port.getPortNumber(), e.getMessage());
            return null;
        }
    }

    private void dumpSwitchDetails(IOFSwitch sw) {
        logSwitch(sw.getId(), String.format("OF version %s", sw.getOFFactory().getVersion()));
        for (TableId tableId : sw.getTables()) {
            dumpSwitchTableDetails(sw, tableId);
        }
    }

    private void dumpSwitchTableDetails(IOFSwitch sw, TableId tableId) {
        TableFeatures features = sw.getTableFeatures(tableId);
        sw.getOFFactory();

        logSwitchTableFeature(sw, tableId, "name", features.getTableName());
        logSwitchTableFeature(sw, tableId, "max-entries", String.valueOf(features.getMaxEntries()));
        logSwitchTableFeature(sw, tableId, "matadata-match-bits", features.getMetadataMatch().toString());
        logSwitchTableFeature(sw, tableId, "metadata-write-bits", features.getMetadataWrite().toString());

        logSwitchTableFeature(sw, tableId, "instructions",
                              features.getPropInstructions().getInstructionIds());
        logSwitchTableFeature(sw, tableId, "instruction (missing)s",
                              features.getPropInstructionsMiss().getInstructionIds());

        logSwitchTableFeature(sw, tableId, "match", features.getPropMatch().getOxmIds());

        logSwitchTableFeature(sw, tableId, "apply-action",
                              features.getPropApplyActions().getActionIds());
        logSwitchTableFeature(sw, tableId, "apply-action (missing)",
                              features.getPropApplyActionsMiss().getActionIds());

        logSwitchTableFeature(sw, tableId, "apply-set-field", features.getPropApplySetField().getOxmIds());
        logSwitchTableFeature(sw, tableId, "apply-set-field", features.getPropApplySetFieldMiss().getOxmIds());

        logSwitchTableFeature(sw, tableId, "write-action",
                              features.getPropWriteActions().getActionIds());
        logSwitchTableFeature(sw, tableId, "write-action (miss)",
                              features.getPropWriteActionsMiss().getActionIds());

        logSwitchTableFeature(sw, tableId, "write-set-field", features.getPropWriteSetField().getOxmIds());
        logSwitchTableFeature(sw, tableId, "write-set-field (miss)", features.getPropWriteSetFieldMiss().getOxmIds());

        logSwitchTableFeature(sw, tableId, "next-table", features.getPropNextTables().getNextTableIds());
        logSwitchTableFeature(sw, tableId, "next-table (miss)", features.getPropNextTablesMiss().getNextTableIds());

        logSwitchTableFeature(sw, tableId, "wildcard", features.getPropWildcards().getOxmIds());

        OFTableFeaturePropExperimenter e = features.getPropExperimenter();
        logSwitchTableFeature(sw, tableId, "experimenter", String.format(
                "id=%d, subtype=%d, payload %d bytes",
                e.getExperimenter(), e.getSubtype(), e.getExperimenterData().length));
        OFTableFeaturePropExperimenterMiss ee = features.getPropExperimenterMiss();
        logSwitchTableFeature(sw, tableId, "experimenter (miss)", String.format(
                "id=%d, subtype=%d, payload %d bytes",
                ee.getExperimenter(), ee.getSubtype(), ee.getExperimenterData().length));
    }

    private static org.openkilda.messaging.info.event.PortChangeType mapChangeType(PortChangeType type) {
        switch (type) {
            case ADD:
                return org.openkilda.messaging.info.event.PortChangeType.ADD;
            case OTHER_UPDATE:
                return org.openkilda.messaging.info.event.PortChangeType.OTHER_UPDATE;
            case DELETE:
                return org.openkilda.messaging.info.event.PortChangeType.DELETE;
            case UP:
                return org.openkilda.messaging.info.event.PortChangeType.UP;
            default:
                return org.openkilda.messaging.info.event.PortChangeType.DOWN;
        }
    }

    /**
     * Builds fully filled switch ISL discovery message.
     *
     * @param sw switch instance
     * @param eventType type of event
     * @return Message
     */
    private Message buildSwitchMessage(IOFSwitch sw, SpeakerSwitchView switchView, SwitchChangeType eventType) {
        return buildMessage(IofSwitchConverter.buildSwitchInfoData(sw, switchView, eventType));
    }

    /**
     * Builds degraded switch ISL discovery message.
     *
     * @param dpId switch datapath
     * @param eventType type of event
     * @return Message
     */
    private Message buildSwitchMessage(DatapathId dpId, SwitchChangeType eventType) {
        return buildMessage(new SwitchInfoData(new SwitchId(dpId.getLong()), eventType));
    }

    /**
     * Builds a port state change message with port number.
     *
     * @param switchId datapathId of switch
     * @param port port that triggered the event
     * @param type type of port event
     * @return Message
     */
    private Message buildPortMessage(final DatapathId switchId, final OFPort port, final PortChangeType type) {
        InfoData data = new PortInfoData(
                new SwitchId(switchId.getLong()), port.getPortNumber(), mapChangeType(type),
                obtainPortEnableStatus(switchId, port, type));
        return buildMessage(data);
    }

    /**
     * Builds a generic message object.
     *
     * @param data data to use in the message body
     * @return Message
     */
    private Message buildMessage(final InfoData data) {
        return new InfoMessage(data, System.currentTimeMillis(), CorrelationContext.getId(), null, region);
    }

    private SpeakerSwitchView buildSwitch(IOFSwitch sw) {
        SwitchDescription ofDescription = sw.getSwitchDescription();
        SpeakerSwitchDescription description = SpeakerSwitchDescription.builder()
                .manufacturer(ofDescription.getManufacturerDescription())
                .hardware(ofDescription.getHardwareDescription())
                .software(ofDescription.getSoftwareDescription())
                .serialNumber(ofDescription.getSerialNumber())
                .datapath(ofDescription.getDatapathDescription())
                .build();
        Set<SpeakerSwitchView.Feature> features = featureDetector.detectSwitch(sw);
        List<SpeakerSwitchPortView> ports = switchManager.getPhysicalPorts(sw).stream()
                .map(port -> new SpeakerSwitchPortView(
                        port.getPortNo().getPortNumber(),
                        port.isEnabled()
                                ? SpeakerSwitchPortView.State.UP
                                : SpeakerSwitchPortView.State.DOWN))
                .collect(Collectors.toList());
        return new SpeakerSwitchView(new SwitchId(sw.getId().getLong()),
                                     (InetSocketAddress) sw.getInetAddress(),
                                     (InetSocketAddress) (sw.getConnectionByCategory(
                                                     LogicalOFMessageCategory.MAIN).getRemoteInetAddress()),
                                     sw.getOFFactory().getVersion().toString(),
                                     description, features, ports);
    }

    private void logSwitchEvent(DatapathId dpId, SwitchChangeType event) {
        logger.info("OF switch event ({} - {})", dpId, event);
    }

    private void logPortEvent(DatapathId dpId, OFPortDesc portDesc, PortChangeType changeType) {
        logger.info("OF port event ({}-{} - {}). PortDesc: {}", dpId, portDesc.getPortNo(), changeType, portDesc);
    }

    private void logSwitchTableFeature(IOFSwitch sw, TableId tableId, String kind, Iterable<?> nameSequence) {
        logSwitchTableFeature(sw, tableId, kind, StringUtils.join(nameSequence, ","));
    }

    private void logSwitchTableFeature(IOFSwitch sw, TableId tableId, String kind, String message) {
        logSwitchTable(sw.getId(), tableId, String.format("features - %s: %s", kind, message));
    }

    private void logSwitchTable(DatapathId dpId, TableId tableId, String message) {
        logSwitch(dpId, String.format("table %s %s", tableId, message));
    }

    private void logSwitch(DatapathId dpid, String message) {
        logger.info("switch {} {}", dpid, message);
    }
}
