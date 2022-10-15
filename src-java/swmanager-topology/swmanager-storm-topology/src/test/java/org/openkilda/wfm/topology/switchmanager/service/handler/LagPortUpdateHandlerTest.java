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

package org.openkilda.wfm.topology.switchmanager.service.handler;

import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.grpc.CreateOrUpdateLogicalPortRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.grpc.CreateOrUpdateLogicalPortResponse;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.LogicalPortType;
import org.openkilda.messaging.swmanager.request.UpdateLagPortRequest;
import org.openkilda.messaging.swmanager.response.LagPortResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.switchmanager.error.SwitchManagerException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.model.LagRollbackData;
import org.openkilda.wfm.topology.switchmanager.service.LagPortOperationService;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;

@RunWith(MockitoJUnitRunner.class)
public class LagPortUpdateHandlerTest {
    @Mock
    private SwitchManagerCarrier carrier;

    @Mock
    private LagPortOperationService operationService;

    @Test
    public void testHappyPath() {
        String requestKey = "testRequest";
        String swAddress = "127.0.3.1";

        UpdateLagPortRequest request = newRequest();
        LagPortUpdateHandler subject = new LagPortUpdateHandler(carrier, operationService, requestKey, request);
        MessageCookie grpcRequestCookie = verifyStartHandler(subject, request, swAddress);

        // GRPC success response
        subject.dispatchGrpcResponse(
                new CreateOrUpdateLogicalPortResponse(
                        swAddress,
                        LogicalPort.builder()
                                .logicalPortNumber(request.getLogicalPortNumber())
                                .portNumbers(request.getTargetPorts())
                                .name("dummy")
                                .type(LogicalPortType.LAG)
                                .build(),
                        true),
                grpcRequestCookie);

        Mockito.verifyNoMoreInteractions(operationService);
        Mockito.verify(carrier).response(
                Mockito.eq(requestKey),
                Mockito.eq(new LagPortResponse(request.getLogicalPortNumber(), request.getTargetPorts(),
                        request.isLacpReply())));
        Mockito.verifyNoMoreInteractions(carrier);

        Assert.assertTrue(subject.isCompleted());
    }

    @Test
    public void testGrpcErrorResponse() {
        String requestKey = "testRequest";
        String swAddress = "127.0.3.1";

        UpdateLagPortRequest request = newRequest();
        LagPortUpdateHandler subject = new LagPortUpdateHandler(carrier, operationService, requestKey, request);
        MessageCookie grpcRequestCookie = verifyStartHandler(subject, request, swAddress);

        // GRPC error response
        ErrorData error = new ErrorData(ErrorType.INTERNAL_ERROR, "Dummy error message", "Dummy error description");
        subject.dispatchErrorResponse(error, grpcRequestCookie);
        Mockito.verify(operationService).updateLagPort(
                Mockito.eq(request.getSwitchId()), Mockito.eq(request.getLogicalPortNumber()),
                Mockito.eq(subject.rollbackData.getPhysicalPorts()),
                Mockito.eq(subject.rollbackData.isLacpReply()));
        Mockito.verifyNoMoreInteractions(operationService);
        Mockito.verify(carrier).errorResponse(
                Mockito.eq(requestKey), Mockito.eq(error.getErrorType()), Mockito.anyString(), Mockito.anyString());
        Mockito.verifyNoMoreInteractions(carrier);

        Assert.assertTrue(subject.isCompleted());
    }

    @Test
    public void testTimeout() {
        String requestKey = "testRequest";
        String swAddress = "127.0.3.1";

        UpdateLagPortRequest request = newRequest();
        LagPortUpdateHandler subject = new LagPortUpdateHandler(carrier, operationService, requestKey, request);
        verifyStartHandler(subject, request, swAddress);

        // GRPC error response
        subject.timeout();
        Mockito.verify(operationService).updateLagPort(
                Mockito.eq(request.getSwitchId()), Mockito.eq(request.getLogicalPortNumber()),
                Mockito.eq(subject.rollbackData.getPhysicalPorts()),
                Mockito.eq(subject.rollbackData.isLacpReply()));
        Mockito.verifyNoMoreInteractions(operationService);
        Mockito.verify(carrier).errorResponse(
                Mockito.eq(requestKey), Mockito.eq(ErrorType.OPERATION_TIMED_OUT),
                Mockito.anyString(), Mockito.anyString());
        Mockito.verifyNoMoreInteractions(carrier);

        Assert.assertTrue(subject.isCompleted());
    }

    @Test
    public void testExceptionOnErrorFromOperationService() {
        String requestKey = "testRequest";

        UpdateLagPortRequest request = newRequest();
        LagPortUpdateHandler subject = new LagPortUpdateHandler(carrier, operationService, requestKey, request);

        Mockito.when(operationService.getSwitchIpAddress(request.getSwitchId())).thenThrow(
                new SwitchNotFoundException(request.getSwitchId()));
        Assert.assertThrows(SwitchManagerException.class, subject::start);
    }

    private MessageCookie verifyStartHandler(
            LagPortUpdateHandler subject, UpdateLagPortRequest request, String swAddress) {
        LagRollbackData existingTargets = LagRollbackData.builder()
                .physicalPorts(ImmutableSet.of(3, 4, 5)).lacpReply(true).build();
        Mockito.when(operationService.getSwitchIpAddress(request.getSwitchId())).thenReturn(swAddress);
        Mockito.when(operationService.updateLagPort(
                        Mockito.eq(request.getSwitchId()), Mockito.eq(request.getLogicalPortNumber()), Mockito.any(),
                        Mockito.eq(request.isLacpReply())))
                .thenReturn(existingTargets);

        Mockito.verifyNoInteractions(carrier);
        Mockito.verifyNoInteractions(operationService);

        // DB update and GRPC request
        subject.start();
        Assert.assertFalse(subject.isCompleted());
        Assert.assertEquals(existingTargets, subject.rollbackData);

        Mockito.verify(operationService).getSwitchIpAddress(Mockito.eq(request.getSwitchId()));
        Mockito.verify(operationService).updateLagPort(
                Mockito.eq(request.getSwitchId()), Mockito.eq(request.getLogicalPortNumber()),
                Mockito.eq(new HashSet<>(request.getTargetPorts())),
                Mockito.eq(request.isLacpReply()));
        Mockito.verifyNoMoreInteractions(operationService);

        ArgumentCaptor<MessageCookie> grpcRequestCookie = ArgumentCaptor.forClass(MessageCookie.class);
        Mockito.verify(carrier).sendCommandToSpeaker(
                Mockito.eq(new CreateOrUpdateLogicalPortRequest(
                        swAddress, request.getTargetPorts(), request.getLogicalPortNumber(), LogicalPortType.LAG)),
                grpcRequestCookie.capture());
        Mockito.verifyNoMoreInteractions(carrier);

        return grpcRequestCookie.getValue();
    }

    private UpdateLagPortRequest newRequest() {
        return new UpdateLagPortRequest(new SwitchId(1), 2001, Sets.newHashSet(1, 2, 3), true);
    }
}
