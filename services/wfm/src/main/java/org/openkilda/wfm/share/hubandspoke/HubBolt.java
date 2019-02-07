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

import static java.util.Objects.requireNonNull;

import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.commons.lang3.StringUtils;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

/**
 * Base class for bolts acting as a hub that interacts with multiple workers (spokes). Defines three main
 * methods: onRequest(), onWorkerResponse() and onTimeout(). Helps to handle income external requests and specify the
 * ways how worker responses and timeouts should be processed.
 * </p>
 * Note: Additional bolt, spout and streams are required for the topology to work this hub properly:
 * {@link CoordinatorBolt} and {@link CoordinatorSpout} should be declared in a topology definition.
 * </p>
 * Following streams are mandatory:
 * {@code}HubBolt{@code} must have income stream with directGrouping from {@code}CoordinatorBolt.ID{@code}.
 * {@link CoordinatorBolt} must have following income streams:
 * <ul>
 *     <il>allGrouping stream from {@code}CoordinatorSpout.ID{@code}</il>
 *     <il>fieldsGrouping stream from hub with grouping by {@code}MessageTranslator.KEY_FIELD{@code}</il>
 * </ul>
 */
public abstract class HubBolt extends CoordinatedBolt {
    private final String requestSenderComponent;

    protected transient OutputCollector collector;

    public HubBolt(String requestSenderComponent, int timeoutMs, boolean autoAck) {
        super(autoAck, timeoutMs);
        this.requestSenderComponent = requireNonNull(requestSenderComponent,
                "A component that sends income requests should be not null");
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        if (requestSenderComponent.equals(input.getSourceComponent())) {
            registerCallback(input.getStringByField(MessageTranslator.KEY_FIELD), input);
            onRequest(input);
        } else if (StringUtils.contains(input.getSourceComponent(), WorkerBolt.ID)) {
            onWorkerResponse(input);
        }
    }

    /**
     * Handler for income request. Define the main steps and functionality for current hub.
     * @param input income message.
     */
    protected abstract void onRequest(Tuple input) throws AbstractException;

    /**
     * Handler for all hub-related workers. Since hub might has unlimited number of workers this method handles all
     * responses from all workers.
     * @param input response from worker.
     */
    protected abstract void onWorkerResponse(Tuple input) throws AbstractException;

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
    }

}
