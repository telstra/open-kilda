package org.openkilda.floodlight.switchmanager.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.floodlightcontroller.core.IOFSwitch;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchOperationException;
import org.openkilda.messaging.command.switches.EnableBfdRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageError;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.openkilda.messaging.Utils.MAPPER;

public class EnableBfdResource extends ServerResource {
    private static final Logger logger = LoggerFactory.getLogger(EnableBfdResource.class);

    @Post("json")
    @Put("json")
    public String enableBfd(String json) {
        ISwitchManager switchManager = (ISwitchManager) getContext().getAttributes()
                .get(ISwitchManager.class.getCanonicalName());
        EnableBfdRequest request;

        try {
            request = MAPPER.readValue(json, EnableBfdRequest.class);
        } catch (IOException e) {
            logger.error("Message received is not valid BFD Request: {}", json);
            MessageError responseMessage = new MessageError(DEFAULT_CORRELATION_ID, now(),
                    ErrorType.DATA_INVALID.toString(), "Message received is not valid BFD Request",
                    e.getMessage());
            return generateJson(responseMessage);
        }

        logger.debug("calling switchManager.startBFD");
        switchManager.startBfd(DatapathId.of(request.getSrcSw()), DatapathId.of(request.getDstSw()),
                request.getInterval(), new Integer(request.getKeepAliveTimeout()).shortValue(),
                new Integer(request.getMultiplier()).shortValue(), request.getDiscriminator(),
                OFPort.of(request.getSrcPort()));

        return generateJson("ok2");
    }

    private String generateJson(Object input) {
        try {
            return MAPPER.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            logger.error("Error processing into JSON", e);
            return "Error occurred";
        }
    }

    private long now() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }
}
