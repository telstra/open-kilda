package org.bitbucket.openkilda.floodlight.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bitbucket.openkilda.messaging.info.*;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

/**
 * This service performs periodic port/flow/meter config statistics collection and pushes it to Kafka.
 */
public class StatisticsService implements IStatisticsService, IFloodlightModule {
    private static final U64 SYSTEM_MASK = U64.of(0x8000000000000000L);
    private static final long OFPM_ALL = 0xffffffffL;
    private static final Logger logger = LoggerFactory.getLogger(StatisticsService.class);

    private IOFSwitchService switchService;
    private KafkaProducer<String, String> producer;
    private String topic;
    private int interval;
    private ObjectMapper mapper = new ObjectMapper();
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

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
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(2);
        services.add(IFloodlightProviderService.class);
        services.add(IOFSwitchService.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        Properties kafkaProps = new Properties();
        Map<String, String> configParameters = context.getConfigParams(this);
        kafkaProps.put("bootstrap.servers", configParameters.get("bootstrap-servers"));
        kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producer = new KafkaProducer<>(kafkaProps);
        topic = configParameters.get("topic");
        interval = Integer.valueOf(configParameters.get("interval"));
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        switchService = context.getServiceImpl(IOFSwitchService.class);
        executorService.scheduleAtFixedRate(() -> {
            switchService.getAllSwitchMap().values().forEach(iofSwitch -> {
                final OFFactory factory = iofSwitch.getOFFactory();
                final OFPortStatsRequest portStatsRequest = factory.buildPortStatsRequest().setPortNo(OFPort.ANY).build();
                OFFlowStatsRequest flowStatsRequest = factory.buildFlowStatsRequest()
                        .setCookie(U64.ZERO)
                        .setCookieMask(SYSTEM_MASK)
                        .build();
                final OFMeterConfigStatsRequest meterConfigStatsRequest = factory.buildMeterConfigStatsRequest().setMeterId(OFPM_ALL).build();
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
                            return new PortStatsData(iofSwitch.getId().toString(), replies);
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
                    return new FlowStatsData(iofSwitch.getId().toString(), replies);
                }, "flow"));
                Futures.addCallback(iofSwitch.writeStatsRequest(meterConfigStatsRequest), new RequestCallback<>(data -> {
                    List<MeterConfigReply> replies = data.stream().map(reply -> {
                        List<Long> meterIds = reply.getEntries().stream().map(OFMeterConfig::getMeterId).collect(toList());
                        return new MeterConfigReply(reply.getXid(), meterIds);
                    }).collect(toList());
                    return new MeterConfigStatsData(iofSwitch.getId().toString(), replies);
                }, "meter config"));
            });
        }, interval, interval, TimeUnit.SECONDS);
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
            try {
                InfoMessage infoMessage = new InfoMessage(transform.apply(data), System.currentTimeMillis(), "system");
                final String json = mapper.writeValueAsString(infoMessage);
                logger.debug("About to send {}", json);
                producer.send(new ProducerRecord<>(topic, json));
            } catch (JsonProcessingException e) {
                logger.error("Exception serializing " + type + " stats", e);
            }
        }

        @Override
        public void onFailure(Throwable t) {
            logger.error("Exception reading " + type + " stats", t);
        }
    }
}
