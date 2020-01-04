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

package org.openkilda.floodlight.converter;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.info.stats.TableStatsEntry;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFTableStatsEntry;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

public class OfTableStatsMapperTest {

    @Test
    public void shouldConvertSuccessfully() {
        OFFactoryVer13 ofFactoryVer13 = new OFFactoryVer13();
        OFTableStatsEntry entry = ofFactoryVer13.buildTableStatsEntry()
                .setTableId(TableId.of(11))
                .setActiveCount(10)
                .setMatchedCount(U64.of(100001L))
                .setLookupCount(U64.of(100002L))
                .build();
        TableStatsEntry result = OfTableStatsMapper.INSTANCE.toTableStatsEntry(entry);
        assertEquals(result.getTableId(), entry.getTableId().getValue());
        assertEquals(result.getActiveEntries(), entry.getActiveCount());
        assertEquals(result.getLookupCount(), entry.getLookupCount().getValue());
        assertEquals(result.getMatchedCount(), entry.getMatchedCount().getValue());
    }
}
