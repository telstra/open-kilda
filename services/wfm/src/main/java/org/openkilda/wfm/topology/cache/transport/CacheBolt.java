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

package org.openkilda.wfm.topology.cache.transport;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.SwitchDeleteRequest;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.CacheBoltState;
import org.openkilda.messaging.ctrl.state.NetworkDump;
import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.NetworkTopologyChange;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.Sender;
import org.openkilda.wfm.ctrl.CtrlAction;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.share.cache.NetworkCache;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.cache.StreamType;
import org.openkilda.wfm.topology.cache.service.CacheService;

import com.google.common.annotations.VisibleForTesting;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

public class CacheBolt
        extends BaseStatefulBolt<InMemoryKeyValueState<String, NetworkCache>>
        implements ICtrlBolt {
    public static final String STREAM_ID_CTRL = "ctrl";
    public static final String FLOW_ID_FIELD = "flowId";
    public static final String CORRELATION_ID_FIELD = "correlationId";

    /**
     * Network cache key.
     */
    private static final String NETWORK_CACHE = "network";

    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(CacheBolt.class);

    /**
     * Network cache cache.
     */
    private InMemoryKeyValueState<String, NetworkCache> state;

    private transient CacheService cacheService;

    private final PersistenceManager persistenceManager;

    private TopologyContext context;
    private OutputCollector outputCollector;

    CacheBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initState(InMemoryKeyValueState<String, NetworkCache> state) {
        this.state = state;
        NetworkCache networkCache = state.get(NETWORK_CACHE);
        if (networkCache == null) {
            networkCache = new NetworkCache();
            state.put(NETWORK_CACHE, networkCache);
        }

        cacheService = new CacheService(networkCache, persistenceManager.getRepositoryFactory());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.context = topologyContext;
        this.outputCollector = outputCollector;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        if (CtrlAction.boltHandlerEntrance(this, tuple)) {
            return;
        }
        logger.trace("State before: {}", state);

        Sender sender = new Sender(outputCollector, tuple);

        String json = tuple.getString(0);

        /*
          (carmine) Hack Alert
          1) merged two kafka topics into one;
          2) previous logic used topic source to determine how to parse the message
          3) new logic tries to parse it one way, then the next. Slightly inefficient.
         */
        // TODO: Eliminate the inefficiency introduced through the hack
        try {
            BaseMessage bm = MAPPER.readValue(json, BaseMessage.class);
            if (bm instanceof InfoMessage) {
                InfoMessage message = (InfoMessage) bm;
                InfoData data = message.getData();

                if (data instanceof SwitchInfoData) {
                    logger.info("Cache update switch info data: {}", data);
                    cacheService.handleSwitchEvent((SwitchInfoData) data, sender, message.getCorrelationId());

                } else if (data instanceof IslInfoData) {
                    cacheService.handleIslEvent((IslInfoData) data, sender, message.getCorrelationId());

                } else if (data instanceof PortInfoData) {
                    cacheService.handlePortEvent((PortInfoData) data, sender, message.getCorrelationId());

                } else if (data instanceof NetworkTopologyChange) {
                    logger.debug("Switch flows reroute request");

                    cacheService.handleNetworkTopologyChange(
                            (NetworkTopologyChange) data, sender, message.getCorrelationId());
                } else {
                    logger.warn("Skip undefined info data type {}", json);
                }
            } else if (bm instanceof CommandMessage) {
                CommandMessage message = (CommandMessage) bm;
                CommandData data = message.getData();

                if (data instanceof SwitchDeleteRequest) {
                    cacheService.deleteSwitch((SwitchDeleteRequest) data, sender, message.getCorrelationId());
                } else {
                    logger.warn("Skip undefined command data type {}", json);
                }
            } else {
                logger.warn("Skip undefined message type {}", json);
            }

        } catch (CacheException exception) {
            logger.error("Could not process message {}", tuple, exception);
        } catch (IOException exception) {
            logger.error("Could not deserialize message {}", tuple, exception);
        } catch (Exception e) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(tuple);
        }

        logger.trace("State after: {}", state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer output) {
        output.declareStream(StreamType.WFM_REROUTE.toString(), new Fields(FLOW_ID_FIELD, CORRELATION_ID_FIELD));
        output.declareStream(StreamType.OFE.toString(), AbstractTopology.fieldMessage);
        // FIXME(dbogun): use proper tuple format
        output.declareStream(STREAM_ID_CTRL, AbstractTopology.fieldMessage);
    }

    @Override
    public AbstractDumpState dumpState() {
        return new CacheBoltState(cacheService.getNetworkDump());
    }

    @VisibleForTesting
    @Override
    public void clearState() {
        logger.info("State clear request from test");
        initState(new InMemoryKeyValueState<>());
    }

    @Override
    public AbstractDumpState dumpStateBySwitchId(SwitchId switchId) {
        // Not implemented
        NetworkDump networkDump = new NetworkDump(
                new HashSet<>(),
                new HashSet<>());
        return new CacheBoltState(networkDump);
    }

    @Override
    public String getCtrlStreamId() {
        return STREAM_ID_CTRL;
    }

    @Override
    public TopologyContext getContext() {
        return context;
    }

    @Override
    public OutputCollector getOutput() {
        return outputCollector;
    }

    @Override
    public Optional<AbstractDumpState> dumpResorceCacheState() {
        return Optional.empty();
    }
}
