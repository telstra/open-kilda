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
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.wfm.topology.stats.MeasurePoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class CacheFilterBolt extends BaseRichBolt {

    public enum FieldsNames {
        COMMAND,
        SWITCH,
        FLOW,
        COOKIE,
        MEASURE_POINT
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
                    FieldsNames.COOKIE.name(),
                    FieldsNames.MEASURE_POINT.name());


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
                if (data instanceof InstallIngressFlow) {
                    InstallIngressFlow command = (InstallIngressFlow) data;
                    logMatchedRecord(command);
                    emit(tuple, Commands.UPDATE, command, MeasurePoint.INGRESS);
                } else if (data instanceof InstallEgressFlow) {
                    InstallEgressFlow command = (InstallEgressFlow) data;
                    logMatchedRecord(command);
                    emit(tuple, Commands.UPDATE, command, MeasurePoint.EGRESS);
                } else if (data instanceof InstallOneSwitchFlow) {
                    InstallOneSwitchFlow command = (InstallOneSwitchFlow) data;
                    logMatchedRecord(command);
                    emit(tuple, Commands.UPDATE, command, MeasurePoint.INGRESS);
                    emit(tuple, Commands.UPDATE, command, MeasurePoint.EGRESS);
                } else if (data instanceof RemoveFlow) {
                    RemoveFlow command = (RemoveFlow) data;
                    logMatchedRecord(command);
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

    private void emit(Tuple tuple, Commands action, BaseFlow command) {
        emit(tuple, action, command, null);
    }

    private void emit(Tuple tuple, Commands action, BaseFlow command, MeasurePoint point) {
        Values values = new Values(
                action,
                command.getId(),
                command.getSwitchId(),
                command.getCookie(),
                point);
        outputCollector.emit(CACHE_UPDATE.name(), tuple, values);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(CACHE_UPDATE.name(), fieldsMessageUpdateCache);
    }

    private void logMatchedRecord(BaseFlow flowCommand) {
        logger.debug("Catch {} command flow_id={} sw={} cookie={}",
                flowCommand.getClass().getCanonicalName(),
                flowCommand.getId(), flowCommand.getSwitchId(), flowCommand.getCookie());
    }
}
