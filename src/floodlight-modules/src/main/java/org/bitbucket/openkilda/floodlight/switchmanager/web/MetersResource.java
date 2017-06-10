package org.bitbucket.openkilda.floodlight.switchmanager.web;

import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.floodlight.switchmanager.ISwitchManager;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.error.MessageError;

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
            MessageError responseMessage = new MessageError(DEFAULT_CORRELATION_ID, System.currentTimeMillis(), code,
                    HttpStatus.getStatusText(code), ErrorType.PARAMETERS_INVALID.toString(), exception.getMessage());
            response.putAll(MAPPER.convertValue(responseMessage, Map.class));
        }
        return response;
    }
}
