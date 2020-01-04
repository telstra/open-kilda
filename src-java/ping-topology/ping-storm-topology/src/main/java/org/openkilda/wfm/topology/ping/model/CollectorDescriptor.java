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

package org.openkilda.wfm.topology.ping.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Data
public class CollectorDescriptor extends Expirable<GroupId> {
    private final GroupId groupId;
    private final List<PingContext> records = new ArrayList<>();
    private final HashSet<UUID> seenRecords = new HashSet<>();

    public CollectorDescriptor(long expireAt, GroupId groupId) {
        super(expireAt);
        this.groupId = groupId;
    }

    /**
     * Store ping data.
     */
    public int add(PingContext pingContext) {
        if (! seenRecords.add(pingContext.getPingId())) {
            throw new IllegalArgumentException(String.format(
                    "groupId collision detected - ping %s already stored in groupId %s",
                    pingContext.getPing(), getGroupId()));
        }
        records.add(pingContext);
        return records.size();
    }

    public Group makeGroup() {
        return new Group(getGroupId(), getRecords());
    }

    public boolean isCompleted() {
        return records.size() == getGroupId().getSize();
    }

    @Override
    public GroupId getExpirableKey() {
        return getGroupId();
    }
}
