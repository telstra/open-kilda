/* Copyright 2019 Telstra Open Source
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

import static java.lang.String.format;

import org.openkilda.floodlight.converter.OfFlowStatsMapper;
import org.openkilda.floodlight.converter.OfMeterStatsMapper;
import org.openkilda.floodlight.converter.OfPortStatsMapper;
import org.openkilda.floodlight.converter.OfTableStatsMapper;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.SwitchTableStatsData;
import org.openkilda.messaging.info.stats.TableStatsEntry;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterStatsRequest;
import org.projectfloodlight.openflow.protocol.OFPortStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFTableStatsReply;
import org.projectfloodlight.openflow.protocol.OFTableStatsRequest;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This service performs periodic port/flow/meter config statistics collection and pushes it to Kafka.
 */
public class StatisticsService implements IStatisticsService, IFloodlightModule {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsService.class);
    private static final long OFPM_ALL = 0xffffffffL;

    private IOFSwitchService switchService;
    private IKafkaProducerService producerService;
    private String statisticsTopic;
    private String region;

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
        producerService = context.getServiceImpl(IKafkaProducerService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext floodlightModuleContext) {

    }

    /**
     * execute stats requests handling.
     * @param context module context
     */
    public void processStatistics(FloodlightModuleContext context, Set<DatapathId> excludeSwitches) {
        statisticsTopic = context.getServiceImpl(KafkaUtilityService.class).getKafkaChannel().getStatsTopic();
        region = context.getServiceImpl(KafkaUtilityService.class).getKafkaChannel().getRegion();

        switchService.getAllSwitchMap().values()
                .stream()
                .filter(it -> !excludeSwitches.contains(it.getId()))
                .forEach(iofSwitch -> {
                    try {
                        gatherPortStats(iofSwitch);
                    } catch (Exception e) {
                        logger.error(format("Failed to gather stats for ports on switch %s.", iofSwitch.getId()), e);
                    }

                    try {
                        gatherFlowStats(iofSwitch);
                    } catch (Exception e) {
                        logger.error(format("Failed to gather stats for flows on switch %s.", iofSwitch.getId()), e);
                    }


                    try {
                        gatherMeterStats(iofSwitch);
                    } catch (Exception e) {
                        logger.error(format("Failed to gather stats for meters on switch %s.", iofSwitch.getId()), e);
                    }

                    try {
                        gatherTableStats(iofSwitch);
                    } catch (Exception e) {
                        logger.error(format("Failed to gather stats for tables on switch %s.", iofSwitch.getId()), e);
                    }
                });
    }

    @NewCorrelationContextRequired
    private void gatherPortStats(IOFSwitch iofSwitch) {
        OFFactory factory = iofSwitch.getOFFactory();
        SwitchId switchId = new SwitchId(iofSwitch.getId().getLong());

        OFPortStatsRequest portStatsRequest = factory
                .buildPortStatsRequest()
                .setPortNo(OFPort.ANY)
                .build();

        logger.trace("Getting port stats for switch={}", iofSwitch.getId());

        Futures.addCallback(iofSwitch.writeStatsRequest(portStatsRequest),
                new RequestCallback<>(data -> OfPortStatsMapper.INSTANCE.toPostStatsData(data, switchId),
                        "port", CorrelationContext.getId()));
    }

    @NewCorrelationContextRequired
    private void gatherFlowStats(IOFSwitch iofSwitch) {
        OFFactory factory = iofSwitch.getOFFactory();
        final SwitchId switchId = new SwitchId(iofSwitch.getId().getLong());

        OFFlowStatsRequest flowStatsRequest = factory
                .buildFlowStatsRequest()
                .setOutGroup(OFGroup.ANY)
                .build();

        if (factory.getVersion().compareTo(OFVersion.OF_15) != 0) {
            // skip flow stats for OF 1.5 protocol version
            logger.trace("Getting flow stats for switch={}", iofSwitch.getId());

            Futures.addCallback(iofSwitch.writeStatsRequest(flowStatsRequest),
                    new RequestCallback<>(data -> OfFlowStatsMapper.INSTANCE.toFlowStatsData(data, switchId),
                            "flow", CorrelationContext.getId()));
        }
    }

    @NewCorrelationContextRequired
    private void gatherTableStats(IOFSwitch iofSwitch) {
        final SwitchId switchId = new SwitchId(iofSwitch.getId().getLong());
        OFFactory factory = iofSwitch.getOFFactory();

        OFTableStatsRequest flowStatsRequest = factory
                .buildTableStatsRequest()
                .build();

        logger.trace("Getting flow stats per table for switch={}", iofSwitch.getId());

        if (factory.getVersion().compareTo(OFVersion.OF_13) >= 0) {
            Function<List<OFTableStatsReply>, InfoData> converter = (response) -> {
                List<TableStatsEntry> tableEntries = response.stream()
                        .filter(reply -> CollectionUtils.isNotEmpty(reply.getEntries()))
                        .map(OFTableStatsReply::getEntries)
                        .flatMap(List::stream)
                        .filter(entry -> entry.getActiveCount() != NumberUtils.LONG_ZERO)
                        .map(OfTableStatsMapper.INSTANCE::toTableStatsEntry)
                        .collect(Collectors.toList());

                return SwitchTableStatsData.builder()
                        .switchId(switchId)
                        .tableStatsEntries(tableEntries)
                        .build();
            };

            RequestCallback<OFTableStatsReply> callback = new RequestCallback<>(converter, "table",
                    CorrelationContext.getId());
            Futures.addCallback(iofSwitch.writeStatsRequest(flowStatsRequest), callback);
        }
    }

    @NewCorrelationContextRequired
    private void gatherMeterStats(IOFSwitch iofSwitch) {
        OFFactory factory = iofSwitch.getOFFactory();
        SwitchId switchId = new SwitchId(iofSwitch.getId().getLong());

        if (factory.getVersion().compareTo(OFVersion.OF_13) >= 0) {

            OFMeterStatsRequest meterStatsRequest = factory
                    .buildMeterStatsRequest()
                    .setMeterId(OFPM_ALL)
                    .build();

            logger.trace("Getting meter stats for switch={}", iofSwitch.getId());

            Futures.addCallback(iofSwitch.writeStatsRequest(meterStatsRequest),
                    new RequestCallback<>(data -> OfMeterStatsMapper.INSTANCE.toMeterStatsData(data, switchId),
                            "meter", CorrelationContext.getId()));
        }
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
                        System.currentTimeMillis(), correlationId, Destination.WFM_STATS, region);
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
