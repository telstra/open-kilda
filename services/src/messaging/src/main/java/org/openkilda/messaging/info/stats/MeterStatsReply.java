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

package org.openkilda.messaging.info.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode
public class MeterStatsReply implements Serializable {

    @JsonProperty
    private long xid;

    @JsonProperty
    private List<MeterStatsEntry> entries;

    @JsonCreator
    public MeterStatsReply(@JsonProperty("xid") long xid, @JsonProperty("entries") List<MeterStatsEntry> entries) {
        this.xid = xid;
        this.entries = entries;
    }

    public long getXid() {
        return xid;
    }

    public List<MeterStatsEntry> getEntries() {
        return entries;
    }
}
