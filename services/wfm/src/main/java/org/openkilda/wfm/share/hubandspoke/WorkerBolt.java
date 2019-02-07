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

package org.openkilda.wfm.share.hubandspoke;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import lombok.Builder;
import lombok.Getter;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * This class provides main methods for classes acting as a worker with asynchronous operations.
 * </p>
 * Note: Additional bolt, spout and streams are required for the topology to work this worker bolt properly:
 * {@link CoordinatorBolt} and {@link CoordinatorSpout} should be declared in a topology definition.
 * </p>
 * Following streams are mandatory:
 * {@code}WorkerBolt{@code} must have income stream with directGrouping from {@code}CoordinatorBolt.ID{@code}.
 * {@link CoordinatorBolt} must have following income streams:
 * <ul>
 *     <il>allGrouping stream from {@code}CoordinatorSpout.ID{@code}</il>
 *     <il>fieldsGrouping stream from hub with grouping by {@code}MessageTranslator.KEY_FIELD{@code}</il>
 * </ul>
 */
public abstract class WorkerBolt extends CoordinatedBolt {
    public static final String ID = "worker.bolt";
    private Map<String, Integer> pendingTasks = new HashMap<>();
    private Config workerConfig;

    public WorkerBolt(Config config) {
        super(config.isAutoAck(), config.getDefaultTimeout());

        requireNonNull(config.getStreamToHub(), "Stream to hub bolt cannot be null");
        requireNonNull(config.getHubComponent(), "Hub bolt id cannot be null");
        requireNonNull(config.getWorkerSpoutComponent(), "Worker's spout id cannot be null");
        this.workerConfig = config;
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String key = input.getStringByField(MessageTranslator.KEY_FIELD);
        String sender = input.getSourceComponent();

        if (workerConfig.getHubComponent().equals(sender)) {
            pendingTasks.put(key, input.getSourceTask());
            registerCallback(key, input);

            onHubRequest(input);
        } else if (pendingTasks.containsKey(key) && workerConfig.getWorkerSpoutComponent().equals(sender)) {
            onAsyncResponse(input);
        } else {
            unhandledInput(input);
        }
    }

    /**
     * Send response to hub bolt once all required responses are received.
     * @param input received tuple.
     * @param values response to be sent to the hub.
     */
    protected void emitResponseToHub(Tuple input, Values values) throws PipelineException {
        String key = input.getStringByField(MessageTranslator.KEY_FIELD);
        cancelCallback(key, input);

        Integer taskId = pendingTasks.remove(key);
        if (taskId == null) {
            throw new IllegalStateException(format("Attempt to send response for non pending task with id %s", key));
        }
        getOutput().emitDirect(taskId, workerConfig.getStreamToHub(), values);
    }

    protected abstract void onHubRequest(Tuple input) throws AbstractException;

    protected abstract void onAsyncResponse(Tuple input) throws AbstractException;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(workerConfig.getStreamToHub(), true, MessageTranslator.STREAM_FIELDS);
    }

    @Builder
    @Getter
    public static class Config implements Serializable {
        private String streamToHub;
        private String hubComponent;
        private String workerSpoutComponent;
        private int defaultTimeout = 100;
        private boolean autoAck = true;
    }
}
