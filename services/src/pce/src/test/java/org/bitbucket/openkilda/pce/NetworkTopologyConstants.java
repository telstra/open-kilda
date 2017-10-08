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

package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.messaging.info.event.IslChangeType;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchState;

import java.util.Arrays;

public final class NetworkTopologyConstants {
    public static final SwitchInfoData sw1 = new SwitchInfoData("sw1", SwitchState.ACTIVATED, "", "", "", "localhost");
    public static final SwitchInfoData sw2 = new SwitchInfoData("sw2", SwitchState.ACTIVATED, "", "", "", "localhost");
    public static final SwitchInfoData sw3 = new SwitchInfoData("sw3", SwitchState.ADDED, "", "", "", "remote");
    public static final SwitchInfoData sw4 = new SwitchInfoData("sw4", SwitchState.ADDED, "", "", "", "remote");
    public static final SwitchInfoData sw5 = new SwitchInfoData("sw5", SwitchState.REMOVED, "", "", "", "remote");

    public static final IslInfoData isl12 = new IslInfoData(3L, Arrays.asList(
            new PathNode(sw1.getSwitchId(), 1, 0, 3L),
            new PathNode(sw2.getSwitchId(), 2, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl21 = new IslInfoData(3L, Arrays.asList(
            new PathNode(sw2.getSwitchId(), 2, 0, 3L),
            new PathNode(sw1.getSwitchId(), 1, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl23 = new IslInfoData(5L, Arrays.asList(
            new PathNode(sw2.getSwitchId(), 1, 0, 5L),
            new PathNode(sw3.getSwitchId(), 2, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl32 = new IslInfoData(5L, Arrays.asList(
            new PathNode(sw3.getSwitchId(), 2, 0, 5L),
            new PathNode(sw2.getSwitchId(), 1, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl14 = new IslInfoData(5L, Arrays.asList(
            new PathNode(sw1.getSwitchId(), 2, 0, 5L),
            new PathNode(sw4.getSwitchId(), 1, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl41 = new IslInfoData(5L, Arrays.asList(
            new PathNode(sw4.getSwitchId(), 1, 0, 5L),
            new PathNode(sw1.getSwitchId(), 2, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl24 = new IslInfoData(6L, Arrays.asList(
            new PathNode(sw2.getSwitchId(), 3, 0, 6L),
            new PathNode(sw4.getSwitchId(), 2, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl42 = new IslInfoData(6L, Arrays.asList(
            new PathNode(sw4.getSwitchId(), 2, 0, 6L),
            new PathNode(sw2.getSwitchId(), 3, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl54 = new IslInfoData(9L, Arrays.asList(
            new PathNode(sw5.getSwitchId(), 1, 0, 9L),
            new PathNode(sw4.getSwitchId(), 3, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl45 = new IslInfoData(9L, Arrays.asList(
            new PathNode(sw4.getSwitchId(), 3, 0, 9L),
            new PathNode(sw5.getSwitchId(), 1, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl52 = new IslInfoData(7L, Arrays.asList(
            new PathNode(sw5.getSwitchId(), 2, 0, 7L),
            new PathNode(sw2.getSwitchId(), 4, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl25 = new IslInfoData(7L, Arrays.asList(
            new PathNode(sw2.getSwitchId(), 4, 0, 7L),
            new PathNode(sw5.getSwitchId(), 2, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl53 = new IslInfoData(8L, Arrays.asList(
            new PathNode(sw5.getSwitchId(), 3, 0, 8L),
            new PathNode(sw3.getSwitchId(), 1, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
    public static final IslInfoData isl35 = new IslInfoData(8L, Arrays.asList(
            new PathNode(sw3.getSwitchId(), 1, 0, 8L),
            new PathNode(sw5.getSwitchId(), 3, 1, 0L)),
            10L, IslChangeType.DISCOVERED, 10L);
}
