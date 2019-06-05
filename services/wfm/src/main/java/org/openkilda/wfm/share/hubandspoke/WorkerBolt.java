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

    private final Config workerConfig;

    protected Map<String, Tuple> pendingTasks = new HashMap<>();

    public WorkerBolt(Config config) {
        super(config.isAutoAck(), config.getDefaultTimeout());

        requireNonNull(config.getStreamToHub(), "Stream to hub bolt cannot be null");
        requireNonNull(config.getHubComponent(), "Hub bolt id cannot be null");
        requireNonNull(config.getWorkerSpoutComponent(), "Worker's spout id cannot be null");
        this.workerConfig = config;
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String key = input.getStringByField(MessageTranslator.KEY_FIELD);
        String sourceComponent = input.getSourceComponent();

        if (sourceComponent.equals(workerConfig.getHubComponent())) {
            pendingTasks.put(key, input);
            registerCallback(key, input);

            onHubRequest(input);
        } else if (pendingTasks.containsKey(key)) {
            if (workerConfig.getWorkerSpoutComponent().equals(sourceComponent)) {
                // TODO(surabujin): it whould be great to get initial request together with response i.e.
                // onAsyncResponse(input, pendingTasks.get(key)onAsyncResponse(input);
                onAsyncResponse(input);
            } else if (sourceComponent.equals(CoordinatorBolt.ID)) {
                log.warn("Timeout occurred while waiting for a response for {}", key);
                onTimeout(key, input);
            }
        } else {
            unhandledInput(input);
        }
    }

    /**
     * Send response to the hub bolt. Note: the operation's key is required.
     * @param input received tuple.
     * @param values response to be sent to the hub.
     */
    protected void emitResponseToHub(Tuple input, Values values) {
        String key = input.getStringByField(MessageTranslator.KEY_FIELD);
        cancelCallback(key, input);

        Tuple processingRequest = pendingTasks.remove(key);
        if (processingRequest == null) {
            throw new IllegalStateException(format("Attempt to send response for non pending task with id %s", key));
        }
        getOutput().emitDirect(processingRequest.getSourceTask(), workerConfig.getStreamToHub(), values);
    }

    protected abstract void onHubRequest(Tuple input) throws Exception;

    protected abstract void onAsyncResponse(Tuple input) throws Exception;

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

        @Builder.Default
        private int defaultTimeout = 100;

        @Builder.Default
        private boolean autoAck = true;
    }
}
