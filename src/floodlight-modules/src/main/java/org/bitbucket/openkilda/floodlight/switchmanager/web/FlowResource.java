package org.bitbucket.openkilda.floodlight.switchmanager.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bitbucket.openkilda.floodlight.message.CommandMessage;
import org.bitbucket.openkilda.floodlight.message.ErrorMessage;
import org.bitbucket.openkilda.floodlight.message.Message;
import org.bitbucket.openkilda.floodlight.message.command.CommandData;
import org.bitbucket.openkilda.floodlight.message.command.InstallEgressFlow;
import org.bitbucket.openkilda.floodlight.message.command.InstallIngressFlow;
import org.bitbucket.openkilda.floodlight.message.command.InstallTransitFlow;
import org.bitbucket.openkilda.floodlight.message.error.ErrorData;
import org.bitbucket.openkilda.floodlight.switchmanager.ISwitchManager;
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
    Logger logger;

    @Post("json")
    @Put("json")
    public String installFlow(String json) throws IOException {
        logger = LoggerFactory.getLogger(FlowResource.class);
        ISwitchManager switchManager = (ISwitchManager) getContext().getAttributes()
                .get(ISwitchManager.class.getCanonicalName());
        ObjectMapper mapper = new ObjectMapper();

        Message message;
        try {
            message = mapper.readValue(json, Message.class);
        } catch (IOException exception) {
            logger.error("error parsing json: ", exception);
            message = new ErrorMessage()
                    .withData(new ErrorData().withErrorCode(123)
                                             .withErrorMessage("Invalid JSON Received")
                                             .withErrorDescription("Received JSON is not valid for TPN."))
                    .withType(Message.Type.ERROR)
                    .withTimestamp(now());
            return mapper.writeValueAsString(message);
        }

        if (!message.getClass().equals(CommandMessage.class)) {
            logger.error("expecting a CommandMessage and got");
            logger.error(json);

            // TODO: figure out a real scheme for errorCode and errorMessages
            message = new ErrorMessage()
                    .withData(new ErrorData().withErrorCode(123)
                                             .withErrorMessage("Invalid JSON Received")
                                             .withErrorDescription("Was expecting an installIngressFlow, " +
                                                     "installEgressFlow or installTransitFlow."))
                    .withType(Message.Type.ERROR)
                    .withTimestamp(now());
            return mapper.writeValueAsString(message);
        }

        CommandMessage cmdMessage = (CommandMessage) message;
        CommandData data = cmdMessage.getData();
        if (!(data instanceof InstallIngressFlow || data instanceof InstallTransitFlow
                || data instanceof InstallEgressFlow)) {
            logger.error("Message was not correct type: " + json);
            logger.debug("Class was " + data.getClass().getCanonicalName());
            message = new ErrorMessage()
                    .withData(new ErrorData().withErrorCode(123)
                                             .withErrorMessage("Invalid JSON Received")
                                             .withErrorDescription("Command was not of the correct type"))
                    .withType(Message.Type.ERROR)
                    .withTimestamp(now());
            return mapper.writeValueAsString(message);
        }

        return mapper.writeValueAsString("ok");
    }

    private long now() {
        return System.currentTimeMillis() / 1000L;
    }
}
