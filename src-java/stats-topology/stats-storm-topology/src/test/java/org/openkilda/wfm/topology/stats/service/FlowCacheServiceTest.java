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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.EGRESS;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.INGRESS;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.TRANSIT;

import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.flow.TestFlowBuilder;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.stats.model.FlowCacheEntry;
import org.openkilda.wfm.topology.stats.model.MeasurePoint;
import org.openkilda.wfm.topology.stats.model.MeterCacheKey;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class FlowCacheServiceTest {
    private static final Long FLOW_UNMASKED_COOKIE = 1L;
    private static final Long FORWARD_PATH_COOKIE =
            new FlowSegmentCookie(FlowPathDirection.FORWARD, FLOW_UNMASKED_COOKIE).getValue();
    private static final Long REVERSE_PATH_COOKIE =
            new FlowSegmentCookie(FlowPathDirection.REVERSE, FLOW_UNMASKED_COOKIE).getValue();
    private static final Long FLOW_PROTECTED_UNMASKED_COOKIE = 2L;
    private static final Long PROTECTED_FORWARD_PATH_COOKIE =
            new FlowSegmentCookie(FlowPathDirection.FORWARD, FLOW_PROTECTED_UNMASKED_COOKIE).getValue();
    private static final Long PROTECTED_REVERSE_PATH_COOKIE =
            new FlowSegmentCookie(FlowPathDirection.REVERSE, FLOW_PROTECTED_UNMASKED_COOKIE).getValue();

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
    FlowCacheBoltCarrier carrier;
    @Captor
    ArgumentCaptor<Map<Long, FlowCacheEntry>> cookieCacheCaptor;
    @Captor
    ArgumentCaptor<Map<MeterCacheKey, FlowCacheEntry>> meterCacheCaptor;

    FlowCacheService service;

    @Before
    public void initService() {
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        service = new FlowCacheService(persistenceManager, carrier);
    }

    @Test
    public void shouldCacheServiceRefreshCookieCache() {
        Flow flow = buildFlow();
        when(flowRepository.findAll()).thenReturn(Collections.singletonList(flow));

        service.refreshCache();

        service.completeAndForwardFlowStats(getFlowStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitFlowStats(any(), cookieCacheCaptor.capture());
        Map<Long, FlowCacheEntry> srcCache = cookieCacheCaptor.getValue();
        Assert.assertEquals(3, srcCache.size());
        assertCookieCache(flow, srcCache, FORWARD_PATH_COOKIE, INGRESS);
        assertCookieCache(flow, srcCache, REVERSE_PATH_COOKIE, EGRESS);
        assertCookieCache(flow, srcCache, PROTECTED_REVERSE_PATH_COOKIE, EGRESS);

        service.completeAndForwardFlowStats(getFlowStatsDataDstSwitch());

        verify(carrier, atLeastOnce()).emitFlowStats(any(), cookieCacheCaptor.capture());
        Map<Long, FlowCacheEntry> dstCache = cookieCacheCaptor.getValue();
        Assert.assertEquals(3, dstCache.size());
        assertCookieCache(flow, dstCache, FORWARD_PATH_COOKIE, EGRESS);
        assertCookieCache(flow, dstCache, REVERSE_PATH_COOKIE, INGRESS);
        assertCookieCache(flow, dstCache, PROTECTED_FORWARD_PATH_COOKIE, EGRESS);

        service.completeAndForwardFlowStats(getFlowStatsDataTransitSwitch());

        verify(carrier, atLeastOnce()).emitFlowStats(any(), cookieCacheCaptor.capture());
        Map<Long, FlowCacheEntry> transitCache = cookieCacheCaptor.getValue();
        Assert.assertEquals(4, transitCache.size());
        assertCookieCache(flow, transitCache, REVERSE_PATH_COOKIE, TRANSIT);
        assertCookieCache(flow, transitCache, PROTECTED_REVERSE_PATH_COOKIE, TRANSIT);
        assertCookieCache(flow, transitCache, PROTECTED_REVERSE_PATH_COOKIE, TRANSIT);
        assertCookieCache(flow, transitCache, PROTECTED_REVERSE_PATH_COOKIE, TRANSIT);
    }

    @Test
    public void shouldCacheServiceRefreshMeterCache() {
        Flow flow = buildFlow();
        when(flowRepository.findAll()).thenReturn(Collections.singletonList(flow));

        service.refreshCache();

        service.completeAndForwardMeterStats(getMeterStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitMeterStats(any(), meterCacheCaptor.capture());
        Map<MeterCacheKey, FlowCacheEntry> srcCache = meterCacheCaptor.getValue();
        Assert.assertEquals(2, srcCache.size());
        assertMeterCache(flow, SRC_SWITCH_ID, srcCache, FORWARD_METER_ID, FORWARD_PATH_COOKIE);
        assertMeterCache(flow, SRC_SWITCH_ID, srcCache, PROTECTED_FORWARD_METER_ID, PROTECTED_FORWARD_PATH_COOKIE);

        service.completeAndForwardMeterStats(getMeterStatsDataDstSwitch());

        verify(carrier, atLeastOnce()).emitMeterStats(any(), meterCacheCaptor.capture());
        Map<MeterCacheKey, FlowCacheEntry> dstCache = meterCacheCaptor.getValue();
        Assert.assertEquals(2, dstCache.size());
        assertMeterCache(flow, DST_SWITCH_ID, dstCache, REVERSE_METER_ID, REVERSE_PATH_COOKIE);
        assertMeterCache(flow, DST_SWITCH_ID, dstCache, PROTECTED_REVERSE_METER_ID, PROTECTED_REVERSE_PATH_COOKIE);
    }

    private void assertCookieCache(Flow flow, Map<Long, FlowCacheEntry> cookieToFlowCache, Long cookie,
                                   MeasurePoint measurePoint) {
        FlowCacheEntry entry = cookieToFlowCache.get(cookie);
        Assert.assertEquals(flow.getFlowId(), entry.getFlowId());
        Assert.assertEquals(cookie.longValue(), entry.getCookie());
        Assert.assertEquals(measurePoint, entry.getMeasurePoint());
    }

    private void assertMeterCache(Flow flow, SwitchId switchId, Map<MeterCacheKey, FlowCacheEntry> cache,
                                  long meterId, Long cookie) {
        FlowCacheEntry entry = cache.get(new MeterCacheKey(switchId, meterId));
        Assert.assertEquals(flow.getFlowId(), entry.getFlowId());
        Assert.assertEquals(cookie.longValue(), entry.getCookie());
    }

    @Test
    public void shouldCompleteFlowStats() {
        Flow flow = buildFlow();

        service.completeAndForwardFlowStats(getFlowStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitFlowStats(any(), cookieCacheCaptor.capture());
        Map<Long, FlowCacheEntry> srcCache = cookieCacheCaptor.getValue();
        Assert.assertEquals(0, srcCache.size());

        FlowPath forwardPath = flow.getForwardPath();
        UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                flow.getFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.addOrUpdateCache(pathInfo);

        service.completeAndForwardFlowStats(getFlowStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitFlowStats(any(), cookieCacheCaptor.capture());
        srcCache = cookieCacheCaptor.getValue();
        Assert.assertEquals(1, srcCache.size());
        assertCookieCache(flow, srcCache, FORWARD_PATH_COOKIE, INGRESS);

        FlowPath reversePath = flow.getReversePath();
        UpdateFlowPathInfo pathInfo2 = new UpdateFlowPathInfo(
                flow.getFlowId(), reversePath.getCookie(), reversePath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(reversePath));
        service.addOrUpdateCache(pathInfo2);

        service.completeAndForwardFlowStats(getFlowStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitFlowStats(any(), cookieCacheCaptor.capture());
        srcCache = cookieCacheCaptor.getValue();
        Assert.assertEquals(2, srcCache.size());
        assertCookieCache(flow, srcCache, FORWARD_PATH_COOKIE, INGRESS);
        assertCookieCache(flow, srcCache, REVERSE_PATH_COOKIE, EGRESS);

        FlowPath protectedReversePath = flow.getProtectedReversePath();
        UpdateFlowPathInfo pathInfo3 = new UpdateFlowPathInfo(
                flow.getFlowId(), protectedReversePath.getCookie(), protectedReversePath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(protectedReversePath));
        service.addOrUpdateCache(pathInfo3);

        service.completeAndForwardFlowStats(getFlowStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitFlowStats(any(), cookieCacheCaptor.capture());
        srcCache = cookieCacheCaptor.getValue();
        Assert.assertEquals(3, srcCache.size());
        assertCookieCache(flow, srcCache, FORWARD_PATH_COOKIE, INGRESS);
        assertCookieCache(flow, srcCache, REVERSE_PATH_COOKIE, EGRESS);
        assertCookieCache(flow, srcCache, PROTECTED_REVERSE_PATH_COOKIE, EGRESS);
    }

    @Test
    public void shouldHandleRemovingFlowFromCache() {
        Flow flow = buildFlow();

        service.completeAndForwardFlowStats(getFlowStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitFlowStats(any(), cookieCacheCaptor.capture());
        Map<Long, FlowCacheEntry> srcCache = cookieCacheCaptor.getValue();
        Assert.assertEquals(0, srcCache.size());

        FlowPath forwardPath = flow.getForwardPath();
        UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                flow.getFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.addOrUpdateCache(pathInfo);

        service.completeAndForwardFlowStats(getFlowStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitFlowStats(any(), cookieCacheCaptor.capture());
        srcCache = cookieCacheCaptor.getValue();
        Assert.assertEquals(1, srcCache.size());
        assertCookieCache(flow, srcCache, FORWARD_PATH_COOKIE, INGRESS);

        RemoveFlowPathInfo pathInfo2 = new RemoveFlowPathInfo(
                flow.getFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.removeCached(pathInfo2);

        service.completeAndForwardFlowStats(getFlowStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitFlowStats(any(), cookieCacheCaptor.capture());
        srcCache = cookieCacheCaptor.getValue();
        Assert.assertEquals(0, srcCache.size());
    }

    @Test
    public void shouldCompleteMeterStats() {
        Flow flow = buildFlow();

        service.completeAndForwardMeterStats(getMeterStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitMeterStats(any(), meterCacheCaptor.capture());
        Map<MeterCacheKey, FlowCacheEntry> srcCache = meterCacheCaptor.getValue();
        Assert.assertEquals(0, srcCache.size());

        FlowPath forwardPath = flow.getForwardPath();
        UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                flow.getFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.addOrUpdateCache(pathInfo);

        service.completeAndForwardMeterStats(getMeterStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitMeterStats(any(), meterCacheCaptor.capture());
        srcCache = meterCacheCaptor.getValue();
        Assert.assertEquals(1, srcCache.size());
        assertMeterCache(flow, SRC_SWITCH_ID, srcCache, FORWARD_METER_ID, FORWARD_PATH_COOKIE);

        FlowPath protectedForwardPath = flow.getProtectedForwardPath();
        UpdateFlowPathInfo pathInfo2 = new UpdateFlowPathInfo(
                flow.getFlowId(), protectedForwardPath.getCookie(), protectedForwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(protectedForwardPath));
        service.addOrUpdateCache(pathInfo2);

        service.completeAndForwardMeterStats(getMeterStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitMeterStats(any(), meterCacheCaptor.capture());
        srcCache = meterCacheCaptor.getValue();
        Assert.assertEquals(2, srcCache.size());
        assertMeterCache(flow, SRC_SWITCH_ID, srcCache, FORWARD_METER_ID, FORWARD_PATH_COOKIE);
        assertMeterCache(flow, SRC_SWITCH_ID, srcCache, PROTECTED_FORWARD_METER_ID, PROTECTED_FORWARD_PATH_COOKIE);
    }

    @Test
    public void shouldHandleRemovingMeterFromCache() {
        Flow flow = buildFlow();

        service.completeAndForwardMeterStats(getMeterStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitMeterStats(any(), meterCacheCaptor.capture());
        Map<MeterCacheKey, FlowCacheEntry> srcCache = meterCacheCaptor.getValue();
        Assert.assertEquals(0, srcCache.size());

        FlowPath forwardPath = flow.getForwardPath();
        UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                flow.getFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.addOrUpdateCache(pathInfo);

        service.completeAndForwardMeterStats(getMeterStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitMeterStats(any(), meterCacheCaptor.capture());
        srcCache = meterCacheCaptor.getValue();
        Assert.assertEquals(1, srcCache.size());
        assertMeterCache(flow, SRC_SWITCH_ID, srcCache, FORWARD_METER_ID, FORWARD_PATH_COOKIE);

        RemoveFlowPathInfo pathInfo2 = new RemoveFlowPathInfo(
                flow.getFlowId(), forwardPath.getCookie(), forwardPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath));
        service.removeCached(pathInfo2);

        service.completeAndForwardMeterStats(getMeterStatsDataSrcSwitch());

        verify(carrier, atLeastOnce()).emitMeterStats(any(), meterCacheCaptor.capture());
        srcCache = meterCacheCaptor.getValue();
        Assert.assertEquals(0, srcCache.size());
    }

    private Flow buildFlow() {
        Switch srcSwitch = Switch.builder().switchId(SRC_SWITCH_ID).build();
        Switch destSwitch = Switch.builder().switchId(DST_SWITCH_ID).build();
        Switch transitSwitch = Switch.builder().switchId(TRANSIT_SWITCH_ID).build();
        Flow flow = new TestFlowBuilder()
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
        return flow;
    }

    private FlowStatsData getFlowStatsDataSrcSwitch() {
        return new FlowStatsData(SRC_SWITCH_ID, asList(
                new FlowStatsEntry(0, FORWARD_PATH_COOKIE, 0, 0, 0, 0),
                new FlowStatsEntry(0, REVERSE_PATH_COOKIE, 0, 0, 0, 0),
                new FlowStatsEntry(0, PROTECTED_REVERSE_PATH_COOKIE, 0, 0, 0, 0)));
    }

    private FlowStatsData getFlowStatsDataDstSwitch() {
        return new FlowStatsData(DST_SWITCH_ID, asList(
                new FlowStatsEntry(0, FORWARD_PATH_COOKIE, 0, 0, 0, 0),
                new FlowStatsEntry(0, REVERSE_PATH_COOKIE, 0, 0, 0, 0),
                new FlowStatsEntry(0, PROTECTED_FORWARD_PATH_COOKIE, 0, 0, 0, 0)));
    }

    private FlowStatsData getFlowStatsDataTransitSwitch() {
        return new FlowStatsData(TRANSIT_SWITCH_ID, asList(
                new FlowStatsEntry(0, FORWARD_PATH_COOKIE, 0, 0, 0, 0),
                new FlowStatsEntry(0, REVERSE_PATH_COOKIE, 0, 0, 0, 0),
                new FlowStatsEntry(0, PROTECTED_FORWARD_PATH_COOKIE, 0, 0, 0, 0),
                new FlowStatsEntry(0, PROTECTED_REVERSE_PATH_COOKIE, 0, 0, 0, 0)));
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
