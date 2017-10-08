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

package org.bitbucket.openkilda.floodlight.switchmanager.web;

import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.floodlight.switchmanager.ISwitchManager;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.BaseInstallFlow;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.error.MessageError;

import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created by jonv on 2/4/17.
 */
public class FlowResource extends ServerResource {
    private static final Logger logger = LoggerFactory.getLogger(FlowResource.class);

    @Post("json")
    @Put("json")
    public String installFlow(String json) throws IOException {
        ISwitchManager switchManager = (ISwitchManager) getContext().getAttributes()
                .get(ISwitchManager.class.getCanonicalName());

        Message message;
        try {
            message = MAPPER.readValue(json, Message.class);
        } catch (IOException exception) {
            String messageString = "Received JSON is not valid for TPN";
            logger.error("{}: {}", messageString, json, exception);
            MessageError responseMessage = new MessageError(DEFAULT_CORRELATION_ID, now(),
                    ErrorType.DATA_INVALID.toString(), messageString, exception.getMessage());
            return MAPPER.writeValueAsString(responseMessage);
        }

        if (!(message instanceof CommandMessage)) {
            String messageString = "Json payload message is not an instance of CommandMessage";
            logger.error("{}: class={}, data={}", messageString, message.getClass().getCanonicalName(), json);
            MessageError responseMessage = new MessageError(DEFAULT_CORRELATION_ID, now(),
                    ErrorType.DATA_INVALID.toString(), messageString, message.getClass().getCanonicalName());
            return MAPPER.writeValueAsString(responseMessage);
        }

        CommandMessage cmdMessage = (CommandMessage) message;
        CommandData data = cmdMessage.getData();
        if (!(data instanceof BaseInstallFlow)) {
            String messageString = "Json payload data is not an instance of CommandData";
            logger.error("{}: class={}, data={}", messageString, data.getClass().getCanonicalName(), json);
            MessageError responseMessage = new MessageError(DEFAULT_CORRELATION_ID, now(),
                    ErrorType.DATA_INVALID.toString(), messageString, data.getClass().getCanonicalName());
            return MAPPER.writeValueAsString(responseMessage);
        }

        return MAPPER.writeValueAsString("ok");
    }

    private long now() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }
}
