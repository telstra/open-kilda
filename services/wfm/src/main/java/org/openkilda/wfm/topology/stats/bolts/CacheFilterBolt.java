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

package org.openkilda.wfm.topology.stats.bolts;

import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.wfm.topology.stats.StatsStreamType.CACHE_UPDATE;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.wfm.topology.utils.StatsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class CacheFilterBolt extends BaseRichBolt {

    public enum FieldsNames {
        COMMAND,
        SWITCH,
        FLOW,
        COOKIE
    }

    public enum Commands {
        UPDATE,
        REMOVE
    }

    public static final Fields fieldsMessageUpdateCache =
            new Fields(
                    FieldsNames.COMMAND.name(),
                    FieldsNames.FLOW.name(),
                    FieldsNames.SWITCH.name(),
                    FieldsNames.COOKIE.name());


    private static final Logger logger = LoggerFactory.getLogger(CacheFilterBolt.class);

    private TopologyContext context;
    private OutputCollector outputCollector;


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

        String json = tuple.getString(0);

        try {
            BaseMessage bm = MAPPER.readValue(json, BaseMessage.class);
            if (bm instanceof CommandMessage) {
                CommandMessage message = (CommandMessage) bm;
                CommandData data = message.getData();
                if (data instanceof InstallEgressFlow) {
                    InstallEgressFlow command = (InstallEgressFlow) data;
                    logger.debug("Catch InstallEgressFlow install flow_id={} sw={} cookie={}",
                            command.getId(), command.getSwitchId(), command.getCookie());
                    emit(tuple, Commands.UPDATE, command);
                }
                else if (data instanceof RemoveFlow)
                {
                    RemoveFlow command = (RemoveFlow) data;
                    logger.debug("Catch RemoveFlow install flow_id={} sw={} cookie={}",
                            command.getId(), command.getSwitchId(), command.getCookie());
                    emit(tuple, Commands.REMOVE, command);
                }
            }
        } catch (IOException exception) {
            logger.error("Could not deserialize message {}", tuple, exception);
        } catch (Exception e) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(tuple);
        }
    }

    public void emit(Tuple tuple, Commands remove, BaseFlow command) {
        Values values = new Values(
                remove,
                command.getId(),
                StatsUtil.formatSwitchId(command.getSwitchId()),
                command.getCookie());
        outputCollector.emit(CACHE_UPDATE.name(), tuple, values);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(CACHE_UPDATE.name(), fieldsMessageUpdateCache);
    }
}
