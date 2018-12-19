/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.statistics;

import static java.util.stream.Collectors.toList;

import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.converter.OfMeterStatsConverter;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.FlowStatsReply;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.messaging.info.stats.PortStatsReply;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterStatsRequest;
import org.projectfloodlight.openflow.protocol.OFPortStatsProp;
import org.projectfloodlight.openflow.protocol.OFPortStatsPropEthernet;
import org.projectfloodlight.openflow.protocol.OFPortStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * This service performs periodic port/flow/meter config statistics collection and pushes it to Kafka.
 */
public class StatisticsService implements IStatisticsService, IFloodlightModule {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsService.class);
    private static final U64 SYSTEM_MASK = U64.of(0x8000000000000000L);
    private static final long OFPM_ALL = 0xffffffffL;

    private IOFSwitchService switchService;
    private IKafkaProducerService producerService;
    private IThreadPoolService threadPoolService;
    private int interval;
    private String statisticsTopic;

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return Collections.singletonList(IStatisticsService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return Collections.singletonMap(IStatisticsService.class, this);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return ImmutableList.of(
                IFloodlightProviderService.class,
                IOFSwitchService.class,
                IThreadPoolService.class,
                KafkaUtilityService.class,
                IKafkaProducerService.class);
    }

    @Override
    public void init(FloodlightModuleContext context) {
        switchService = context.getServiceImpl(IOFSwitchService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
        producerService = context.getServiceImpl(IKafkaProducerService.class);

        FloodlightModuleConfigurationProvider provider = FloodlightModuleConfigurationProvider.of(context, this);
        StatisticsServiceConfig serviceConfig = provider.getConfiguration(StatisticsServiceConfig.class);
        interval = serviceConfig.getInterval();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        statisticsTopic = context.getServiceImpl(KafkaUtilityService.class).getTopics().getStatsTopic();

        if (interval > 0) {
            threadPoolService.getScheduledExecutor().scheduleAtFixedRate(
                    () -> switchService.getAllSwitchMap().values().forEach(iofSwitch -> {
                        gatherPortStats(iofSwitch);
                        gatherFlowStats(iofSwitch);
                        gatherMeterStats(iofSwitch);
                    }), interval, interval, TimeUnit.SECONDS);
        }
    }

    @NewCorrelationContextRequired
    private void gatherPortStats(IOFSwitch iofSwitch) {
        OFFactory factory = iofSwitch.getOFFactory();
        SwitchId switchId = new SwitchId(iofSwitch.getId().toString());

        OFPortStatsRequest portStatsRequest = factory
                .buildPortStatsRequest()
                .setPortNo(OFPort.ANY)
                .build();

        logger.trace("Getting port stats for switch={}", iofSwitch.getId());

        Futures.addCallback(iofSwitch.writeStatsRequest(portStatsRequest),
                new RequestCallback<>(data -> {
                    List<PortStatsReply> replies = data.stream().map(reply -> {
                        List<PortStatsEntry> entries = reply.getEntries().stream()
                                .map(entry -> {
                                    if (entry.getVersion().compareTo(OFVersion.OF_13) > 0) {
                                        long rxFrameErr = 0L;
                                        long rxOverErr = 0L;
                                        long rxCrcErr = 0L;
                                        long collisions = 0L;

                                        for (OFPortStatsProp property : entry.getProperties()) {
                                            if (property.getType() == 0x0) {
                                                OFPortStatsPropEthernet etherProps =
                                                        (OFPortStatsPropEthernet) property;
                                                rxFrameErr = etherProps.getRxFrameErr().getValue();
                                                rxOverErr = etherProps.getRxOverErr().getValue();
                                                rxCrcErr = etherProps.getRxCrcErr().getValue();
                                                collisions = etherProps.getCollisions().getValue();
                                            }
                                        }

                                        return new PortStatsEntry(
                                                entry.getPortNo().getPortNumber(),
                                                entry.getRxPackets().getValue(),
                                                entry.getTxPackets().getValue(),
                                                entry.getRxBytes().getValue(),
                                                entry.getTxBytes().getValue(),
                                                entry.getRxDropped().getValue(),
                                                entry.getTxDropped().getValue(),
                                                entry.getRxErrors().getValue(),
                                                entry.getTxErrors().getValue(),
                                                rxFrameErr,
                                                rxOverErr,
                                                rxCrcErr,
                                                collisions);
                                    } else {
                                        return new PortStatsEntry(
                                                entry.getPortNo().getPortNumber(),
                                                entry.getRxPackets().getValue(),
                                                entry.getTxPackets().getValue(),
                                                entry.getRxBytes().getValue(),
                                                entry.getTxBytes().getValue(),
                                                entry.getRxDropped().getValue(),
                                                entry.getTxDropped().getValue(),
                                                entry.getRxErrors().getValue(),
                                                entry.getTxErrors().getValue(),
                                                entry.getRxFrameErr().getValue(),
                                                entry.getRxOverErr().getValue(),
                                                entry.getRxCrcErr().getValue(),
                                                entry.getCollisions().getValue());
                                    }
                                })
                                .collect(toList());
                        return new PortStatsReply(reply.getXid(), entries);
                    }).collect(toList());
                    return new PortStatsData(switchId, replies);
                }, "port", CorrelationContext.getId()));
    }

    @NewCorrelationContextRequired
    private void gatherFlowStats(IOFSwitch iofSwitch) {
        OFFactory factory = iofSwitch.getOFFactory();
        final SwitchId switchId = new SwitchId(iofSwitch.getId().toString());

        OFFlowStatsRequest flowStatsRequest = factory
                .buildFlowStatsRequest()
                .setOutGroup(OFGroup.ANY)
                .setCookieMask(SYSTEM_MASK)
                .build();

        if (factory.getVersion().compareTo(OFVersion.OF_15) != 0) {
            // skip flow stats for OF 1.5 protocol version
            logger.trace("Getting flow stats for switch={}", iofSwitch.getId());

            Futures.addCallback(iofSwitch.writeStatsRequest(flowStatsRequest),
                    new RequestCallback<>(data -> {
                        List<FlowStatsReply> replies = data.stream().map(reply -> {
                            List<FlowStatsEntry> entries = reply.getEntries().stream()
                                    .map(entry -> new FlowStatsEntry(
                                            entry.getTableId().getValue(),
                                            entry.getCookie().getValue(),
                                            entry.getPacketCount().getValue(),
                                            entry.getByteCount().getValue()))
                                    .collect(toList());
                            return new FlowStatsReply(reply.getXid(), entries);
                        }).collect(toList());
                        return new FlowStatsData(switchId, replies);
                    }, "flow", CorrelationContext.getId()));
        }
    }

    @NewCorrelationContextRequired
    private void gatherMeterStats(IOFSwitch iofSwitch) {
        OFFactory factory = iofSwitch.getOFFactory();
        SwitchId switchId = new SwitchId(iofSwitch.getId().toString());

        OFMeterStatsRequest meterStatsRequest = factory
                .buildMeterStatsRequest()
                .setMeterId(OFPM_ALL)
                .build();

        logger.trace("Getting meter stats for switch={}", iofSwitch.getId());

        Futures.addCallback(iofSwitch.writeStatsRequest(meterStatsRequest),
                new RequestCallback<>(data -> OfMeterStatsConverter.toMeterStatsData(data, switchId),
                        "meter", CorrelationContext.getId()));
    }

    private class RequestCallback<T extends OFStatsReply> implements FutureCallback<List<T>> {
        private Function<List<T>, InfoData> transform;
        private String type;
        private final String correlationId;

        RequestCallback(Function<List<T>, InfoData> transform, String type, String correlationId) {
            this.transform = transform;
            this.type = type;
            this.correlationId = correlationId;
        }

        @Override
        public void onSuccess(List<T> data) {
            // Restore the correlation context used for the request.
            try (CorrelationContextClosable closable = CorrelationContext.create(correlationId)) {

                InfoMessage infoMessage = new InfoMessage(transform.apply(data),
                        System.currentTimeMillis(), correlationId, Destination.WFM_STATS);
                producerService.sendMessageAndTrack(statisticsTopic, infoMessage);
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            // Restore the correlation context used for the request.
            try (CorrelationContextClosable closable = CorrelationContext.create(correlationId)) {

                logger.error("Exception reading {} stats", type, throwable);
            }
        }
    }
}
