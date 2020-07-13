/* Copyright 2020 Telstra Open Source
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

package org.openkilda.floodlight.kafka.discovery;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.DiscoPacketSendingConfirmation;
import org.openkilda.model.SwitchId;
import org.openkilda.stubs.ManualClock;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.Capture;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.time.Duration;

public class NetworkDiscoveryEmitterTest extends EasyMockSupport {
    private static final String CONFIRMATION_TOPIC = "kilda.topo.disco";
    private static final String REGION = "region0";

    private static final SwitchId SWITCH_ALPHA = new SwitchId(1);
    private static final Duration flushDelay = Duration.ofMillis(10);
    private static final Duration flushHalfDelay = Duration.ofMillis(5);

    private final ManualClock clock = new ManualClock();
    private final FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    @Mock
    private IKafkaProducerService kafkaProducerService;
    @Mock
    private IPathVerificationService pathVerificationService;

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        moduleContext.addService(IKafkaProducerService.class, kafkaProducerService);
        moduleContext.addService(IPathVerificationService.class, pathVerificationService);

        KafkaChannel kafkaChannel = mock(KafkaChannel.class);
        expect(kafkaChannel.getTopoDiscoTopic()).andStubReturn(CONFIRMATION_TOPIC);
        expect(kafkaChannel.getRegion()).andStubReturn(REGION);

        KafkaUtilityService kafkaUtility = mock(KafkaUtilityService.class);
        expect(kafkaUtility.getKafkaChannel()).andStubReturn(kafkaChannel);
        moduleContext.addService(KafkaUtilityService.class, kafkaUtility);
    }

    @Test
    public void testNoDelayForSpacedOutRequests() {
        String correlationId = "dummy-correlation-id";
        DiscoverIslCommandData requestAlpha = new DiscoverIslCommandData(SWITCH_ALPHA, 1, 1L);

        expectDiscoveryEmmit(requestAlpha);
        Capture<InfoMessage> confirmationCapture = setupConfirmationCatcher(requestAlpha, 2);
        replayAll();

        NetworkDiscoveryEmitter subject = new NetworkDiscoveryEmitter(clock, moduleContext, flushDelay);
        subject.handleRequest(requestAlpha, correlationId);

        verify(pathVerificationService);
        verifyCaptured(confirmationCapture, requestAlpha);

        clock.adjust(flushDelay);
        clock.adjust(flushHalfDelay);

        subject.tick();

        DiscoverIslCommandData requestBeta = makeNextRequest(requestAlpha);

        reset(pathVerificationService);
        expectDiscoveryEmmit(requestBeta);
        confirmationCapture.reset();
        replay(pathVerificationService);

        subject.handleRequest(requestBeta, correlationId);

        verify(pathVerificationService);
        verifyCaptured(confirmationCapture, requestBeta);
    }

    @Test
    public void testSuppressAllButFirstAndLastHighRateRequests() {
        String correlationId = "dummy-correlation-id";

        DiscoverIslCommandData request = new DiscoverIslCommandData(SWITCH_ALPHA, 1, 1L);
        expectDiscoveryEmmit(request);
        Capture<InfoMessage> confirmationCapture = setupConfirmationCatcher(request, 2);
        replayAll();

        NetworkDiscoveryEmitter subject = new NetworkDiscoveryEmitter(clock, moduleContext, flushDelay);

        // first (0)
        subject.handleRequest(request, correlationId);
        subject.tick();

        verifyCaptured(confirmationCapture, request);
        verify(pathVerificationService);

        reset(pathVerificationService);
        replay(pathVerificationService);
        confirmationCapture.reset();

        clock.adjust(flushHalfDelay);
        // second (0.5)
        request = makeNextRequest(request);
        subject.handleRequest(request, correlationId);
        subject.tick();

        verify(pathVerificationService);  // no interaction
        Assert.assertFalse(confirmationCapture.hasCaptured());

        clock.adjust(flushHalfDelay);
        // third (1)
        request = makeNextRequest(request);
        subject.handleRequest(request, correlationId);
        subject.tick();

        verify(pathVerificationService);  // no interaction
        Assert.assertFalse(confirmationCapture.hasCaptured());

        clock.adjust(flushHalfDelay);
        // fourth (1.5)
        request = makeNextRequest(request);
        subject.handleRequest(request, correlationId);
        subject.tick();

        verify(pathVerificationService);  // no interaction
        Assert.assertFalse(confirmationCapture.hasCaptured());

        // wait flush (3)
        clock.adjust(flushHalfDelay);
        clock.adjust(flushDelay);

        reset(pathVerificationService);
        expectDiscoveryEmmit(request);
        replay(pathVerificationService);
        subject.tick();

        verify(pathVerificationService);
        verifyCaptured(confirmationCapture, request);
    }

    private DiscoverIslCommandData makeNextRequest(DiscoverIslCommandData current) {
        return new DiscoverIslCommandData(
                current.getSwitchId(), current.getPortNumber(), current.getPacketId() + 1);
    }

    private void verifyCaptured(Capture<InfoMessage> capture, DiscoverIslCommandData request) {
        Assert.assertTrue(capture.hasCaptured());
        InfoMessage wrapper = capture.getValue();
        Assert.assertEquals(REGION, wrapper.getRegion());
        Assert.assertTrue(wrapper.getData() instanceof DiscoPacketSendingConfirmation);

        DiscoPacketSendingConfirmation confirmation = (DiscoPacketSendingConfirmation) wrapper.getData();
        Assert.assertEquals(request.getSwitchId(), confirmation.getEndpoint().getDatapath());
        Assert.assertEquals(request.getPortNumber(), (int) confirmation.getEndpoint().getPortNumber());
        Assert.assertEquals((long) request.getPacketId(), confirmation.getPacketId());
    }

    private void expectDiscoveryEmmit(DiscoverIslCommandData request) {
        expect(pathVerificationService.sendDiscoveryMessage(
                eq(DatapathId.of(request.getSwitchId().getId())), eq(OFPort.of(request.getPortNumber())),
                eq(request.getPacketId()))).andReturn(true);
    }

    private Capture<InfoMessage> setupConfirmationCatcher(DiscoverIslCommandData request, int count) {
        Capture<InfoMessage> capture = newCapture();
        kafkaProducerService.sendMessageAndTrack(
                eq(CONFIRMATION_TOPIC), eq(request.getSwitchId().toString()), capture(capture));
        expectLastCall().times(count);
        return capture;
    }
}
