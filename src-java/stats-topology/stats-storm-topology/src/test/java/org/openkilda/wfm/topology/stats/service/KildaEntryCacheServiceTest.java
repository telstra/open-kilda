/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.stats.service;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.EGRESS;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.INGRESS;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.TRANSIT;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.Y_FLOW_SHARED;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.Y_FLOW_Y_POINT;

import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.share.flow.TestFlowBuilder;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.yflow.TestYFlowBuilder;
import org.openkilda.wfm.share.yflow.TestYSubFlowBuilder;
import org.openkilda.wfm.topology.stats.model.CommonFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.FlowStatsAndDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.MeterStatsAndDescriptor;
import org.openkilda.wfm.topology.stats.model.StatsAndDescriptor;
import org.openkilda.wfm.topology.stats.model.SwitchFlowStats;
import org.openkilda.wfm.topology.stats.model.SwitchMeterStats;
import org.openkilda.wfm.topology.stats.model.YFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowSubDescriptor;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class KildaEntryCacheServiceTest {
    private static final Long FLOW_UNMASKED_COOKIE = 1L;
    private static final FlowSegmentCookie FORWARD_PATH_COOKIE =
            new FlowSegmentCookie(FlowPathDirection.FORWARD, FLOW_UNMASKED_COOKIE);
    private static final FlowSegmentCookie REVERSE_PATH_COOKIE =
            new FlowSegmentCookie(FlowPathDirection.REVERSE, FLOW_UNMASKED_COOKIE);
    private static final Long FLOW_PROTECTED_UNMASKED_COOKIE = 2L;
    private static final FlowSegmentCookie PROTECTED_FORWARD_PATH_COOKIE =
            new FlowSegmentCookie(FlowPathDirection.FORWARD, FLOW_PROTECTED_UNMASKED_COOKIE);
    private static final FlowSegmentCookie PROTECTED_REVERSE_PATH_COOKIE =
            new FlowSegmentCookie(FlowPathDirection.REVERSE, FLOW_PROTECTED_UNMASKED_COOKIE);

    private static final long FORWARD_METER_ID = MeterId.MIN_FLOW_METER_ID + 1;
    private static final long REVERSE_METER_ID = MeterId.MIN_FLOW_METER_ID + 3;
    private static final long PROTECTED_FORWARD_METER_ID = MeterId.MIN_FLOW_METER_ID + 2;
    private static final long PROTECTED_REVERSE_METER_ID = MeterId.MIN_FLOW_METER_ID + 4;

    private static final SwitchId SRC_SWITCH_ID = new SwitchId(1L);
    private static final SwitchId DST_SWITCH_ID = new SwitchId(2L);
    private static final SwitchId TRANSIT_SWITCH_ID = new SwitchId(3L);

    @Mock
    PersistenceManager persistenceManager;
    @Mock
    RepositoryFactory repositoryFactory;
    @Mock
    FlowRepository flowRepository;
    @Mock
    YFlowRepository yFlowRepository;
    @Mock
    KildaEntryCacheCarrier carrier;
    @Captor
    ArgumentCaptor<SwitchFlowStats> cookieCacheCaptor;
    @Captor
    ArgumentCaptor<SwitchMeterStats> meterCacheCaptor;

    KildaEntryCacheService service;

    @Before
    public void initService() {
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        when(repositoryFactory.createYFlowRepository()).thenReturn(yFlowRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        service = new KildaEntryCacheService(persistenceManager, carrier);
    }

    @Test
    public void shouldRefreshCommonFlowsCookieCache() {
        Flow flow = buildFlow();
        when(flowRepository.findAll()).thenReturn(Collections.singletonList(flow));
        when(yFlowRepository.findAll()).thenReturn(Collections.emptyList());

        service.refreshCache();

        final FlowPath forwardPath = flow.getForwardPath();

        FlowStatsData statsOriginSrc = getFlowStatsDataSrcSwitch();
        service.completeAndForwardFlowStats(statsOriginSrc);

        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        List<FlowStatsAndDescriptor> statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOriginSrc.getStats().size(), 3);
        assertCookieCache(
                statsEntries, forwardPath.getCookie(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), forwardPath.getCookie(),
                        flow.getForwardPath().getMeterId()));
        assertCookieCache(
                statsEntries, REVERSE_PATH_COOKIE,
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), EGRESS, flow.getFlowId(), REVERSE_PATH_COOKIE, null));
        assertCookieCache(
                statsEntries, PROTECTED_REVERSE_PATH_COOKIE,
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), EGRESS, flow.getFlowId(), PROTECTED_REVERSE_PATH_COOKIE, null));

        FlowStatsData statsOriginDst = getFlowStatsDataDstSwitch();
        service.completeAndForwardFlowStats(statsOriginDst);

        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOriginDst.getStats().size(), 3);
        assertCookieCache(
                statsEntries, forwardPath.getCookie(),
                new CommonFlowDescriptor(
                        flow.getDestSwitchId(), EGRESS, flow.getFlowId(), forwardPath.getCookie(), null));
        assertCookieCache(
                statsEntries, REVERSE_PATH_COOKIE,
                new CommonFlowDescriptor(
                        flow.getDestSwitchId(), INGRESS, flow.getFlowId(), REVERSE_PATH_COOKIE,
                        flow.getReversePath().getMeterId()));
        assertCookieCache(
                statsEntries, PROTECTED_FORWARD_PATH_COOKIE,
                new CommonFlowDescriptor(
                        flow.getDestSwitchId(), EGRESS, flow.getFlowId(), PROTECTED_FORWARD_PATH_COOKIE, null));

        FlowStatsData statsOriginTransit = getFlowStatsDataTransitSwitch();
        service.completeAndForwardFlowStats(statsOriginTransit);

        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOriginTransit.getStats().size(), 4);
        assertCookieCache(
                statsEntries, forwardPath.getCookie(),
                new CommonFlowDescriptor(TRANSIT_SWITCH_ID, TRANSIT, flow.getFlowId(), forwardPath.getCookie(), null));
        assertCookieCache(
                statsEntries, REVERSE_PATH_COOKIE,
                new CommonFlowDescriptor(TRANSIT_SWITCH_ID, TRANSIT, flow.getFlowId(), REVERSE_PATH_COOKIE, null));
        assertCookieCache(
                statsEntries, PROTECTED_FORWARD_PATH_COOKIE,
                new CommonFlowDescriptor(
                        TRANSIT_SWITCH_ID, TRANSIT, flow.getFlowId(), PROTECTED_FORWARD_PATH_COOKIE, null));
        assertCookieCache(
                statsEntries, PROTECTED_REVERSE_PATH_COOKIE,
                new CommonFlowDescriptor(
                        TRANSIT_SWITCH_ID, TRANSIT, flow.getFlowId(), PROTECTED_REVERSE_PATH_COOKIE, null));

        // flow endpoint satellites
        service.completeAndForwardFlowStats(
                new FlowStatsData(flow.getSrcSwitchId(), asList(
                        new FlowStatsEntry(
                                0,
                                forwardPath.getCookie().toBuilder()
                                .type(CookieType.SERVER_42_FLOW_RTT_INGRESS).build().getValue(),
                                0, 0, 0, 0),
                        new FlowStatsEntry(
                                0, forwardPath.getCookie().toBuilder().mirror(true).build().getValue(),
                                0, 0, 0, 0),
                        new FlowStatsEntry(
                                0, forwardPath.getCookie().toBuilder().looped(true).build().getValue(),
                                0, 0, 0, 0))));
        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, 3, 3);
        assertCookieCache(
                statsEntries, forwardPath.getCookie().toBuilder()
                        .type(CookieType.SERVER_42_FLOW_RTT_INGRESS).build(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), forwardPath.getCookie(),
                        forwardPath.getMeterId()));
        assertCookieCache(
                statsEntries, forwardPath.getCookie().toBuilder().mirror(true).build(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), forwardPath.getCookie(),
                        forwardPath.getMeterId()));
        assertCookieCache(
                statsEntries, forwardPath.getCookie().toBuilder().looped(true).build(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), forwardPath.getCookie(),
                        forwardPath.getMeterId()));
    }

    @Test
    public void shouldRefreshYFlowSubFlowCookieCache() {
        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch destSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();
        Switch transitSwitch = Switch.builder().switchId(TRANSIT_SWITCH_ID).build();
        Flow flow = new TestFlowBuilder()
                .yFlowId("dummy-y-flow-id")
                .srcSwitch(srcSwitch)
                .addTransitionEndpoint(srcSwitch, 2)
                .addTransitionEndpoint(transitSwitch, 1)
                .addTransitionEndpoint(transitSwitch, 2)
                .addTransitionEndpoint(destSwitch, 1)
                .unmaskedCookie(FLOW_UNMASKED_COOKIE)
                .forwardMeterId(FORWARD_METER_ID)
                .reverseMeterId(REVERSE_METER_ID)
                .destSwitch(destSwitch)
                .build();

        when(flowRepository.findAll()).thenReturn(Collections.singletonList(flow));
        when(yFlowRepository.findAll()).thenReturn(Collections.emptyList());

        service.refreshCache();

        final FlowSegmentCookie forwardPathCookie = flow.getForwardPath().getCookie();
        final FlowSegmentCookie reversePathCookie = flow.getReversePath().getCookie();

        // source switch
        service.completeAndForwardFlowStats(new FlowStatsData(
                flow.getSrcSwitchId(), asList(
                    new FlowStatsEntry(0, forwardPathCookie.getValue(), 0, 0, 0, 0),
                    new FlowStatsEntry(0, reversePathCookie.getValue(), 0, 0, 0, 0))));

        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        List<FlowStatsAndDescriptor> statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, 2, 2);
        assertCookieCache(
                statsEntries, forwardPathCookie,
                new YFlowSubDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getYFlowId(), flow.getFlowId(), forwardPathCookie,
                        flow.getForwardPath().getMeterId()));
        assertCookieCache(
                statsEntries, reversePathCookie,
                new YFlowSubDescriptor(
                        flow.getSrcSwitchId(), EGRESS, flow.getYFlowId(), flow.getFlowId(), reversePathCookie, null));

        // transit
        service.completeAndForwardFlowStats(new FlowStatsData(
                transitSwitch.getSwitchId(), asList(
                    new FlowStatsEntry(0, forwardPathCookie.getValue(), 0, 0, 0, 0),
                    new FlowStatsEntry(0, reversePathCookie.getValue(), 0, 0, 0, 0))));
        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, 2, 2);
        assertCookieCache(
                statsEntries, forwardPathCookie,
                new YFlowSubDescriptor(
                        transitSwitch.getSwitchId(), TRANSIT, flow.getYFlowId(), flow.getFlowId(), forwardPathCookie,
                        null));
        assertCookieCache(
                statsEntries, reversePathCookie,
                new YFlowSubDescriptor(
                        transitSwitch.getSwitchId(), TRANSIT, flow.getYFlowId(), flow.getFlowId(), reversePathCookie,
                        null));

        // egress
        service.completeAndForwardFlowStats(new FlowStatsData(
                flow.getDestSwitchId(), asList(
                    new FlowStatsEntry(0, forwardPathCookie.getValue(), 0, 0, 0, 0),
                    new FlowStatsEntry(0, reversePathCookie.getValue(), 0, 0, 0, 0))));
        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, 2, 2);
        assertCookieCache(
                statsEntries, forwardPathCookie,
                new YFlowSubDescriptor(
                        flow.getDestSwitchId(), EGRESS, flow.getYFlowId(), flow.getFlowId(), forwardPathCookie, null));
        assertCookieCache(
                statsEntries, reversePathCookie,
                new YFlowSubDescriptor(
                        flow.getDestSwitchId(), INGRESS, flow.getYFlowId(), flow.getFlowId(), reversePathCookie,
                        flow.getReversePath().getMeterId()));
    }

    @Test
    public void shouldCacheServiceRefreshMeterCache() {
        Flow flow = buildFlow();
        when(flowRepository.findAll()).thenReturn(Collections.singletonList(flow));

        service.refreshCache();

        MeterStatsData statsOriginSrc = getMeterStatsDataSrcSwitch();
        service.completeAndForwardMeterStats(statsOriginSrc);

        final MeterId forwardMeterId = flow.getForwardPath().getMeterId();

        @SuppressWarnings({"ConstantConditions"})
        final MeterId forwardProtectedMeterId = flow.getProtectedForwardPath().getMeterId();

        verify(carrier, atLeastOnce()).emitMeterStats(meterCacheCaptor.capture());
        List<MeterStatsAndDescriptor> statsEntries = meterCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOriginSrc.getStats().size(), 2);
        assertMeterCache(
                statsEntries, forwardMeterId.getValue(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), flow.getForwardPath().getCookie(),
                        forwardMeterId));
        assertMeterCache(
                statsEntries, forwardProtectedMeterId.getValue(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), flow.getProtectedForwardPath().getCookie(),
                        forwardProtectedMeterId));

        MeterStatsData statsOriginDst = getMeterStatsDataDstSwitch();
        service.completeAndForwardMeterStats(statsOriginDst);

        verify(carrier, atLeastOnce()).emitMeterStats(meterCacheCaptor.capture());
        statsEntries = meterCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOriginDst.getStats().size(), 2);

        final MeterId reverseMeterId = flow.getReversePath().getMeterId();
        assertMeterCache(
                statsEntries, reverseMeterId.getValue(),
                new CommonFlowDescriptor(
                        flow.getDestSwitchId(), INGRESS, flow.getFlowId(), flow.getReversePath().getCookie(),
                        reverseMeterId));
        final MeterId reverseProtectedMeterId = flow.getProtectedReversePath().getMeterId();
        assertMeterCache(
                statsEntries, reverseProtectedMeterId.getValue(),
                new CommonFlowDescriptor(
                        flow.getDestSwitchId(), INGRESS, flow.getFlowId(), flow.getProtectedReversePath().getCookie(),
                        reverseProtectedMeterId));
    }

    @Test
    public void shouldRefreshYFlowMeterCache() {
        FlowEndpoint sharedEndpoint = new FlowEndpoint(SRC_SWITCH_ID, 1);
        FlowEndpoint splitEndEndpoint = new FlowEndpoint(DST_SWITCH_ID, 2);

        Switch sharedSwitch = Switch.builder().switchId(sharedEndpoint.getSwitchId()).build();
        Switch splitEndSwitch = Switch.builder().switchId(splitEndEndpoint.getSwitchId()).build();
        Switch yPointSwitch = Switch.builder().switchId(TRANSIT_SWITCH_ID).build();

        Flow subFlowAlpha = new TestFlowBuilder()
                .srcSwitch(sharedSwitch)
                .destSwitch(splitEndSwitch)
                .addTransitionEndpoint(sharedSwitch, sharedEndpoint.getPortNumber())
                .addTransitionEndpoint(yPointSwitch, 1)
                .addTransitionEndpoint(yPointSwitch, 2)
                .addTransitionEndpoint(splitEndSwitch, sharedEndpoint.getPortNumber())
                .build();
        Flow subFlowBeta = new TestFlowBuilder()
                .srcSwitch(sharedSwitch)
                .destSwitch(splitEndSwitch)
                .addTransitionEndpoint(sharedSwitch, sharedEndpoint.getPortNumber())
                .addTransitionEndpoint(yPointSwitch, 1)
                .addTransitionEndpoint(yPointSwitch, 2)
                .addTransitionEndpoint(splitEndSwitch, sharedEndpoint.getPortNumber())
                .build();

        YFlow yFlow = new TestYFlowBuilder()
                .sharedEndpoint(new YFlow.SharedEndpoint(sharedEndpoint.getSwitchId(), sharedEndpoint.getPortNumber()))
                .sharedEndpointMeterId(new MeterId(100))
                .yPoint(yPointSwitch.getSwitchId())
                .meterId(new MeterId(110))
                .subFlow(
                        new TestYSubFlowBuilder()
                                .flow(subFlowAlpha)
                                .sharedEndpointVlan(10)
                                .endpoint(new FlowEndpoint(DST_SWITCH_ID, 2, 30)))
                .subFlow(
                        new TestYSubFlowBuilder()
                                .flow(subFlowBeta)
                                .sharedEndpointVlan(20)
                                .endpoint(new FlowEndpoint(DST_SWITCH_ID, 2, 40)))
                .build();

        when(flowRepository.findAll()).thenReturn(Collections.emptyList());
        when(yFlowRepository.findAll()).thenReturn(Collections.singletonList(yFlow));

        service.refreshCache();

        // shared endpoint
        service.completeAndForwardMeterStats(new MeterStatsData(
                yFlow.getSharedEndpoint().getSwitchId(), Collections.singletonList(
                    new MeterStatsEntry(yFlow.getSharedEndpointMeterId().getValue(), 0, 0))));

        verify(carrier, atLeastOnce()).emitMeterStats(meterCacheCaptor.capture());
        List<MeterStatsAndDescriptor> statsEntries = meterCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, 1, 1);
        assertMeterCache(
                statsEntries, yFlow.getSharedEndpointMeterId().getValue(),
                new YFlowDescriptor(
                        yFlow.getSharedEndpoint().getSwitchId(), Y_FLOW_SHARED, yFlow.getYFlowId(),
                        yFlow.getSharedEndpointMeterId()));

        // y-point
        service.completeAndForwardMeterStats(new MeterStatsData(
                yFlow.getYPoint(), Collections.singletonList(
                    new MeterStatsEntry(yFlow.getMeterId().getValue(), 0, 0))));
        verify(carrier, atLeastOnce()).emitMeterStats(meterCacheCaptor.capture());
        statsEntries = meterCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, 1, 1);
        assertMeterCache(
                statsEntries, yFlow.getMeterId().getValue(),
                new YFlowDescriptor(
                        yFlow.getYPoint(), Y_FLOW_Y_POINT, yFlow.getYFlowId(), yFlow.getMeterId()));
    }

    @Test
    public void shouldCompleteFlowStats() {
        Flow flow = buildFlow();

        FlowStatsData statsOrigin = getFlowStatsDataSrcSwitch();
        service.completeAndForwardFlowStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        List<FlowStatsAndDescriptor> statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 0);

        FlowPath forwardPath = flow.getForwardPath();
        UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                flow.getFlowId(), flow.getYFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.addOrUpdateCache(pathInfo);

        service.completeAndForwardFlowStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 1);
        assertCookieCache(
                statsEntries, forwardPath.getCookie(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), forwardPath.getCookie(),
                        forwardPath.getMeterId()));

        FlowPath reversePath = flow.getReversePath();
        UpdateFlowPathInfo pathInfo2 = new UpdateFlowPathInfo(
                flow.getFlowId(), flow.getYFlowId(), reversePath.getCookie(), reversePath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(reversePath));
        service.addOrUpdateCache(pathInfo2);

        service.completeAndForwardFlowStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 2);
        assertCookieCache(
                statsEntries, forwardPath.getCookie(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), forwardPath.getCookie(),
                        forwardPath.getMeterId()));
        assertCookieCache(
                statsEntries, reversePath.getCookie(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), EGRESS, flow.getFlowId(), reversePath.getCookie(), null));

        FlowPath protectedReversePath = flow.getProtectedReversePath();
        UpdateFlowPathInfo pathInfo3 = new UpdateFlowPathInfo(
                flow.getFlowId(), flow.getYFlowId(), protectedReversePath.getCookie(),
                protectedReversePath.getMeterId(), FlowPathMapper.INSTANCE.mapToPathNodes(protectedReversePath));
        service.addOrUpdateCache(pathInfo3);

        service.completeAndForwardFlowStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 3);
        assertCookieCache(
                statsEntries, forwardPath.getCookie(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), forwardPath.getCookie(),
                        forwardPath.getMeterId()));
        assertCookieCache(
                statsEntries, reversePath.getCookie(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), EGRESS, flow.getFlowId(), reversePath.getCookie(), null));
        assertCookieCache(
                statsEntries, protectedReversePath.getCookie(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), EGRESS, flow.getFlowId(), protectedReversePath.getCookie(), null));
    }

    @Test
    public void shouldHandleRemovingFlowFromCache() {
        Flow flow = buildFlow();

        FlowStatsData statsOrigin = getFlowStatsDataSrcSwitch();
        service.completeAndForwardFlowStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        List<FlowStatsAndDescriptor> statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 0);

        FlowPath forwardPath = flow.getForwardPath();
        UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                flow.getFlowId(), flow.getYFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.addOrUpdateCache(pathInfo);

        service.completeAndForwardFlowStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 1);
        assertCookieCache(
                statsEntries, forwardPath.getCookie(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), forwardPath.getCookie(),
                        forwardPath.getMeterId()));

        RemoveFlowPathInfo pathInfo2 = new RemoveFlowPathInfo(
                flow.getFlowId(), flow.getYFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.removeCached(pathInfo2);

        service.completeAndForwardFlowStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitFlowStats(cookieCacheCaptor.capture());
        statsEntries = cookieCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 0);
    }

    @Test
    public void shouldCompleteMeterStats() {
        Flow flow = buildFlow();

        MeterStatsData statsOrigin = getMeterStatsDataSrcSwitch();
        service.completeAndForwardMeterStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitMeterStats(meterCacheCaptor.capture());
        List<MeterStatsAndDescriptor> statsEntries = meterCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 0);

        FlowPath forwardPath = flow.getForwardPath();
        UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                flow.getFlowId(), flow.getYFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.addOrUpdateCache(pathInfo);

        service.completeAndForwardMeterStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitMeterStats(meterCacheCaptor.capture());
        statsEntries = meterCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 1);
        assertMeterCache(
                statsEntries, forwardPath.getMeterId().getValue(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), forwardPath.getCookie(),
                        forwardPath.getMeterId()));

        FlowPath protectedForwardPath = flow.getProtectedForwardPath();
        UpdateFlowPathInfo pathInfo2 = new UpdateFlowPathInfo(
                flow.getFlowId(), flow.getYFlowId(), protectedForwardPath.getCookie(),
                protectedForwardPath.getMeterId(), FlowPathMapper.INSTANCE.mapToPathNodes(protectedForwardPath));
        service.addOrUpdateCache(pathInfo2);

        service.completeAndForwardMeterStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitMeterStats(meterCacheCaptor.capture());
        statsEntries = meterCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 2);
        assertMeterCache(
                statsEntries, forwardPath.getMeterId().getValue(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), forwardPath.getCookie(),
                        forwardPath.getMeterId()));
        assertMeterCache(
                statsEntries, protectedForwardPath.getMeterId().getValue(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), protectedForwardPath.getCookie(),
                        protectedForwardPath.getMeterId()));
    }

    @Test
    public void shouldHandleRemovingMeterFromCache() {
        Flow flow = buildFlow();

        MeterStatsData statsOrigin = getMeterStatsDataSrcSwitch();
        service.completeAndForwardMeterStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitMeterStats(meterCacheCaptor.capture());
        List<MeterStatsAndDescriptor> statsEntries = meterCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 0);

        FlowPath forwardPath = flow.getForwardPath();
        UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                flow.getFlowId(), flow.getYFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.addOrUpdateCache(pathInfo);

        service.completeAndForwardMeterStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitMeterStats(meterCacheCaptor.capture());
        statsEntries = meterCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 1);
        assertMeterCache(
                statsEntries, forwardPath.getMeterId().getValue(),
                new CommonFlowDescriptor(
                        flow.getSrcSwitchId(), INGRESS, flow.getFlowId(), forwardPath.getCookie(),
                        forwardPath.getMeterId()));

        RemoveFlowPathInfo pathInfo2 = new RemoveFlowPathInfo(
                flow.getFlowId(), flow.getYFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.removeCached(pathInfo2);

        service.completeAndForwardMeterStats(statsOrigin);

        verify(carrier, atLeastOnce()).emitMeterStats(meterCacheCaptor.capture());
        statsEntries = meterCacheCaptor.getValue().getStatsEntries();
        assertDescriptionPopulation(statsEntries, statsOrigin.getStats().size(), 0);
    }

    private void assertCookieCache(
            List<FlowStatsAndDescriptor> statsEntries, FlowSegmentCookie cookie,
            KildaEntryDescriptor expectedDescriptor) {
        long needle = cookie.getValue();
        for (FlowStatsAndDescriptor entry : statsEntries) {
            if (needle == entry.getData().getCookie()) {
                KildaEntryDescriptor descriptor = entry.getDescriptor();
                Assert.assertEquals(expectedDescriptor, descriptor);
                return;
            }
        }
        Assert.fail("Cache miss");
    }

    private void assertMeterCache(
            List<MeterStatsAndDescriptor> statsEntries, long meterId, KildaEntryDescriptor expectedDescriptor) {
        for (MeterStatsAndDescriptor entry : statsEntries) {
            if (meterId == entry.getData().getMeterId()) {
                KildaEntryDescriptor descriptor = entry.getDescriptor();
                Assert.assertEquals(expectedDescriptor, descriptor);
                return;
            }
        }
        Assert.fail("Cache miss");
    }

    private void assertDescriptionPopulation(
            List<? extends StatsAndDescriptor<?>> statsEntries, long expectEntriesTotal, long expectCacheHits) {
        Assert.assertEquals(expectEntriesTotal, statsEntries.size());
        Assert.assertEquals(
                expectCacheHits, statsEntries.stream().filter(entry -> entry.getDescriptor() != null).count());
    }

    private Flow buildFlow() {
        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch destSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();
        Switch transitSwitch = Switch.builder().switchId(TRANSIT_SWITCH_ID).build();
        return new TestFlowBuilder()
                .srcSwitch(srcSwitch)
                .addTransitionEndpoint(srcSwitch, 2)
                .addTransitionEndpoint(transitSwitch, 1)
                .addTransitionEndpoint(transitSwitch, 2)
                .addTransitionEndpoint(destSwitch, 1)
                .unmaskedCookie(FLOW_UNMASKED_COOKIE)
                .forwardMeterId(FORWARD_METER_ID)
                .reverseMeterId(REVERSE_METER_ID)
                .addProtectedTransitionEndpoint(srcSwitch, 3)
                .addProtectedTransitionEndpoint(transitSwitch, 3)
                .addProtectedTransitionEndpoint(transitSwitch, 4)
                .addProtectedTransitionEndpoint(destSwitch, 3)
                .protectedUnmaskedCookie(FLOW_PROTECTED_UNMASKED_COOKIE)
                .protectedForwardMeterId(PROTECTED_FORWARD_METER_ID)
                .protectedReverseMeterId(PROTECTED_REVERSE_METER_ID)
                .destSwitch(destSwitch)
                .build();
    }

    private FlowStatsData getFlowStatsDataSrcSwitch() {
        return new FlowStatsData(SRC_SWITCH_ID, asList(
                new FlowStatsEntry(0, FORWARD_PATH_COOKIE.getValue(), 0, 0, 0, 0),
                new FlowStatsEntry(0, REVERSE_PATH_COOKIE.getValue(), 0, 0, 0, 0),
                new FlowStatsEntry(0, PROTECTED_REVERSE_PATH_COOKIE.getValue(), 0, 0, 0, 0)));
    }

    private FlowStatsData getFlowStatsDataDstSwitch() {
        return new FlowStatsData(DST_SWITCH_ID, asList(
                new FlowStatsEntry(0, FORWARD_PATH_COOKIE.getValue(), 0, 0, 0, 0),
                new FlowStatsEntry(0, REVERSE_PATH_COOKIE.getValue(), 0, 0, 0, 0),
                new FlowStatsEntry(0, PROTECTED_FORWARD_PATH_COOKIE.getValue(), 0, 0, 0, 0)));
    }

    private FlowStatsData getFlowStatsDataTransitSwitch() {
        return new FlowStatsData(TRANSIT_SWITCH_ID, asList(
                new FlowStatsEntry(0, FORWARD_PATH_COOKIE.getValue(), 0, 0, 0, 0),
                new FlowStatsEntry(0, REVERSE_PATH_COOKIE.getValue(), 0, 0, 0, 0),
                new FlowStatsEntry(0, PROTECTED_FORWARD_PATH_COOKIE.getValue(), 0, 0, 0, 0),
                new FlowStatsEntry(0, PROTECTED_REVERSE_PATH_COOKIE.getValue(), 0, 0, 0, 0)));
    }

    private MeterStatsData getMeterStatsDataSrcSwitch() {
        return new MeterStatsData(SRC_SWITCH_ID, asList(
                new MeterStatsEntry(FORWARD_METER_ID, 0, 0),
                new MeterStatsEntry(PROTECTED_FORWARD_METER_ID, 0, 0)));
    }

    private MeterStatsData getMeterStatsDataDstSwitch() {
        return new MeterStatsData(DST_SWITCH_ID, asList(
                new MeterStatsEntry(REVERSE_METER_ID, 0, 0),
                new MeterStatsEntry(PROTECTED_REVERSE_METER_ID, 0, 0)));
    }
}
