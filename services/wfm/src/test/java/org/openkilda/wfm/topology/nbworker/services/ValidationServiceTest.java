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

package org.openkilda.wfm.topology.nbworker.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.SyncRulesResponse;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ValidationServiceTest {

    @Test
    public void empty() {
        ValidationService validationService = new ValidationService(repositoryFactory().build());
        SyncRulesResponse response = validationService.validate(generateSwitchFlowEntries());
        assertTrue(response.getMissingRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertTrue(response.getExcessRules().isEmpty());
    }

    @Test
    public void simpleSegmentCookies() {
        ValidationService validationService =
                new ValidationService(repositoryFactory().withSegmentsCookies(2L, 3L).build());
        SyncRulesResponse response = validationService.validate(generateSwitchFlowEntries(1L, 2L));
        assertEquals(ImmutableList.of(3L), response.getMissingRules());
        assertEquals(ImmutableList.of(2L), response.getProperRules());
        assertEquals(ImmutableList.of(1L), response.getExcessRules());
    }

    @Test
    public void segmentAndIngressCookies() {
        ValidationService validationService =
                new ValidationService(repositoryFactory().withSegmentsCookies(2L).withIngressCookies(1L).build());
        SyncRulesResponse response = validationService.validate(generateSwitchFlowEntries(1L, 2L));
        assertTrue(response.getMissingRules().isEmpty());
        assertEquals(ImmutableSet.of(1L, 2L), new HashSet<>(response.getProperRules()));
        assertTrue(response.getExcessRules().isEmpty());
    }

    @Test
    public void ignoreDefaulRules() {
        ValidationService validationService = new ValidationService(repositoryFactory().build());
        SyncRulesResponse response = validationService.validate(generateSwitchFlowEntries(0x8000000000000002L));
        assertTrue(response.getMissingRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertTrue(response.getExcessRules().isEmpty());
    }

    private SwitchFlowEntries generateSwitchFlowEntries(long... cookies) {
        List<FlowEntry> flowEntryList = new ArrayList<>(cookies.length);
        for (long cookie : cookies) {
            flowEntryList.add(new FlowEntry(cookie, null, 0, 0, 0, 0, "", 0, 0, 0, 0, null, null, null));
        }
        return new SwitchFlowEntries(new SwitchId("00:10"), flowEntryList);
    }

    private RepositoryFactoryBuilder repositoryFactory() {
        return new RepositoryFactoryBuilder();
    }

    private static class RepositoryFactoryBuilder {
        private FlowPathRepository flowPathRepository = mock(FlowPathRepository.class);

        private long[] segmentsCookies = new long[0];
        private long[] ingressCookies = new long[0];

        private RepositoryFactoryBuilder withSegmentsCookies(long... cookies) {
            segmentsCookies = cookies;
            return this;
        }

        private RepositoryFactoryBuilder withIngressCookies(long... cookies) {
            ingressCookies = cookies;
            return this;
        }

        private RepositoryFactory build() {
            List<FlowPath> pathsBySegment = new ArrayList<>(segmentsCookies.length);
            for (long cookie : segmentsCookies) {
                FlowPath flowPath = mock(FlowPath.class);
                when(flowPath.getCookie()).thenReturn(new Cookie(cookie));
                pathsBySegment.add(flowPath);
            }
            List<FlowPath> flowPaths = new ArrayList<>(ingressCookies.length);
            for (long cookie : ingressCookies) {
                FlowPath flowPath = mock(FlowPath.class);
                when(flowPath.getCookie()).thenReturn(new Cookie(cookie));
                flowPaths.add(flowPath);
            }
            when(flowPathRepository.findBySegmentDestSwitch(any())).thenReturn(pathsBySegment);
            when(flowPathRepository.findByEndpointSwitch(any())).thenReturn(flowPaths);

            RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
            when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
            return repositoryFactory;
        }
    }
}
