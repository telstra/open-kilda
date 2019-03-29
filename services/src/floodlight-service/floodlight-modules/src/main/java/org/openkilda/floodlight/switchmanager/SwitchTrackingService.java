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
import org.openkilda.floodlight.utils.FloodlightDashboardLogger;
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
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class SwitchTrackingService implements IOFSwitchListener, IService {
    private static final Logger logger = LoggerFactory.getLogger(SwitchTrackingService.class);

    private static final FloodlightDashboardLogger logWrapper = new FloodlightDashboardLogger(logger);

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
            logWrapper.onSwitchErrorEvent(Level.ERROR, e.getMessage(), switchId, SwitchChangeType.ACTIVATED);
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
        OFPort port = portDesc.getPortNo();
        logPortEvent(switchId, port, type);

        if (ISwitchManager.isPhysicalPort(portDesc)) {
            portDiscovery(switchId, port, type);
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
        logWrapper.onSwitchEvent(Level.INFO, dpId, event);
    }

    private void logPortEvent(DatapathId dpId, OFPort port, PortChangeType changeType) {
        logWrapper.onPortEvent(Level.INFO, dpId, port.getPortNumber(), changeType);
    }
}
