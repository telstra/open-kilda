/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.info.event;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import org.junit.Test;

public class IslInfoDataTest {

    @Test
    public void shouldReturnTrueWhenSelfLooped() {
        final String switchId = "00:00:00:00:00:00:00:01";
        PathNode source = new PathNode(switchId, 1, 0);
        PathNode destination = new PathNode(switchId, 2, 1);
        IslInfoData isl = new IslInfoData(Lists.newArrayList(source, destination), IslChangeType.DISCOVERED);

        assertTrue(isl.isSelfLooped());
    }

    @Test
    public void shouldReturnFalseWhenNotSelfLooped() {
        final String srcSwitch = "00:00:00:00:00:00:00:01";
        final String dstSwitch = "00:00:00:00:00:00:00:02";
        PathNode source = new PathNode(srcSwitch, 1, 0);
        PathNode destination = new PathNode(dstSwitch, 2, 1);
        IslInfoData isl = new IslInfoData(Lists.newArrayList(source, destination), IslChangeType.DISCOVERED);

        assertFalse(isl.isSelfLooped());
    }
}
