package org.bitbucket.openkilda.floodlight.switchmanager.web;

import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.floodlight.switchmanager.ISwitchManager;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;

import org.apache.commons.httpclient.HttpStatus;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by atopilin on 22/04/2017.
 */
public class MetersResource extends ServerResource {
    private static final Logger logger = LoggerFactory.getLogger(MetersResource.class);

    @Get("json")
    @SuppressWarnings("unchecked")
    public Map<Long, Object> getMeters() {
        Map<Long, Object> response = new HashMap<>();
        String switchId = (String) this.getRequestAttributes().get("switch_id");
        logger.debug("Get meters for switch: {}", switchId);
        ISwitchManager switchManager = (ISwitchManager) getContext().getAttributes()
                .get(ISwitchManager.class.getCanonicalName());

        try {
            OFMeterConfigStatsReply replay = switchManager.dumpMeters(DatapathId.of(switchId));
            logger.debug("Meters from switch {} received: {}", switchId, replay);

            if (replay != null) {
                for (OFMeterConfig entry : replay.getEntries()) {
                    response.put(entry.getMeterId(), entry);
                }
            }
        } catch (IllegalArgumentException exception) {
            logger.error("No such switch: {}", switchId, exception);
            int code = HttpStatus.SC_BAD_REQUEST;
            Message responseMessage = new ErrorMessage(new ErrorData(code, HttpStatus.getStatusText(code),
                    ErrorType.PARAMETERS_INVALID, exception.getMessage()),
                    System.currentTimeMillis(), DEFAULT_CORRELATION_ID);
            response.putAll(MAPPER.convertValue(responseMessage, Map.class));
        }
        return response;
    }
}
