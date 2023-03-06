/* Copyright 2022 Telstra Open Source
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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.model.Ping;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class PingMapper {

    public static final String TIMEOUT_ERROR_MESSAGE = "No ping for reasonable time";
    public static final String WRITE_ERROR_MESSAGE = "Can't send ping";
    public static final String NOT_CAPABLE_ERROR_MESSAGE =
            "Can't ping - at least one of endpoints are not capable to catch pings.";
    public static final String ENDPOINT_NOT_AVAILABLE_ERROR_MESSAGE =
            "Can't ping - at least one of endpoints are unavailable";

    /**
     * Translate Java's error code(enum) into human readable string.
     */
    public String getPingError(Ping.Errors error) {
        if (error == null) {
            return null;
        }

        switch (error) {
            case TIMEOUT:
                return TIMEOUT_ERROR_MESSAGE;
            case WRITE_FAILURE:
                return WRITE_ERROR_MESSAGE;
            case NOT_CAPABLE:
                return NOT_CAPABLE_ERROR_MESSAGE;
            case SOURCE_NOT_AVAILABLE:
            case DEST_NOT_AVAILABLE:
                return ENDPOINT_NOT_AVAILABLE_ERROR_MESSAGE;
            default:
                return error.toString();
        }
    }
}
