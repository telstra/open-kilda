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

package org.openkilda.northbound.controller;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.northbound.utils.RequestCorrelationId;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.Optional;
import java.util.stream.Stream;

@ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Operation is successful"),
        @ApiResponse(responseCode = "400", description = "Invalid input data",
                content = @Content(mediaType = "application/json", schema =
        @Schema(implementation = MessageError.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
                content = @Content(mediaType = "application/json", schema =
                @Schema(implementation = MessageError.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden",
                content = @Content(mediaType = "application/json", schema =
                @Schema(implementation = MessageError.class))),
        @ApiResponse(responseCode = "404", description = "Not found",
                content = @Content(mediaType = "application/json", schema =
                @Schema(implementation = MessageError.class))),
        @ApiResponse(responseCode = "500", description = "General error",
                content = @Content(mediaType = "application/json", schema =
                @Schema(implementation = MessageError.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                content = @Content(mediaType = "application/json", schema =
                @Schema(implementation = MessageError.class)))
})
public class BaseController {
    protected void exposeBodyValidationResults(Stream<Optional<String>> defectStream, final String errorMsg) {
        String[] defects = defectStream
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toArray(String[]::new);
        if (defects.length != 0) {
            String errorDescription = "Errors: "
                    + String.join(", ", defects);
            throw new MessageException(RequestCorrelationId.getId(), System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    errorMsg, errorDescription);
        }
    }
}
