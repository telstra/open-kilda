/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.utils;

import static org.openkilda.messaging.Utils.CORRELATION_ID;

import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Form;
import org.restlet.routing.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Restlet filter which initializes the correlation context with either provided "correlation_id" (HTTP header) or a new one.
 */
public class RequestCorrelationFilter extends Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected int doHandle(Request request, Response response) {
        Form headers = (Form) request.getAttributes().get("org.restlet.http.headers");
        String correlationId = headers.getFirstValue(CORRELATION_ID);

        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            LOGGER.warn("CorrelationId was not sent, generated one: {}", correlationId);
        } else {
            LOGGER.debug("Found correlationId in header: {}", correlationId);
        }

        try (CorrelationContextClosable closable = CorrelationContext.create(correlationId)) {
            return super.doHandle(request, response);
        }
    }
}
