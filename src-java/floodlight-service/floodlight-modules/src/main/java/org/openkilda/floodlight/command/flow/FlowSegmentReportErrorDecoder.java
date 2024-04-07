/* Copyright 2024 Telstra Open Source
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

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.FlowErrorResponseBuilder;

import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.errormsg.OFBadActionErrorMsg;
import org.projectfloodlight.openflow.protocol.errormsg.OFFlowModFailedErrorMsg;

/**
 * Utility class for decoding OF error messages.
 */
public abstract class FlowSegmentReportErrorDecoder {

    /**
     * Decode OF error message and fill error response.
     *
     * @param errorResponse error response
     * @param error         OF error message
     * @return true if error decoded successfully, false otherwise
     */
    public static boolean decodeError(FlowErrorResponseBuilder errorResponse, OFErrorMsg error) {
        boolean result = true;
        if (error instanceof OFFlowModFailedErrorMsg) {
            decodeError(errorResponse, (OFFlowModFailedErrorMsg) error);
        } else if (error instanceof OFBadActionErrorMsg) {
            decodeError(errorResponse, (OFBadActionErrorMsg) error);
        } else {
            errorResponse.errorCode(ErrorCode.UNKNOWN);
            result = false;
        }
        return result;
    }

    private static void decodeError(FlowErrorResponseBuilder errorResponse, OFFlowModFailedErrorMsg error) {
        switch (error.getCode()) {
            case UNSUPPORTED:
                errorResponse.errorCode(ErrorCode.UNSUPPORTED);
                break;
            case BAD_COMMAND:
                errorResponse.errorCode(ErrorCode.BAD_COMMAND);
                break;
            case BAD_FLAGS:
                errorResponse.errorCode(ErrorCode.BAD_FLAGS);
                break;
            default:
                errorResponse.errorCode(ErrorCode.UNKNOWN);
        }
    }

    private static void decodeError(FlowErrorResponseBuilder errorResponse, OFBadActionErrorMsg error) {
        switch (error.getCode()) {
            case BAD_OUT_PORT:
                errorResponse.errorCode(ErrorCode.BAD_OUT_PORT);
                break;
            default:
                errorResponse.errorCode(ErrorCode.UNKNOWN);
        }
    }

}
