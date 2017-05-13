package org.bitbucket.openkilda.floodlight.switchmanager.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bitbucket.openkilda.floodlight.switchmanager.ISwitchManager;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.AbstractInstallFlowCommandData;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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
        ObjectMapper mapper = new ObjectMapper();

        Message message;
        try {
            message = mapper.readValue(json, Message.class);
        } catch (IOException exception) {
            logger.error("error parsing json: ", exception);
            final ErrorData data = new ErrorData(123, "Received JSON is not valid for TPN.",
                    ErrorType.DATA_INVALID, exception.toString());
            message = new ErrorMessage(data, now(), "");
            return mapper.writeValueAsString(message);
        }

        if (!message.getClass().equals(CommandMessage.class)) {
            logger.error("expecting a CommandMessage and got");
            logger.error(json);

            // TODO: figure out a real scheme for errorCode and errorMessages
            final ErrorData data = new ErrorData(123,
                    "Was expecting an installIngressFlow, installEgressFlow or installTransitFlow.",
                    ErrorType.DATA_INVALID, "");
            message = new ErrorMessage(data, now(), "");
            return mapper.writeValueAsString(message);
        }

        CommandMessage cmdMessage = (CommandMessage) message;
        CommandData data = cmdMessage.getData();
        if (!(data instanceof AbstractInstallFlowCommandData)) {
            logger.error("Message was not correct type: " + json);
            logger.debug("Class was " + data.getClass().getCanonicalName());
            final ErrorData errorData = new ErrorData(123, "Command was not of the correct type", ErrorType.DATA_INVALID, "");
            message = new ErrorMessage(errorData, now(), "");
            return mapper.writeValueAsString(message);
        }

        return mapper.writeValueAsString("ok");
    }

    private long now() {
        return System.currentTimeMillis() / 1000L;
    }
}
