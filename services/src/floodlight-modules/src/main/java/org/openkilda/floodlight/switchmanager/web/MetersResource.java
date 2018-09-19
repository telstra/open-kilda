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

package org.openkilda.floodlight.switchmanager.web;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchOperationException;
import org.openkilda.floodlight.switchmanager.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageError;

import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetersResource extends ServerResource {
    private static final Logger logger = LoggerFactory.getLogger(MetersResource.class);

    // FIXME(surabujin): is it used anywhere?
    @Get("json")
    @SuppressWarnings("unchecked")
    public Map<Long, Object> getMeters() {
        Map<Long, Object> response = new HashMap<>();
        String switchId = (String) this.getRequestAttributes().get("switch_id");
        logger.debug("Get meters for switch: {}", switchId);
        ISwitchManager switchManager = (ISwitchManager) getContext().getAttributes()
                .get(ISwitchManager.class.getCanonicalName());

        try {
            List<OFMeterConfig> meters = switchManager.dumpMeters(DatapathId.of(switchId));

            if (meters != null) {
                logger.debug("Meters from switch {} received: {}", switchId, meters.size());

                for (OFMeterConfig entry : meters) {
                    response.put(entry.getMeterId(), entry);
                }
            }
        } catch (UnsupportedSwitchOperationException ex) {
            String messageString = "Not supported";
            logger.error("{}: {}", messageString, switchId, ex);
            MessageError responseMessage = new MessageError(CorrelationContext.getId(), System.currentTimeMillis(),
                    ErrorType.PARAMETERS_INVALID.toString(), messageString, ex.getMessage());
            response.putAll(MAPPER.convertValue(responseMessage, Map.class));

            getResponse().setStatus(Status.SERVER_ERROR_NOT_IMPLEMENTED);

        } catch (IllegalArgumentException | SwitchOperationException exception) {
            String messageString = "No such switch";
            logger.error("{}: {}", messageString, switchId, exception);
            MessageError responseMessage = new MessageError(CorrelationContext.getId(), System.currentTimeMillis(),
                    ErrorType.PARAMETERS_INVALID.toString(), messageString, exception.getMessage());
            response.putAll(MAPPER.convertValue(responseMessage, Map.class));

            getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND);
        }
        return response;
    }
}
