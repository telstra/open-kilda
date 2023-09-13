/* Copyright 2021 Telstra Open Source
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

import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.topology.stats.model.CommonFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.CommonHaFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.CookieCacheKey;
import org.openkilda.wfm.topology.stats.model.DummyFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.DummyGroupDescriptor;
import org.openkilda.wfm.topology.stats.model.DummyMeterDescriptor;
import org.openkilda.wfm.topology.stats.model.EndpointFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.GroupCacheKey;
import org.openkilda.wfm.topology.stats.model.HaFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptorHandler;
import org.openkilda.wfm.topology.stats.model.MeterCacheKey;
import org.openkilda.wfm.topology.stats.model.StatVlanDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowSubDescriptor;

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;

abstract class BaseCacheChangeHandler implements KildaEntryDescriptorHandler {
    protected final HashSetValuedHashMap<CookieCacheKey, KildaEntryDescriptor> cookieToEntry;
    protected final HashSetValuedHashMap<MeterCacheKey, KildaEntryDescriptor> meterToEntry;
    protected final HashSetValuedHashMap<GroupCacheKey, KildaEntryDescriptor> groupToEntry;

    public BaseCacheChangeHandler(
            HashSetValuedHashMap<CookieCacheKey, KildaEntryDescriptor> cookieToEntry,
            HashSetValuedHashMap<MeterCacheKey, KildaEntryDescriptor> meterToEntry,
            HashSetValuedHashMap<GroupCacheKey, KildaEntryDescriptor> groupToEntry) {
        this.cookieToEntry = cookieToEntry;
        this.meterToEntry = meterToEntry;
        this.groupToEntry = groupToEntry;
    }

    @Override
    public void handle(KildaEntryDescriptor entry) {
        entry.handle(this);
    }

    @Override
    public void handleStatsEntry(CommonFlowDescriptor descriptor) {
        handleFlowStatsEntry(descriptor.getSwitchId(), descriptor.getCookie(), descriptor.getMeterId(), descriptor);
    }

    @Override
    public void handleStatsEntry(StatVlanDescriptor descriptor) {
        if (descriptor.getStatVlans() != null) {
            for (Integer statVlan : descriptor.getStatVlans()) {
                long statVlanCookie = descriptor.getCookie().toBuilder()
                        .type(CookieType.VLAN_STATS_PRE_INGRESS).statsVlan(statVlan).build().getValue();
                cacheAction(new CookieCacheKey(descriptor.getSwitchId(), statVlanCookie), descriptor);
            }
        }
    }

    @Override
    public void handleStatsEntry(EndpointFlowDescriptor descriptor) {
        handleFlowStatsEntry(descriptor.getSwitchId(), descriptor.getCookie(), descriptor.getMeterId(), descriptor);
    }

    @Override
    public void handleStatsEntry(YFlowDescriptor descriptor) {
        cacheAction(new MeterCacheKey(descriptor.getSwitchId(), descriptor.getMeterId().getValue()), descriptor);
    }

    @Override
    public void handleStatsEntry(CommonHaFlowDescriptor descriptor) {
        handleHaFlowStatsEntry(descriptor.getSwitchId(), descriptor.getCookie(),
                descriptor.getMeterId(), descriptor.getYpointGroupId(),
                descriptor.getYPointMeterId(), descriptor);
    }

    @Override
    public void handleStatsEntry(HaFlowDescriptor descriptor) {
        handleHaFlowStatsEntry(descriptor.getSwitchId(), descriptor.getCookie(),
                descriptor.getMeterId(), descriptor.getYpointGroupId(),
                descriptor.getYPointMeterId(), descriptor);
    }

    @Override
    public void handleStatsEntry(YFlowSubDescriptor descriptor) {
        FlowSegmentCookie cookie = descriptor.getCookie();
        handleFlowStatsEntry(descriptor.getSwitchId(), cookie, descriptor.getMeterId(), descriptor);

        FlowSegmentCookie satelliteCookie = cookie.toBuilder().yFlow(true).build();
        cacheAction(new CookieCacheKey(descriptor.getSwitchId(), satelliteCookie.getValue()), descriptor);
    }

    @Override
    public void handleStatsEntry(DummyFlowDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedArgumentMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(DummyMeterDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedArgumentMessage(descriptor.getClass()));
    }

    @Override
    public void handleStatsEntry(DummyGroupDescriptor descriptor) {
        throw new IllegalArgumentException(formatUnexpectedArgumentMessage(descriptor.getClass()));
    }

    private void handleHaFlowStatsEntry(SwitchId switchId, FlowSegmentCookie cookie, MeterId meterId,
                                        GroupId yPointGroupId, MeterId yPointMeterId, KildaEntryDescriptor entry) {
        cacheAction(new CookieCacheKey(switchId, cookie.getValue()), entry);
        cacheAction(
                new CookieCacheKey(
                        switchId, cookie.toBuilder().type(CookieType.SERVER_42_FLOW_RTT_INGRESS).build().getValue()),
                entry);

        if (meterId != null) {
            cacheAction(new MeterCacheKey(entry.getSwitchId(), meterId.getValue()), entry);
        }
        if (yPointMeterId != null) {
            cacheAction(new MeterCacheKey(entry.getSwitchId(), yPointMeterId.getValue()), entry);
        }
        if (yPointGroupId != null) {
            cacheAction(new GroupCacheKey(entry.getSwitchId(), yPointGroupId.getValue()), entry);
        }
    }

    private void handleFlowStatsEntry(
            SwitchId switchId, FlowSegmentCookie cookie, MeterId meterId, KildaEntryDescriptor entry) {
        cacheAction(new CookieCacheKey(switchId, cookie.getValue()), entry);
        cacheAction(new CookieCacheKey(switchId, cookie.toBuilder().looped(true).build().getValue()), entry);
        cacheAction(new CookieCacheKey(switchId, cookie.toBuilder().mirror(true).build().getValue()), entry);
        cacheAction(
                new CookieCacheKey(
                        switchId, cookie.toBuilder().type(CookieType.SERVER_42_FLOW_RTT_INGRESS).build().getValue()),
                entry);

        if (meterId != null) {
            cacheAction(new MeterCacheKey(entry.getSwitchId(), meterId.getValue()), entry);
        }
    }

    protected abstract void cacheAction(CookieCacheKey key, KildaEntryDescriptor entry);

    protected abstract void cacheAction(MeterCacheKey key, KildaEntryDescriptor entry);

    protected abstract void cacheAction(GroupCacheKey key, KildaEntryDescriptor entry);

    private static String formatUnexpectedArgumentMessage(Class<?> klass) {
        return String.format("Kilda entries descriptor cache are not supposed to keep entries of %s", klass.getName());
    }
}
