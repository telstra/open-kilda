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

import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.FlowErrorResponseBuilder;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.projectfloodlight.openflow.protocol.OFBadActionCode;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFlowModFailedCode;
import org.projectfloodlight.openflow.protocol.ver13.OFErrorMsgsVer13;

import java.util.UUID;

public class FlowErrorResponseTest {

    @Test
    public void decodeErrorBadOutPort() {
        FlowErrorResponseBuilder errorResponse = makeErrorTemplate();
        ErrorCode expectedErrorCode =  ErrorCode.BAD_OUT_PORT;

        OFErrorMsg errorMsg = OFErrorMsgsVer13.INSTANCE.buildBadActionErrorMsg()
                .setCode(OFBadActionCode.BAD_OUT_PORT).build();
        boolean result = FlowSegmentReportErrorDecoder.decodeError(errorResponse, errorMsg);

        Assertions.assertTrue(result);
        Assertions.assertEquals(expectedErrorCode, errorResponse.build().getErrorCode());
    }

    @Test
    public void decodeErrorUnsupported() {
        FlowErrorResponseBuilder errorResponse = makeErrorTemplate();
        ErrorCode expectedErrorCode =  ErrorCode.UNSUPPORTED;

        OFErrorMsg errorMsg = OFErrorMsgsVer13.INSTANCE.buildFlowModFailedErrorMsg()
                .setCode(OFFlowModFailedCode.UNSUPPORTED).build();
        boolean result = FlowSegmentReportErrorDecoder.decodeError(errorResponse, errorMsg);

        Assertions.assertTrue(result);
        Assertions.assertEquals(expectedErrorCode, errorResponse.build().getErrorCode());
    }

    @Test
    public void decodeErrorBadCommand() {
        FlowErrorResponseBuilder errorResponse = makeErrorTemplate();
        ErrorCode expectedErrorCode =  ErrorCode.BAD_COMMAND;

        OFErrorMsg errorMsg = OFErrorMsgsVer13.INSTANCE.buildFlowModFailedErrorMsg()
                .setCode(OFFlowModFailedCode.BAD_COMMAND).build();
        boolean result = FlowSegmentReportErrorDecoder.decodeError(errorResponse, errorMsg);

        Assertions.assertTrue(result);
        Assertions.assertEquals(expectedErrorCode, errorResponse.build().getErrorCode());
    }

    @Test
    public void decodeErrorBadFlags() {
        FlowErrorResponseBuilder errorResponse = makeErrorTemplate();
        ErrorCode expectedErrorCode =  ErrorCode.BAD_FLAGS;

        OFErrorMsg errorMsg = OFErrorMsgsVer13.INSTANCE.buildFlowModFailedErrorMsg()
                .setCode(OFFlowModFailedCode.BAD_FLAGS).build();
        boolean result = FlowSegmentReportErrorDecoder.decodeError(errorResponse, errorMsg);

        Assertions.assertTrue(result);
        Assertions.assertEquals(expectedErrorCode, errorResponse.build().getErrorCode());
    }

    @Test
    public void decodeErrorUnknownFlowModError() {
        FlowErrorResponseBuilder errorResponse = makeErrorTemplate();
        ErrorCode expectedErrorCode =  ErrorCode.UNKNOWN;

        OFErrorMsg errorMsg = OFErrorMsgsVer13.INSTANCE.buildFlowModFailedErrorMsg()
                .setCode(OFFlowModFailedCode.EPERM).build();
        boolean result = FlowSegmentReportErrorDecoder.decodeError(errorResponse, errorMsg);

        Assertions.assertTrue(result);
        Assertions.assertEquals(expectedErrorCode, errorResponse.build().getErrorCode());
    }

    @Test
    public void decodeErrorUnknownBadActionError() {
        FlowErrorResponseBuilder errorResponse = makeErrorTemplate();
        ErrorCode expectedErrorCode =  ErrorCode.UNKNOWN;

        OFErrorMsg errorMsg = OFErrorMsgsVer13.INSTANCE.buildBadActionErrorMsg()
                .setCode(OFBadActionCode.EPERM).build();
        boolean result = FlowSegmentReportErrorDecoder.decodeError(errorResponse, errorMsg);

        Assertions.assertTrue(result);
        Assertions.assertEquals(expectedErrorCode, errorResponse.build().getErrorCode());
    }

    @Test
    public void decodeErrorUnknownError() {
        FlowErrorResponseBuilder errorResponse = makeErrorTemplate();
        ErrorCode expectedErrorCode =  ErrorCode.UNKNOWN;

        OFErrorMsg errorMsg = OFErrorMsgsVer13.INSTANCE.buildBsnError().build();
        boolean result = FlowSegmentReportErrorDecoder.decodeError(errorResponse, errorMsg);

        Assertions.assertFalse(result);
        Assertions.assertEquals(expectedErrorCode, errorResponse.build().getErrorCode());
    }

    private FlowErrorResponseBuilder makeErrorTemplate() {
        return FlowErrorResponse.errorBuilder()
                .messageContext(new MessageContext())
                .commandId(UUID.randomUUID())
                .switchId(new SwitchId(1))
                .metadata(new FlowSegmentMetadata("test", new Cookie(1)));
    }
}
