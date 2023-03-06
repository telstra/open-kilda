/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.network.storm.bolt.bfd.worker.command;

import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.messaging.info.grpc.CreateOrUpdateLogicalPortResponse;
import org.openkilda.messaging.info.grpc.DeleteLogicalPortResponse;
import org.openkilda.wfm.topology.network.storm.ICommand;
import org.openkilda.wfm.topology.network.storm.bolt.bfd.worker.BfdWorker;

import lombok.Getter;

public abstract class BfdWorkerCommand implements ICommand<BfdWorker> {
    @Getter
    private final String requestId;

    public BfdWorkerCommand(String requestId) {
        this.requestId = requestId;
    }

    public void consumeResponse(BfdWorker handler, BfdSessionResponse response) {
        failedToConsume(response);
    }

    public void consumeResponse(BfdWorker handler, CreateOrUpdateLogicalPortResponse response) {
        failedToConsume(response);
    }

    public void consumeResponse(BfdWorker handler, DeleteLogicalPortResponse response) {
        failedToConsume(response);
    }

    public void consumeResponse(BfdWorker handler, ErrorData response) {
        failedToConsume(response);
    }

    public void failedToConsume(MessageData response) {
        throw new IllegalStateException(
                String.format("Unable to consume %s response by %s request", response, this.getClass().getName()));
    }

    public abstract void timeout(BfdWorker handler);
}
