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

package org.openkilda.messaging.model;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SwitchIdTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void colonSeparatedBytesIllegalArgumentExceptionNegativeOffset() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(String.format(
                "Illegal offset value %d (expect offset > 0 and offset %% 2 == 0 and offset < hex.length)", -1));

        new SwitchId("00").colonSeparatedBytes(new char[] {'t', 'e', 's', 't'}, -1);
    }

    @Test
    public void colonSeparatedBytesIllegalArgumentExceptionOddOffset() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(String.format(
                "Illegal offset value %d (expect offset > 0 and offset %% 2 == 0 and offset < hex.length)", 3));

        new SwitchId("00").colonSeparatedBytes(new char[] {'t', 'e', 's', 't'}, 3);
    }

    @Test
    public void colonSeparatedBytesIllegalArgumentExceptionMoreThenHexLength() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(String.format(
                "Illegal offset value %d (expect offset > 0 and offset %% 2 == 0 and offset < hex.length)", 6));

        new SwitchId("00").colonSeparatedBytes(new char[] {'t', 'e', 's', 't'}, 6);
    }

    @Test
    public void colonSeparatedBytesPositive() {
        String switchIdString = "fe:dc:ba:98:76:54:32:10";
        SwitchId switchId = new SwitchId(switchIdString);

        char[] hexArray = String.format("%016x", switchId.toLong()).toCharArray();

        Assert.assertEquals(switchIdString, switchId.colonSeparatedBytes(hexArray, 0));
        Assert.assertEquals(switchIdString.substring(3), switchId.colonSeparatedBytes(hexArray, 2));
        Assert.assertEquals(switchIdString.substring(6), switchId.colonSeparatedBytes(hexArray, 4));
        Assert.assertEquals(switchIdString.substring(9), switchId.colonSeparatedBytes(hexArray, 6));
        Assert.assertEquals(switchIdString.substring(12), switchId.colonSeparatedBytes(hexArray, 8));
        Assert.assertEquals(switchIdString.substring(15), switchId.colonSeparatedBytes(hexArray, 10));
        Assert.assertEquals(switchIdString.substring(18), switchId.colonSeparatedBytes(hexArray, 12));
        Assert.assertEquals(switchIdString.substring(21), switchId.colonSeparatedBytes(hexArray, 14));
    }
}
