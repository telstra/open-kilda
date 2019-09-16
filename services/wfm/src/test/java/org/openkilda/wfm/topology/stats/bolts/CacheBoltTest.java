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

package org.openkilda.wfm.topology.stats.bolts;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.topology.stats.CacheFlowEntry;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class CacheBoltTest {

    private static final Long FORWARD_PATH_COOKIE = 1L;
    private static final Long PROTECTED_FORWARD_PATH_COOKIE = 2L;
    private static final Long REVERSE_PATH_COOKIE = 3L;
    private static final Long PROTECTED_REVERSE_PATH_COOKIE = 4L;

    @Mock
    private PersistenceManager persistenceManager;
    @Mock
    private RepositoryFactory repositoryFactory;
    @Mock
    private FlowRepository flowRepository;

    @Test
    public void cacheBoltInitTest() {
        Flow flow = getFlow();
        when(flowRepository.findAll()).thenReturn(Collections.singletonList(flow));
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        CacheBolt cacheBolt = new CacheBolt(persistenceManager);
        cacheBolt.init();

        Map<Long, CacheFlowEntry> cookieToFlowCache = cacheBolt.createCookieToFlowCache(getFlowStatsData());

        Assert.assertEquals(4, cookieToFlowCache.size());
        CacheFlowEntry forwardCacheFlowEntry = cookieToFlowCache.get(FORWARD_PATH_COOKIE);
        Assert.assertEquals(flow.getFlowId(), forwardCacheFlowEntry.getFlowId());
        Assert.assertEquals(FORWARD_PATH_COOKIE, forwardCacheFlowEntry.getCookie());

        CacheFlowEntry protectedForwardCacheFlowEntry = cookieToFlowCache.get(PROTECTED_FORWARD_PATH_COOKIE);
        Assert.assertEquals(flow.getFlowId(), protectedForwardCacheFlowEntry.getFlowId());
        Assert.assertEquals(PROTECTED_FORWARD_PATH_COOKIE, protectedForwardCacheFlowEntry.getCookie());

        CacheFlowEntry reverseCacheFlowEntry = cookieToFlowCache.get(REVERSE_PATH_COOKIE);
        Assert.assertEquals(flow.getFlowId(), reverseCacheFlowEntry.getFlowId());
        Assert.assertEquals(REVERSE_PATH_COOKIE, reverseCacheFlowEntry.getCookie());

        CacheFlowEntry protectedReverseCacheFlowEntry = cookieToFlowCache.get(PROTECTED_REVERSE_PATH_COOKIE);
        Assert.assertEquals(flow.getFlowId(), protectedReverseCacheFlowEntry.getFlowId());
        Assert.assertEquals(PROTECTED_REVERSE_PATH_COOKIE, protectedReverseCacheFlowEntry.getCookie());
    }

    private Flow getFlow() {
        Switch srcSwitch = Switch.builder().switchId(new SwitchId(1L)).build();
        Switch destSwitch = Switch.builder().switchId(new SwitchId(2L)).build();
        Flow flow = Flow.builder()
                .flowId(uuid())
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .build();
        flow.setForwardPath(getPath(flow, srcSwitch, destSwitch, FORWARD_PATH_COOKIE));
        flow.setProtectedForwardPath(getPath(flow, srcSwitch, destSwitch, PROTECTED_FORWARD_PATH_COOKIE));
        flow.setReversePath(getPath(flow, destSwitch, srcSwitch, REVERSE_PATH_COOKIE));
        flow.setProtectedReversePath(getPath(flow, destSwitch, srcSwitch, PROTECTED_REVERSE_PATH_COOKIE));
        return flow;
    }

    private FlowPath getPath(Flow flow, Switch src, Switch dest, long cookie) {
        return FlowPath.builder()
                .pathId(new PathId(uuid()))
                .flow(flow)
                .srcSwitch(src)
                .destSwitch(dest)
                .cookie(new Cookie(cookie))
                .build();
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

    private FlowStatsData getFlowStatsData() {
        return new FlowStatsData(new SwitchId(3L), asList(new FlowStatsEntry(0, FORWARD_PATH_COOKIE, 0, 0, 0, 0),
                new FlowStatsEntry(0, PROTECTED_FORWARD_PATH_COOKIE, 0, 0, 0, 0),
                new FlowStatsEntry(0, REVERSE_PATH_COOKIE, 0, 0, 0, 0),
                new FlowStatsEntry(0, PROTECTED_REVERSE_PATH_COOKIE, 0, 0, 0, 0)));
    }
}
