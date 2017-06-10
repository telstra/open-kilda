package org.bitbucket.openkilda.floodlight.statistics;

import static java.util.stream.Collectors.toList;

import org.bitbucket.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.stats.FlowStatsData;
import org.bitbucket.openkilda.messaging.info.stats.FlowStatsEntry;
import org.bitbucket.openkilda.messaging.info.stats.FlowStatsReply;
import org.bitbucket.openkilda.messaging.info.stats.MeterConfigReply;
import org.bitbucket.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.bitbucket.openkilda.messaging.info.stats.PortStatsData;
import org.bitbucket.openkilda.messaging.info.stats.PortStatsEntry;
import org.bitbucket.openkilda.messaging.info.stats.PortStatsReply;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.protocol.OFPortStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
    private KafkaMessageProducer kafkaProducer;
    private IThreadPoolService threadPoolService;
    private int interval;

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
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(3);
        services.add(IFloodlightProviderService.class);
        services.add(IOFSwitchService.class);
        services.add(IThreadPoolService.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        switchService = context.getServiceImpl(IOFSwitchService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
        kafkaProducer = context.getServiceImpl(KafkaMessageProducer.class);
        Map<String, String> configParameters = context.getConfigParams(this);
        interval = Integer.valueOf(configParameters.get("interval"));
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        threadPoolService.getScheduledExecutor().scheduleAtFixedRate(
                () -> switchService.getAllSwitchMap().values().forEach(iofSwitch -> {
                    OFFactory factory = iofSwitch.getOFFactory();
                    OFPortStatsRequest portStatsRequest = factory.buildPortStatsRequest().setPortNo(OFPort.ANY).build();
                    OFFlowStatsRequest flowStatsRequest = factory.buildFlowStatsRequest().setOutGroup(OFGroup.ANY).setCookieMask(SYSTEM_MASK).build();
                    OFMeterConfigStatsRequest meterConfigStatsRequest = factory.buildMeterConfigStatsRequest().setMeterId(OFPM_ALL).build();
                    final String switchId = iofSwitch.getId().toString();
                    Futures.addCallback(iofSwitch.writeStatsRequest(portStatsRequest),
                            new RequestCallback<>(data -> {
                                List<PortStatsReply> replies = data.stream().map(reply -> {
                                    List<PortStatsEntry> entries = reply.getEntries().stream()
                                            .map(entry -> new PortStatsEntry(entry.getPortNo().getPortNumber(),
                                                    entry.getRxPackets().getValue(), entry.getTxPackets().getValue(),
                                                    entry.getRxBytes().getValue(), entry.getTxBytes().getValue(),
                                                    entry.getRxDropped().getValue(), entry.getTxDropped().getValue(),
                                                    entry.getRxErrors().getValue(), entry.getTxErrors().getValue(),
                                                    entry.getRxFrameErr().getValue(), entry.getRxOverErr().getValue(),
                                                    entry.getRxCrcErr().getValue(), entry.getCollisions().getValue()))
                                            .collect(toList());
                                    return new PortStatsReply(reply.getXid(), entries);
                                }).collect(toList());
                                return new PortStatsData(switchId, replies);
                            }, "port"));
                    Futures.addCallback(iofSwitch.writeStatsRequest(flowStatsRequest), new RequestCallback<>(data -> {
                        List<FlowStatsReply> replies = data.stream().map(reply -> {
                            List<FlowStatsEntry> entries = reply.getEntries().stream()
                                    .map(entry -> new FlowStatsEntry(entry.getTableId().getValue(),
                                            entry.getCookie().getValue(),
                                            entry.getPacketCount().getValue(),
                                            entry.getByteCount().getValue()))
                                    .collect(toList());
                            return new FlowStatsReply(reply.getXid(), entries);
                        }).collect(toList());
                        return new FlowStatsData(switchId, replies);
                    }, "flow"));
                    Futures.addCallback(iofSwitch.writeStatsRequest(meterConfigStatsRequest), new RequestCallback<>(data -> {
                        List<MeterConfigReply> replies = data.stream().map(reply -> {
                            List<Long> meterIds = reply.getEntries().stream().map(OFMeterConfig::getMeterId).collect(toList());
                            return new MeterConfigReply(reply.getXid(), meterIds);
                        }).collect(toList());
                        return new MeterConfigStatsData(switchId, replies);
                    }, "meter config"));
                }), interval, interval, TimeUnit.SECONDS);
    }

    private class RequestCallback<T extends OFStatsReply> implements FutureCallback<List<T>> {
        private Function<List<T>, InfoData> transform;
        private String type;

        RequestCallback(Function<List<T>, InfoData> transform, String type) {
            this.transform = transform;
            this.type = type;
        }

        @Override
        public void onSuccess(List<T> data) {
            //InfoMessage infoMessage = new InfoMessage(
            // transform.apply(data), System.currentTimeMillis(), "system", Destination.WFM_STATS);
            //kafkaProducer.postMessage(Topic.OFS_WFM_STATS.getId(), infoMessage);
        }

        @Override
        public void onFailure(Throwable t) {
            logger.error("Exception reading " + type + " stats", t);
        }
    }
}
