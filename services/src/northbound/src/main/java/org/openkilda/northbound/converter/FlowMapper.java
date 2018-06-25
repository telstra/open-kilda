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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.info.flow.FlowVerificationErrorCode;
import org.openkilda.messaging.info.flow.FlowVerificationResponse;
import org.openkilda.northbound.dto.flows.VerificationOutput;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FlowMapper {
    VerificationOutput toVerificationOutput(FlowVerificationResponse response);

    /**
     * Translate Java's error code(enum) into human readable string.
     */
    default String getVerificationError(FlowVerificationErrorCode error) {
        if (error == null) {
            return null;
        }

        String message;
        switch (error) {
            case TIMEOUT:
                message = "No ping for reasonable time";
                break;
            case WRITE_FAILURE:
                message = "Can't send ping";
                break;
            case NOT_CAPABLE:
                message = "Unable to perform flow verification due to unsupported switch (at least one)";
                break;
            default:
                message = error.toString();
        }

        return message;
    }
}
