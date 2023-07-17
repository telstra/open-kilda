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

package org.openkilda.floodlight.service.session;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.floodlight.error.SessionCloseException;
import org.openkilda.floodlight.error.SessionErrorResponseException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.SwitchWriteException;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.messaging.MessageContext;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectfloodlight.openflow.protocol.OFBadActionCode;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SessionServiceTest extends EasyMockSupport {
    private final SessionService subject = new SessionService();
    private final FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    private final DatapathId dpId = DatapathId.of(0xfffe000000000001L);
    private final MessageContext context = new MessageContext();

    private final Capture<OFMessage> swWriteMessages = EasyMock.newCapture(CaptureType.ALL);

    @Mock
    private InputService inputService;

    @Mock
    private IOFSwitchService ofSwitchService;

    @BeforeEach
    public void setUp() {
        injectMocks(this);

        // configure mocks
        inputService.addTranslator(OFType.ERROR, subject);
        inputService.addTranslator(OFType.BARRIER_REPLY, subject);

        ofSwitchService.addOFSwitchListener(anyObject(SwitchEventsTranslator.class));

        // fill FL's module context
        moduleContext.addService(InputService.class, inputService);
        moduleContext.addService(IOFSwitchService.class, ofSwitchService);
    }

    @AfterEach
    public void tearDown() {
        verifyAll();
    }

    @Test
    public void positive() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        setupSwitchMock(sw, dpId);
        swWriteAlwaysSuccess(sw);
        doneWithSetUp(sw);

        CompletableFuture<Optional<OFMessage>> future;
        OFPacketOut pktOut = makePacketOut(sw.getOFFactory(), 1);

        try (Session session = subject.open(context, sw)) {
            future = session.write(pktOut);
        }
        Assertions.assertFalse(future.isDone());

        List<OFMessage> swActualWrite = swWriteMessages.getValues();
        assertEquals(2, swActualWrite.size());
        assertEquals(pktOut, swActualWrite.get(0));
        assertEquals(OFType.BARRIER_REQUEST, swActualWrite.get(1).getType());

        completeSessions(sw);

        Assertions.assertTrue(future.isDone());
        Optional<OFMessage> response = future.get();
        Assertions.assertFalse(response.isPresent());
    }

    @Test
    public void barrierInTheMiddle() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        setupSwitchMock(sw, dpId);
        swWriteAlwaysSuccess(sw);
        doneWithSetUp(sw);

        OFFactory ofFactory = sw.getOFFactory();
        OFPacketOut pktOut = makePacketOut(ofFactory, 1);
        OFBarrierRequest barrier = ofFactory.barrierRequest();

        CompletableFuture<Optional<OFMessage>> pktOutFuture;
        CompletableFuture<Optional<OFMessage>> barrierFuture;
        try (Session session = subject.open(context, sw)) {
            pktOutFuture = session.write(pktOut);
            barrierFuture = session.write(barrier);
        }

        Assertions.assertFalse(pktOutFuture.isDone());
        Assertions.assertFalse(barrierFuture.isDone());

        subject.handleResponse(sw.getId(), ofFactory.buildBarrierReply().setXid(barrier.getXid()).build());

        Assertions.assertFalse(pktOutFuture.isDone());
        Assertions.assertTrue(barrierFuture.isDone());

        completeSessions(sw);

        Assertions.assertTrue(pktOutFuture.isDone());
        Assertions.assertTrue(barrierFuture.isDone());

        Optional<OFMessage> barrierResponse = barrierFuture.get();
        Assertions.assertTrue(barrierResponse.isPresent());
        assertEquals(OFType.BARRIER_REPLY, barrierResponse.get().getType());
        assertEquals(barrier.getXid(), barrierResponse.get().getXid());
    }

    @Test
    public void errorResponse() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        setupSwitchMock(sw, dpId);
        swWriteAlwaysSuccess(sw);
        doneWithSetUp(sw);

        OFFactory ofFactory = sw.getOFFactory();
        OFPacketOut pktOut = makePacketOut(ofFactory, 1);
        CompletableFuture<Optional<OFMessage>> future;
        try (Session session = subject.open(context, sw)) {
            future = session.write(pktOut);
        }

        Assertions.assertFalse(future.isDone());

        subject.handleResponse(sw.getId(), ofFactory.errorMsgs().buildBadActionErrorMsg()
                .setXid(pktOut.getXid())
                .setCode(OFBadActionCode.BAD_LEN)
                .build());

        Assertions.assertTrue(future.isDone());

        completeSessions(sw);

        expectExceptionResponse(future, SessionErrorResponseException.class);
    }

    @Test
    public void switchWriteError() throws Throwable {
        IOFSwitch sw = createMock(IOFSwitch.class);
        setupSwitchMock(sw, dpId);
        swWriteSecondFail(sw);
        doneWithSetUp(sw);

        OFFactory ofFactory = sw.getOFFactory();
        CompletableFuture<Optional<OFMessage>> future = null;
        try (Session session = subject.open(context, sw)) {
            session.write(makePacketOut(ofFactory, 1));
            future = session.write(makePacketOut(ofFactory, 2));
        }

        try {
            future.get(1, TimeUnit.SECONDS);
            Assertions.fail();
        } catch (ExecutionException e) {
            Assertions.assertNotNull(e.getCause());
            Assertions.assertTrue(e.getCause() instanceof SwitchWriteException);
        }
    }

    @Test
    public void responseBeforeClose() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        setupSwitchMock(sw, dpId);
        swWriteAlwaysSuccess(sw);
        doneWithSetUp(sw);

        OFFactory ofFactory = sw.getOFFactory();
        CompletableFuture<Optional<OFMessage>> first;
        CompletableFuture<Optional<OFMessage>> second;
        try (Session session = subject.open(context, sw)) {
            OFPacketOut egress = makePacketOut(ofFactory, 100);
            first = session.write(egress);

            second = session.write(makePacketOut(ofFactory, 1));

            // response before close
            OFMessage ingress = ofFactory.errorMsgs().buildBadRequestErrorMsg()
                    .setXid(egress.getXid())
                    .setCode(OFBadRequestCode.BAD_PORT)
                    .build();
            subject.handleResponse(dpId, ingress);
        }

        completeSessions(sw);

        // received error response must be reported via future
        expectExceptionResponse(first, SessionErrorResponseException.class);
        // second request must be marker as completed
        expectNoResponse(second);
    }

    @Test
    public void sessionBarrierWriteError() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        setupSwitchMock(sw, dpId);
        swWriteSecondFail(sw);
        doneWithSetUp(sw);

        CompletableFuture<Optional<OFMessage>> future = null;
        try (Session session = subject.open(context, sw)) {
            future = session.write(makePacketOut(sw.getOFFactory(), 1));
        }

        try {
            future.get(1, TimeUnit.SECONDS);
            Assertions.fail();
        } catch (ExecutionException e) {
            Assertions.assertNotNull(e.getCause());
            Assertions.assertTrue(e.getCause() instanceof SessionCloseException);
        }
    }

    private OFPacketOut makePacketOut(OFFactory ofFactory, int inPort) {
        return ofFactory.buildPacketOut()
                .setInPort(OFPort.of(inPort))
                .build();
    }

    private void expectExceptionResponse(CompletableFuture<Optional<OFMessage>> future,
                                         Class<? extends SwitchOperationException> klass) throws Exception {
        Assertions.assertTrue(future.isDone());
        try {
            future.get();
            throw new AssertionError("Expect exception to be thrown");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assertions.assertTrue(klass.isInstance(cause));
        }
    }

    private void expectNoResponse(CompletableFuture<Optional<OFMessage>> future)
            throws ExecutionException, InterruptedException {
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.get().isPresent());
    }

    private void setupSwitchMock(IOFSwitch mock, DatapathId dpId) {
        expect(mock.getId()).andStubReturn(dpId);
        expect(mock.getOFFactory()).andStubReturn(OFFactoryVer13.INSTANCE);
        expect(mock.getLatency()).andStubReturn(U64.of(50));
    }

    private void swWriteAlwaysSuccess(IOFSwitch mock) {
        expect(mock.write(capture(swWriteMessages))).andStubReturn(true);
    }

    private void swWriteSecondFail(IOFSwitch mock) {
        expect(mock.write(capture(swWriteMessages))).andReturn(true);
        expect(mock.write(anyObject(OFMessage.class))).andReturn(false);
        expect(mock.write(capture(swWriteMessages))).andStubReturn(true);
    }

    private void doneWithSetUp(IOFSwitch sw) {
        replayAll();

        subject.setup(moduleContext);
        subject.switchActivate(sw.getId());
    }

    private void completeSessions(IOFSwitch sw) {
        List<OFMessage> requests = swWriteMessages.getValues();
        Assertions.assertTrue(0 < requests.size());

        OFMessage barrier = requests.get(requests.size() - 1);
        assertEquals(OFType.BARRIER_REQUEST, barrier.getType());
        subject.handleResponse(sw.getId(), sw.getOFFactory().buildBarrierReply().setXid(barrier.getXid()).build());
    }
}
