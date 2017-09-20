package org.bitbucket.openkilda.wfm.topology.cache;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.discovery.NetworkCommandData;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowRestoreRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.error.CacheException;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.discovery.NetworkInfoData;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PortInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.info.flow.FlowInfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.ImmutablePair;
import org.bitbucket.openkilda.pce.cache.Cache;
import org.bitbucket.openkilda.pce.cache.FlowCache;
import org.bitbucket.openkilda.pce.cache.NetworkCache;
import org.bitbucket.openkilda.wfm.topology.AbstractTopology;
import org.bitbucket.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CacheBolt extends AbstractTickStatefulBolt<InMemoryKeyValueState<String, Cache>> {
    /**
     * Network cache key.
     */
    private static final String NETWORK_CACHE = "network";

    /**
     * Network cache key.
     */
    private static final String FLOW_CACHE = "flow";

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger(CacheBolt.class);

    /**
     * Network cache.
     */
    private NetworkCache networkCache;

    /**
     * Flow cache.
     */
    private FlowCache flowCache;

    /**
     * Network cache cache.
     */
    private InMemoryKeyValueState<String, Cache> state;

    /**
     * Output collector.
     */
    private OutputCollector outputCollector;

    /**
     * Time passed.
     */
    private int timePassed = 0;

    /**
     * Discovery interval.
     */
    private final int discoveryInterval;

    /**
     * Flows dump.
     */
    private final Set<Flow> flows = new HashSet<>();

    /**
     * Instance constructor.
     *
     * @param discoveryInterval discovery interval
     */
    CacheBolt(int discoveryInterval) {
        this.discoveryInterval = discoveryInterval;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initState(InMemoryKeyValueState<String, Cache> state) {
        this.state = state;

        networkCache = (NetworkCache) state.get(NETWORK_CACHE);
        if (networkCache == null) {
            networkCache = new NetworkCache();
            this.state.put(NETWORK_CACHE, networkCache);
        }

        flowCache = (FlowCache) state.get(FLOW_CACHE);
        if (flowCache == null) {
            flowCache = new FlowCache();
            this.state.put(FLOW_CACHE, flowCache);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }

    @Override
    public void doWork(Tuple tuple) {
        logger.trace("State before: {}", state);
        logger.debug("Ingoing tuple: {}", tuple);

        String json = tuple.getString(0);
        ComponentType componentId = ComponentType.valueOf(tuple.getSourceComponent());

        try {
            switch (componentId) {

                case WFM_UPDATE_KAFKA_SPOUT:
                    InfoData data = MAPPER.readValue(json, InfoData.class);

                    if (data != null) {
                        logger.debug("State update info data {}", data);

                        if (data instanceof SwitchInfoData) {
                            logger.debug("State update switch info message");

                            handleSwitchEvent((SwitchInfoData) data, tuple);
                            emitInfoMessage(data, tuple);

                        } else if (data instanceof IslInfoData) {
                            logger.debug("State update isl info message");

                            handleIslEvent((IslInfoData) data, tuple);
                            emitInfoMessage(data, tuple);

                        } else if (data instanceof PortInfoData) {
                            logger.debug("State update port info message");

                            handlePortEvent((PortInfoData) data, tuple);
                            emitInfoMessage(data, tuple);

                        } else if (data instanceof FlowInfoData) {
                            FlowInfoData flowData = (FlowInfoData) data;
                            Flow forward = flowData.getPayload().getLeft();
                            Flow reverse = flowData.getPayload().getRight();

                            switch (flowData.getOperation()) {
                                case CREATE:
                                    flowCache.putFlow(forward.getFlowId(), flowData.getPayload());
                                    emitCommandMessage(new FlowCreateRequest(forward), tuple);
                                    emitCommandMessage(new FlowCreateRequest(reverse), tuple);
                                    break;
                                case DELETE:
                                    flowCache.removeFlow(forward.getFlowId());
                                    emitCommandMessage(new FlowDeleteRequest(forward), tuple);
                                    emitCommandMessage(new FlowDeleteRequest(reverse), tuple);
                                    break;
                                case UPDATE:
                                    flowCache.putFlow(forward.getFlowId(), flowData.getPayload());
                                    emitCommandMessage(new FlowUpdateRequest(forward), tuple);
                                    emitCommandMessage(new FlowUpdateRequest(reverse), tuple);
                                    break;
                                default:
                                    logger.warn("Skip undefined flow operation {}", json);
                                    break;
                            }
                        } else {
                            logger.warn("Skip undefined info data type {}", json);
                        }
                    } else {
                        logger.warn("Skip undefined message type {}", json);
                    }

                    break;

                case TPE_KAFKA_SPOUT:
                    Message message = MAPPER.readValue(json, Message.class);

                    if (message instanceof InfoMessage && Destination.WFM_CACHE == message.getDestination()) {
                        logger.debug("Storage content message {}", json);

                        handleNetworkDump(((InfoMessage) message).getData());
                    }

                    break;

                default:
                    logger.warn("Skip undefined cache {}", tuple);

                    break;
            }

        } catch (CacheException exception) {
            logger.error("Could not process message {}", tuple, exception);

        } catch (IOException exception) {
            logger.error("Could not deserialize message {}", tuple, exception);

        } finally {
            logger.debug("State ack: component={}", componentId);

            outputCollector.ack(tuple);
        }

        logger.trace("State after: {}", state);
    }

    @Override
    protected void doTick(Tuple tuple) {
        if (timePassed == 0) {
            Values values = getNetworkRequest();
            if (values != null) {
                outputCollector.emit(StreamType.TPE.toString(), tuple, values);
            } else {
                logger.error("Could not send network cache request");
            }
        }
        if (timePassed == discoveryInterval) {
            emitRestoreCommands(flows, tuple);
            flows.clear();
        }
        if (timePassed <= discoveryInterval) {
            timePassed += 1;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.TPE.toString(), AbstractTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.WFM_DUMP.toString(), AbstractTopology.fieldMessage);
    }

    private void handleNetworkDump(InfoData info) {

        if (info instanceof NetworkInfoData) {
            NetworkInfoData data = (NetworkInfoData) info;
            logger.debug("Fill network state {}", data);
            networkCache.load(data.getSwitches(), data.getIsls());
            flowCache.load(data.getFlows());
            flows.addAll(formatFlows(data.getFlows()));
        } else {
            logger.warn("Incorrect network state {}", info);
        }
    }

    private void handleSwitchEvent(SwitchInfoData sw, Tuple tuple) {
        logger.debug("State update switch info message {}", sw.getState().toString());

        switch (sw.getState()) {
            case ADDED:
            case ACTIVATED:
                if (networkCache.cacheContainsSwitch(sw.getSwitchId())) {
                    networkCache.updateSwitch(sw);
                } else {
                    networkCache.createSwitch(sw);
                }
                break;
            case DEACTIVATED:
            case REMOVED:
                if (networkCache.cacheContainsSwitch(sw.getSwitchId())) {
                    networkCache.updateSwitch(sw);
                }
                emitRerouteCommands(flowCache.getAffectedFlows(sw.getSwitchId()), tuple);
                break;
            case CHANGED:
                break;
            default:
                logger.warn("Unknown state update switch info message");
                break;
        }
    }

    private void handleIslEvent(IslInfoData isl, Tuple tuple) {
        logger.debug("State update isl info message cached {}", isl.getState().toString());

        switch (isl.getState()) {
            case DISCOVERED:
                if (networkCache.cacheContainsIsl(isl.getId())) {
                    networkCache.updateIsl(isl);
                } else {
                    networkCache.createIsl(isl);
                }
                break;
            case FAILED:
                networkCache.deleteIsl(isl.getId());
                emitRerouteCommands(flowCache.getAffectedFlows(isl), tuple);
                break;
            case OTHER_UPDATE:
                break;
            default:
                logger.warn("Unknown state update isl info message");
                break;
        }
    }

    private void handlePortEvent(PortInfoData port, Tuple tuple) {
        logger.debug("State update port info message cached {}", port.getState().toString());

        switch (port.getState()) {
            case DOWN:
            case DELETE:
                emitRerouteCommands(flowCache.getAffectedFlows(port), tuple);
                break;
            case ADD:
            case UP:
            case OTHER_UPDATE:
                break;
            default:
                logger.warn("Unknown state update isl info message");
                break;
        }
    }

    private void emitInfoMessage(InfoData data, Tuple tuple) throws IOException {
        Message message = new InfoMessage(data, System.currentTimeMillis(),
                Utils.SYSTEM_CORRELATION_ID, Destination.TOPOLOGY_ENGINE);
        outputCollector.emit(StreamType.TPE.toString(), tuple, new Values(MAPPER.writeValueAsString(message)));
        logger.debug("State update info message sent");
    }

    private void emitCommandMessage(CommandData data, Tuple tuple) throws IOException {
        Message message = new CommandMessage(data, System.currentTimeMillis(),
                Utils.SYSTEM_CORRELATION_ID, Destination.TOPOLOGY_ENGINE);
        outputCollector.emit(StreamType.TPE.toString(), tuple, new Values(MAPPER.writeValueAsString(message)));
        logger.debug("State update command message sent");
    }

    private void emitRestoreCommands(Set<Flow> flows, Tuple tuple) {
        if (flows != null) {
            for (Flow flow : flows) {
                try {
                    FlowRestoreRequest request = new FlowRestoreRequest(flow);
                    CommandMessage command = new CommandMessage(request, System.currentTimeMillis(),
                            Utils.SYSTEM_CORRELATION_ID, Destination.WFM);
                    Values values = new Values(Utils.MAPPER.writeValueAsString(command));
                    outputCollector.emit(StreamType.WFM_DUMP.toString(), tuple, values);
                } catch (JsonProcessingException exception) {
                    logger.error("Could not format flow restore request by flow={}", flow, exception);
                }
            }
        }
    }

    private void emitRerouteCommands(Set<ImmutablePair<Flow, Flow>> flows, Tuple tuple) {
        for (ImmutablePair<Flow, Flow> flow : flows) {
            try {
                FlowRerouteRequest request = new FlowRerouteRequest(flow.getLeft());
                CommandMessage command = new CommandMessage(request, System.currentTimeMillis(),
                        Utils.SYSTEM_CORRELATION_ID, Destination.WFM);
                Values values = new Values(Utils.MAPPER.writeValueAsString(command));
                outputCollector.emit(StreamType.WFM_DUMP.toString(), tuple, values);
            } catch (JsonProcessingException exception) {
                logger.error("Could not format flow reroute request by flow={}", flow, exception);
            }
        }
    }

    private Values getNetworkRequest() {
        Values values = null;

        try {
            CommandMessage command = new CommandMessage(new NetworkCommandData(),
                    System.currentTimeMillis(), Utils.SYSTEM_CORRELATION_ID, Destination.TOPOLOGY_ENGINE);
            values = new Values(Utils.MAPPER.writeValueAsString(command));
        } catch (IOException exception) {
            logger.error("Could not serialize network cache request", exception);
        }

        return values;
    }

    private Set<Flow> formatFlows(Set<ImmutablePair<Flow, Flow>> flows) {
        return flows.stream()
                .map(flow -> new Flow(
                        flow.left.getFlowId(),
                        flow.left.getBandwidth(),
                        flow.left.getDescription(),
                        flow.left.getSourceSwitch(),
                        flow.left.getSourcePort(),
                        flow.left.getSourceVlan(),
                        flow.left.getDestinationSwitch(),
                        flow.left.getDestinationPort(),
                        flow.left.getDestinationVlan()))
                .collect(Collectors.toSet());
    }
}
