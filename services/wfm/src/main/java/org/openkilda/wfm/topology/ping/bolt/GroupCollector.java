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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.WorkflowException;
import org.openkilda.wfm.topology.ping.model.CollectorDescriptor;
import org.openkilda.wfm.topology.ping.model.ExpirableMap;
import org.openkilda.wfm.topology.ping.model.Group;
import org.openkilda.wfm.topology.ping.model.GroupId;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GroupCollector extends Abstract {
    public static final String BOLT_ID = ComponentId.GROUP_COLLECTOR.toString();

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_PING_GROUP, FIELD_ID_CONTEXT);

    public static final String STREAM_ON_DEMAND_ID = "periodic.ping";

    private long expireDelay;

    private ExpirableMap<GroupId, CollectorDescriptor> cache;

    public GroupCollector(int pingTimeout) {
        expireDelay = TimeUnit.SECONDS.toMillis(pingTimeout);
    }

    @Override
    protected void init() {
        super.init();

        cache = new ExpirableMap<>();
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String component = input.getSourceComponent();
        if (TickDeduplicator.BOLT_ID.equals(component)) {
            expire(input);
        } else if (OnDemandResultManager.BOLT_ID.equals(component)) {
            collect(input);
        } else {
            unhandledInput(input);
        }
    }

    private void expire(Tuple input) {
        long now = input.getLongByField(MonotonicTick.FIELD_ID_TIME_MILLIS);
        List<CollectorDescriptor> expired = cache.expire(now);

        if (!expired.isEmpty()) {
            log.warn("Groups {} have been expired and dropped ", expired.stream()
                    .map(CollectorDescriptor::getGroupId)
                    .map(GroupId::getId)
                    .collect(Collectors.toList()));
        }
    }

    private void collect(Tuple input) throws AbstractException {
        CollectorDescriptor descriptor = saveCurrentRecord(input);
        if (descriptor.isCompleted()) {
            Group group = descriptor.makeGroup();
            emitGroup(input, group);
        }
    }

    private CollectorDescriptor saveCurrentRecord(Tuple input) throws AbstractException {
        final PingContext pingContext = pullPingContext(input);

        // expiring is only a memory leakage protection in this place
        // waiting ping command timeout + storm internal processing delay milliseconds
        long expireAt = System.currentTimeMillis() + pingContext.getTimeout() + expireDelay;
        CollectorDescriptor descriptor = new CollectorDescriptor(expireAt, pingContext.getGroup());
        descriptor = cache.addIfAbsent(descriptor);

        try {
            int size = descriptor.add(pingContext);
            GroupId group = descriptor.getGroupId();
            log.debug("Group {} add {} of {} response {}", group.getId(), size, group.getSize(), pingContext);
        } catch (IllegalArgumentException e) {
            throw new WorkflowException(this, input, e.toString());
        }

        return descriptor;
    }

    private void emitGroup(Tuple input, Group group) throws AbstractException {
        String stream = routeBack(input);
        Values output = new Values(group, pullContext(input));
        getOutput().emit(stream, input, output);
    }

    private String routeBack(Tuple input) throws WorkflowException {
        String component = input.getSourceComponent();
        String stream;
        if (OnDemandResultManager.BOLT_ID.equals(component)) {
            stream = STREAM_ON_DEMAND_ID;
        } else {
            final String details = String.format(
                    "there is no route back from %s to %s", getClass().getCanonicalName(), component);
            throw new WorkflowException(this, input, details);
        }
        return stream;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declareStream(STREAM_ON_DEMAND_ID, STREAM_FIELDS);
    }
}
