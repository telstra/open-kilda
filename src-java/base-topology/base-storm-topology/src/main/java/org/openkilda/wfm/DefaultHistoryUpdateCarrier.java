/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;

import lombok.NonNull;
import org.apache.storm.tuple.Values;

/**
 * This is a default implementation for sending history updates that are accessible later via HistoryService.
 * A topology that uses the bolt implementing this interface must register a path to history bolt.
 * That is, in a topology that extends AbstractTopology, declare a Kafka bolt and specify a component ID
 * that is a source of history updates in the fields grouping and declare a stream.
 * TODO add a readme.md with the details and example.
 */
public interface DefaultHistoryUpdateCarrier extends HistoryUpdateCarrier, TupleIdentifiable, ContextEmittable {

    @Override
    default void sendHistoryUpdate(@NonNull FlowHistoryHolder historyHolder) {
        InfoMessage message = new InfoMessage(historyHolder, getCommandContext().getCreateTime(),
                getCommandContext().getCorrelationId());
        emitWithContext(getHistoryStreamName(), getCurrentTuple(),
                new Values(historyHolder.getTaskId(), message));
    }
}
