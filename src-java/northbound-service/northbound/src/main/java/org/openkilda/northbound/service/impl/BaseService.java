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

package org.openkilda.northbound.service.impl;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.utils.RequestCorrelationId;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public abstract class BaseService {
    private final MessagingChannel messagingChannel;

    protected BaseService(MessagingChannel messagingChannel) {
        this.messagingChannel = messagingChannel;
    }

    protected CompletionException maskConcurrentException(Throwable e) {
        if (e instanceof CompletionException) {
            return (CompletionException) e;
        }
        return new CompletionException(e);
    }

    protected CompletableFuture<InfoData> sendRequest(String topic, CommandData payload) {
        CommandMessage message = new CommandMessage(payload, System.currentTimeMillis(), RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(topic, message);
    }
}
