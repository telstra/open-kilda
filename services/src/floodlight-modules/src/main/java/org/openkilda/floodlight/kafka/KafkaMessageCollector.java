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

package org.openkilda.floodlight.kafka;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.MeterPool;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KafkaMessageCollector implements IFloodlightModule {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageCollector.class);
    private static final String INPUT_TOPIC = "kilda-test";
    private static final String OUTPUT_TOPIC = "kilda-test";
    private final MeterPool meterPool = new MeterPool();
    private Properties kafkaProps;
    private IPathVerificationService pathVerificationService;
    private KafkaMessageProducer kafkaProducer;
    private ISwitchManager switchManager;
    private String zookeeperHosts;

    /**
     * IFloodLightModule Methods
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
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(2);
        services.add(IPathVerificationService.class);
        services.add(ISwitchManager.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        pathVerificationService = context.getServiceImpl(IPathVerificationService.class);
        switchManager = context.getServiceImpl(ISwitchManager.class);
        kafkaProducer = context.getServiceImpl(KafkaMessageProducer.class);
        Map<String, String> configParameters = context.getConfigParams(this);
        kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", configParameters.get("bootstrap-servers"));
        kafkaProps.put("group.id", "kilda-message-collector");
        kafkaProps.put("enable.auto.commit", "true");
        //kafkaProps.put("auto.commit.interval.ms", "1000");
        kafkaProps.put("session.timeout.ms", "30000");
        kafkaProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        kafkaProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        zookeeperHosts = configParameters.get("zookeeper-hosts");
    }

    @Override
    public void startUp(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException {
        logger.info("Starting {}", this.getClass().getCanonicalName());
        try {
            ExecutorService parseRecordExecutor = Executors.newFixedThreadPool(10);
            ExecutorService consumerExecutor = Executors.newSingleThreadExecutor();
            consumerExecutor.execute(new Consumer(
                    Collections.singletonList(INPUT_TOPIC), kafkaProps, parseRecordExecutor));
        } catch (Exception exception) {
            logger.error("error", exception);
        }
    }

    class ParseRecord implements Runnable {
        final ConsumerRecord record;

        public ParseRecord(ConsumerRecord record) {
            this.record = record;
        }

        private void doControllerMsg(CommandMessage message) {
            CommandData data = message.getData();
            if (data instanceof DiscoverIslCommandData) {
                doDiscoverIslCommand(data);
            } else if (data instanceof DiscoverPathCommandData) {
                doDiscoverPathCommand(data);
            } else if (data instanceof InstallIngressFlow) {
                doInstallIngressFlow(message);
            } else if (data instanceof InstallEgressFlow) {
                doInstallEgressFlow(message);
            } else if (data instanceof InstallTransitFlow) {
                doInstallTransitFlow(message);
            } else if (data instanceof InstallOneSwitchFlow) {
                doInstallOneSwitchFlow(message);
            } else if (data instanceof RemoveFlow) {
                doDeleteFlow(message);
            } else {
                logger.error("unknown data type: {}", data.toString());
            }
        }

        private void doDiscoverIslCommand(CommandData data) {
            DiscoverIslCommandData command = (DiscoverIslCommandData) data;
            logger.debug("sending discover ISL to {}", command);

            String switchId = command.getSwitchId();
            boolean result = pathVerificationService.sendDiscoveryMessage(
                    DatapathId.of(switchId), OFPort.of(command.getPortNo()));

            if (result) {
                logger.debug("packet_out was sent to {}", switchId);
            } else {
                logger.warn("packet_out was not sent to {}", switchId);
            }
        }

        private void doDiscoverPathCommand(CommandData data) {
            DiscoverPathCommandData command = (DiscoverPathCommandData) data;
            logger.debug("sending discover Path to {}", command);
        }

        /**
         * Installs ingress flow on the switch.
         *
         * @param message command message for flow installation
         */
        private void doInstallIngressFlow(final CommandMessage message) {
            InstallIngressFlow command = (InstallIngressFlow) message.getData();
            logger.debug("Creating an ingress flow: {}", command);

            int meterId = meterPool.allocate(command.getSwitchId(), command.getId());

            ImmutablePair<Long, Boolean> meterInstalled = switchManager.installMeter(
                    DatapathId.of(command.getSwitchId()),
                    command.getBandwidth(),
                    1024,
                    meterId);

            if (!meterInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(
                        new ErrorData(ErrorType.CREATION_FAILURE, "Could not install meter", command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            }

            ImmutablePair<Long, Boolean> flowInstalled = switchManager.installIngressFlow(
                    DatapathId.of(command.getSwitchId()),
                    command.getId(),
                    command.getCookie(),
                    command.getInputPort(),
                    command.getOutputPort(),
                    command.getInputVlanId(),
                    command.getTransitVlanId(),
                    command.getOutputVlanType(),
                    meterId);

            if (!flowInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(
                        new ErrorData(ErrorType.CREATION_FAILURE, "Could not install ingress flow", command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                message.setDestination(Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }
        }

        /**
         * Installs egress flow on the switch.
         *
         * @param message command message for flow installation
         */
        private void doInstallEgressFlow(final CommandMessage message) {
            InstallEgressFlow command = (InstallEgressFlow) message.getData();
            logger.debug("Creating an egress flow: {}", command);

            ImmutablePair<Long, Boolean> flowInstalled = switchManager.installEgressFlow(
                    DatapathId.of(command.getSwitchId()),
                    command.getId(),
                    command.getCookie(),
                    command.getInputPort(),
                    command.getOutputPort(),
                    command.getTransitVlanId(),
                    command.getOutputVlanId(),
                    command.getOutputVlanType());

            if (!flowInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(
                        new ErrorData(ErrorType.CREATION_FAILURE, "Could not install egress flow", command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                message.setDestination(Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }
        }

        /**
         * Installs transit flow on the switch.
         *
         * @param message command message for flow installation
         */
        private void doInstallTransitFlow(final CommandMessage message) {
            InstallTransitFlow command = (InstallTransitFlow) message.getData();
            logger.debug("Creating a transit flow: {}", command);

            ImmutablePair<Long, Boolean> flowInstalled = switchManager.installTransitFlow(
                    DatapathId.of(command.getSwitchId()),
                    command.getId(),
                    command.getCookie(),
                    command.getInputPort(),
                    command.getOutputPort(),
                    command.getTransitVlanId());

            if (!flowInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(
                        new ErrorData(ErrorType.CREATION_FAILURE, "Could not install transit flow", command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                message.setDestination(Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }
        }

        /**
         * Installs flow through one switch.
         *
         * @param message command message for flow installation
         */
        private void doInstallOneSwitchFlow(final CommandMessage message) {
            InstallOneSwitchFlow command = (InstallOneSwitchFlow) message.getData();
            logger.debug("creating a flow through one switch: {}", command);

            int meterId = meterPool.allocate(command.getSwitchId(), command.getId());

            ImmutablePair<Long, Boolean> meterInstalled = switchManager.installMeter(
                    DatapathId.of(command.getSwitchId()),
                    command.getBandwidth(),
                    1024,
                    meterId);

            if (!meterInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(
                        new ErrorData(ErrorType.CREATION_FAILURE, "Could not install meter", command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            }

            OutputVlanType directOutputVlanType = command.getOutputVlanType();
            ImmutablePair<Long, Boolean> forwardFlowInstalled = switchManager.installOneSwitchFlow(
                    DatapathId.of(command.getSwitchId()),
                    command.getId(),
                    command.getCookie(),
                    command.getInputPort(),
                    command.getOutputPort(),
                    command.getInputVlanId(),
                    command.getOutputVlanId(),
                    directOutputVlanType,
                    meterId);

            if (!forwardFlowInstalled.getRight()) {
                ErrorMessage error = new ErrorMessage(
                        new ErrorData(ErrorType.CREATION_FAILURE, "Could not install flow", command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                message.setDestination(Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }
        }

        /**
         * Removes flow.
         *
         * @param message command message for flow installation
         */
        private void doDeleteFlow(final CommandMessage message) {
            RemoveFlow command = (RemoveFlow) message.getData();
            logger.debug("deleting a flow: {}", command);

            DatapathId dpid = DatapathId.of(command.getSwitchId());
            ImmutablePair<Long, Boolean> flowDeleted = switchManager.deleteFlow(
                    dpid, command.getId(), command.getCookie());

            if (!flowDeleted.getRight()) {
                ErrorMessage error = new ErrorMessage(
                        new ErrorData(ErrorType.DELETION_FAILURE, "Could not delete flow", command.getId()),
                        System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, error);
            } else {
                message.setDestination(Destination.WFM_TRANSACTION);
                kafkaProducer.postMessage(OUTPUT_TOPIC, message);
            }

            Integer meterId = meterPool.deallocate(command.getSwitchId(), command.getId());

            if (flowDeleted.getRight() && meterId != null) {
                ImmutablePair<Long, Boolean> meterDeleted = switchManager.deleteMeter(dpid, meterId);
                if (!meterDeleted.getRight()) {
                    ErrorMessage error = new ErrorMessage(
                            new ErrorData(ErrorType.DELETION_FAILURE, "Could not delete meter", command.getId()),
                            System.currentTimeMillis(), message.getCorrelationId(), Destination.WFM_TRANSACTION);
                    kafkaProducer.postMessage(OUTPUT_TOPIC, error);
                }
            }
        }

        private void parseRecord(ConsumerRecord record) {
            try {
                if (record.value() instanceof String) {
                    String value = (String) record.value();
                    Message message = MAPPER.readValue(value, Message.class);
                    if (Destination.CONTROLLER.equals(message.getDestination()) && message instanceof CommandMessage) {
                        logger.debug("Got a command message for controller: {}", value);
                        doControllerMsg((CommandMessage) message);
                    } else {
                        logger.trace("Skip message: {}", message);
                    }
                } else {
                    logger.error("{} not of type String", record.value());
                }
            } catch (Exception exception) {
                logger.error("error parsing record={}", record.value(), exception);
            }
        }

        @Override
        public void run() {
            parseRecord(record);
        }
    }

    class Consumer implements Runnable {
        final List<String> topics;
        final Properties kafkaProps;
        final ExecutorService parseRecordExecutor;

        public Consumer(List<String> topics, Properties kafkaProps, ExecutorService parseRecordExecutor) {
            this.topics = topics;
            this.kafkaProps = kafkaProps;
            this.parseRecordExecutor = parseRecordExecutor;
        }

        @Override
        public void run() {
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps);
            consumer.subscribe(topics);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(100);
                for (ConsumerRecord<String, String> record : records) {
                    logger.trace("received message: {} - {}", record.offset(), record.value());
                    parseRecordExecutor.execute(new ParseRecord(record));
                }
            }
        }
    }
}
