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

package org.openkilda.wfm.topology.event.bolt;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.AbstractOutputAdapter;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.topology.event.OFEventWfmTopologyConfig;
import org.openkilda.wfm.topology.event.OFEventWfmTopologyConfig.DiscoveryConfig;
import org.openkilda.wfm.topology.event.model.Sync;
import org.openkilda.wfm.topology.event.service.FlMonitorService;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FlMonitor extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.FL_MONITOR.toString();

    public static final String FIELD_ID_INPUT = SpeakerDecoder.FIELD_ID_INPUT;
    public static final String FIELD_ID_SYNC = "sync";

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_INPUT, FIELD_ID_CONTEXT);

    public static final String STREAM_SPEAKER_ID = "speaker";
    public static final Fields STREAM_SPEAKER_FIELDS = new Fields(SpeakerEncoder.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    public static final String STREAM_SYNC_ID = "sync";
    public static final Fields STREAM_SYNC_FIELDS = new Fields(FIELD_ID_SYNC, FIELD_ID_CONTEXT);

    private final long speakerOutageDelay;
    private final long dumpRequestTimeout;

    private FlMonitorService monitor;

    public FlMonitor(OFEventWfmTopologyConfig config) {
        DiscoveryConfig discoveryConfig = config.getDiscoveryConfig();

        speakerOutageDelay = TimeUnit.SECONDS.toMillis(discoveryConfig.getDiscoverySpeakerFailureTimeout());
        dumpRequestTimeout = TimeUnit.SECONDS.toMillis(discoveryConfig.getDiscoveryDumpRequestTimeout());
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        OutputAdapter outputAdapter = new OutputAdapter(this, input);

        String source = input.getSourceComponent();
        if (MonotonicTick.BOLT_ID.equals(source)) {
            long timeMillis = pullValue(input, MonotonicTick.FIELD_ID_TIME_MILLIS, Long.class);
            monitor.timerTick(outputAdapter, timeMillis);
        } else if (SpeakerDecoder.BOLT_ID.equals(source)) {
            Message message = pullValue(input, FIELD_ID_INPUT, Message.class);
            monitor.speakerMessage(outputAdapter, message);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector outputManager) {
        super.prepare(stormConf, context, outputManager);

        monitor = new FlMonitorService(speakerOutageDelay, dumpRequestTimeout, System.currentTimeMillis());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
        outputManager.declareStream(STREAM_SPEAKER_ID, STREAM_SPEAKER_FIELDS);
        outputManager.declareStream(STREAM_SYNC_ID, STREAM_SYNC_FIELDS);
    }

    public static class OutputAdapter extends AbstractOutputAdapter {
        public OutputAdapter(AbstractBolt owner, Tuple tuple) {
            super(owner, tuple);
        }

        public void proxyCurrentTuple() {
            emit(tuple.select(STREAM_FIELDS));
        }

        public void speakerCommand(CommandData payload) {
            emit(STREAM_SPEAKER_ID, new Values(payload, getContext()));
        }

        public void shareSync(Sync payload) {
            emit(STREAM_SYNC_ID, new Values(payload, getContext()));
        }
    }
}
