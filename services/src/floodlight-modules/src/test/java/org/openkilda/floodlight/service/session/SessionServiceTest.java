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

import org.openkilda.floodlight.error.SessionCloseException;
import org.openkilda.floodlight.error.SessionErrorResponseException;
import org.openkilda.floodlight.error.SessionRevertException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.SwitchWriteException;
import org.openkilda.floodlight.service.of.InputService;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBadActionCode;
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

public class SessionServiceTest extends EasyMockSupport {
    private final SessionService subject = new SessionService();
    private final FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    private final DatapathId dpId = DatapathId.of(0xfffe000000000001L);

    private final Capture<OFMessage> swWriteMessages = EasyMock.newCapture(CaptureType.ALL);

    @Mock
    private InputService inputService;

    @Mock
    private IOFSwitchService ofSwitchService;

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        // configure mocks
        inputService.addTranslator(OFType.ERROR, subject);
        inputService.addTranslator(OFType.BARRIER_REPLY, subject);

        ofSwitchService.addOFSwitchListener(anyObject(SwitchEventsTranslator.class));

        // fill FL's module context
        moduleContext.addService(InputService.class, inputService);
        moduleContext.addService(IOFSwitchService.class, ofSwitchService);
    }

    @After
    public void tearDown() throws Exception {
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

        try (Session session = subject.open(sw)) {
            future = session.write(pktOut);
        }
        Assert.assertFalse(future.isDone());

        List<OFMessage> swActualWrite = swWriteMessages.getValues();
        Assert.assertEquals(2, swActualWrite.size());
        Assert.assertEquals(pktOut, swActualWrite.get(0));
        Assert.assertEquals(OFType.BARRIER_REQUEST, swActualWrite.get(1).getType());

        completeSessions(sw);

        Assert.assertTrue(future.isDone());
        Optional<OFMessage> response = future.get();
        Assert.assertFalse(response.isPresent());
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
        try (Session session = subject.open(sw)) {
            pktOutFuture = session.write(pktOut);
            barrierFuture = session.write(barrier);
        }

        Assert.assertFalse(pktOutFuture.isDone());
        Assert.assertFalse(barrierFuture.isDone());

        subject.handleResponse(sw.getId(), ofFactory.buildBarrierReply().setXid(barrier.getXid()).build());

        Assert.assertFalse(pktOutFuture.isDone());
        Assert.assertTrue(barrierFuture.isDone());

        completeSessions(sw);

        Assert.assertTrue(pktOutFuture.isDone());
        Assert.assertTrue(barrierFuture.isDone());

        Optional<OFMessage> barrierResponse = barrierFuture.get();
        Assert.assertTrue(barrierResponse.isPresent());
        Assert.assertEquals(OFType.BARRIER_REPLY, barrierResponse.get().getType());
        Assert.assertEquals(barrier.getXid(), barrierResponse.get().getXid());
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
        try (Session session = subject.open(sw)) {
            future = session.write(pktOut);
        }

        Assert.assertFalse(future.isDone());

        subject.handleResponse(sw.getId(), ofFactory.errorMsgs().buildBadActionErrorMsg()
                .setXid(pktOut.getXid())
                .setCode(OFBadActionCode.BAD_LEN)
                .build());

        Assert.assertTrue(future.isDone());

        completeSessions(sw);

        expectExceptionResponse(future, SessionErrorResponseException.class);
    }

    @Test
    public void switchWriteError() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        setupSwitchMock(sw, dpId);
        swWriteSecondFail(sw);
        doneWithSetUp(sw);

        OFFactory ofFactory = sw.getOFFactory();
        CompletableFuture<Optional<OFMessage>> futureAlpha = null;
        CompletableFuture<Optional<OFMessage>> futureBeta = null;
        try {
            try (Session session = subject.open(sw)) {
                futureAlpha = session.write(makePacketOut(ofFactory, 1));
                futureBeta = session.write(makePacketOut(ofFactory, 2));
            }

            throw new AssertionError("Expect exception to be thrown");
        } catch (SwitchWriteException e) {
            Assert.assertTrue(futureAlpha != null);
            expectExceptionResponse(futureAlpha, SessionRevertException.class);
            Assert.assertTrue(futureBeta == null);
        }
    }

    @Test
    public void sessionBarrierWriteError() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        setupSwitchMock(sw, dpId);
        swWriteSecondFail(sw);
        doneWithSetUp(sw);

        CompletableFuture<Optional<OFMessage>> future = null;
        try {
            try (Session session = subject.open(sw)) {
                future = session.write(makePacketOut(sw.getOFFactory(), 1));
            }
            throw new AssertionError("Expect exception to be thrown");
        } catch (SwitchWriteException e) {
            Assert.assertTrue(future != null);
            expectExceptionResponse(future, SessionCloseException.class);
        }
    }

    private OFPacketOut makePacketOut(OFFactory ofFactory, int inPort) {
        return ofFactory.buildPacketOut()
                .setInPort(OFPort.of(inPort))
                .build();
    }

    private void expectExceptionResponse(CompletableFuture<Optional<OFMessage>> future,
                                         Class<? extends SwitchOperationException> klass) throws Exception {
        Assert.assertTrue(future.isDone());
        try {
            future.get();
            throw new AssertionError("Expect exception to be thrown");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertTrue(klass.isInstance(cause));
        }
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
        subject.switchConnect(sw.getId());
    }

    private void completeSessions(IOFSwitch sw) {
        List<OFMessage> requests = swWriteMessages.getValues();
        Assert.assertTrue(0 < requests.size());

        OFMessage barrier = requests.get(requests.size() - 1);
        Assert.assertEquals(OFType.BARRIER_REQUEST, barrier.getType());
        subject.handleResponse(sw.getId(), sw.getOFFactory().buildBarrierReply().setXid(barrier.getXid()).build());
    }
}
