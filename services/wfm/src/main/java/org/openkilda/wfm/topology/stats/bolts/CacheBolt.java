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

package org.openkilda.wfm.topology.stats.bolts;

import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_CACHE_FILTER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_OFS_BOLT;
import static org.openkilda.wfm.topology.stats.StatsStreamType.FLOW_STATS;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.FlowStatsReply;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.topology.stats.CacheFlowEntry;
import org.openkilda.wfm.topology.stats.MeasurePoint;
import org.openkilda.wfm.topology.stats.StatsComponentType;
import org.openkilda.wfm.topology.stats.bolts.CacheFilterBolt.Commands;
import org.openkilda.wfm.topology.stats.bolts.CacheFilterBolt.FieldsNames;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CacheBolt extends BaseRichBolt {

    public static final String CACHE_FIELD = "cache";

    public static final Fields fieldsMessageFlowStats =
            new Fields(
                    MESSAGE_FIELD,
                    CACHE_FIELD);
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(
            CacheBolt.class);

    /**
     * Path computation instance.
     */
    private final PersistenceManager persistenceManager;

    private TopologyContext context;
    private OutputCollector outputCollector;

    /**
     * Cookie to flow map.
     */
    private Map<Long, CacheFlowEntry> cookieToFlow = new HashMap<>();

    public CacheBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    private void initFlowCache(FlowRepository flowRepository) {
        try {
            flowRepository.findAll().forEach(
                    flow -> cookieToFlow.put(flow.getCookie(), new CacheFlowEntry(
                            flow.getFlowId(),
                            flow.getSrcSwitch().getSwitchId().toOtsdFormat(),
                            flow.getDestSwitch().getSwitchId().toOtsdFormat()))
            );
            logger.debug("initFlowCache: {}", cookieToFlow);
            logger.info("Stats Cache: Initialized");
        } catch (Exception ex) {
            logger.error("Error on initFlowCache", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.context = topologyContext;
        this.outputCollector = outputCollector;

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();

        initFlowCache(repositoryFactory.createFlowRepository());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {

        try {

            StatsComponentType componentId = StatsComponentType.valueOf(tuple.getSourceComponent());

            if (componentId == STATS_CACHE_FILTER_BOLT) {

                Long cookie = tuple.getLongByField(FieldsNames.COOKIE.name());
                String flow = tuple.getStringByField(FieldsNames.FLOW.name());
                String sw = new SwitchId(tuple.getValueByField(FieldsNames.SWITCH.name()).toString()).toOtsdFormat();

                Commands command = (Commands) tuple.getValueByField(FieldsNames.COMMAND.name());
                MeasurePoint measurePoint = (MeasurePoint) tuple.getValueByField(FieldsNames.MEASURE_POINT.name());

                switch (command) {
                    case UPDATE:
                        updateCacheEntry(cookie, flow, sw, measurePoint);
                        break;
                    case REMOVE:
                        cookieToFlow.remove(cookie);
                        break;
                    default:
                        logger.error("invalid command");
                        break;
                }

                logger.debug("updated cookieToFlow: {}", cookieToFlow);
            } else if (componentId == STATS_OFS_BOLT) {
                InfoMessage message = (InfoMessage) tuple.getValueByField(MESSAGE_FIELD);

                FlowStatsData data = (FlowStatsData) message.getData();

                Map<Long, CacheFlowEntry> dataCache = new HashMap<>();
                for (FlowStatsReply reply : data.getStats()) {
                    for (FlowStatsEntry entry : reply.getEntries()) {
                        if (cookieToFlow.containsKey(entry.getCookie())) {
                            CacheFlowEntry cacheFlowEntry = cookieToFlow.get(entry.getCookie());
                            dataCache.put(entry.getCookie(), cacheFlowEntry);
                        }
                    }
                }
                logger.debug("execute:dataCache: {}", dataCache);
                Values values = new Values(message, dataCache);
                outputCollector.emit(FLOW_STATS.name(), tuple, values);
            }
        } finally {
            outputCollector.ack(tuple);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(FLOW_STATS.name(),
                fieldsMessageFlowStats);
    }

    private void updateCacheEntry(Long cookie, String flowId, String sw, MeasurePoint measurePoint) {
        CacheFlowEntry current = cookieToFlow.get(cookie);
        CacheFlowEntry replacement;
        if (current != null) {
            replacement = current.replace(sw, measurePoint);
        } else {
            replacement = new CacheFlowEntry(flowId).replace(sw, measurePoint);
        }
        cookieToFlow.put(cookie, replacement);
    }
}
