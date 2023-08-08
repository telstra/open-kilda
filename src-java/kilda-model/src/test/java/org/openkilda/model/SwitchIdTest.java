/* Copyright 2022 Telstra Open Source
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

package org.openkilda.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class SwitchIdTest {

    @Test
    public void colonSeparatedBytesIllegalArgumentExceptionNegativeOffset() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new SwitchId("00").colonSeparatedBytes(new char[]{'t', 'e', 's', 't'}, -1));
        assertEquals(thrown.getMessage(), String.format(
                "Illegal offset value %d (expect offset > 0 and offset %% 2 == 0 and offset < hex.length)", -1));

    }

    @Test
    public void colonSeparatedBytesIllegalArgumentExceptionOddOffset() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new SwitchId("00").colonSeparatedBytes(new char[]{'t', 'e', 's', 't'}, 3));
        assertEquals(thrown.getMessage(), String.format(
                "Illegal offset value %d (expect offset > 0 and offset %% 2 == 0 and offset < hex.length)", 3));

    }

    @Test
    public void colonSeparatedBytesIllegalArgumentExceptionMoreThenHexLength() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new SwitchId("00").colonSeparatedBytes(new char[]{'t', 'e', 's', 't'}, 6));
        assertEquals(thrown.getMessage(), String.format(
                "Illegal offset value %d (expect offset > 0 and offset %% 2 == 0 and offset < hex.length)", 6));
    }

    @Test
    public void colonSeparatedBytesPositive() {
        String switchIdString = "fe:dc:ba:98:76:54:32:10";
        SwitchId switchId = new SwitchId(switchIdString);

        char[] hexArray = String.format("%016x", switchId.toLong()).toCharArray();

        assertEquals(switchIdString, switchId.colonSeparatedBytes(hexArray, 0));
        assertEquals(switchIdString.substring(3), switchId.colonSeparatedBytes(hexArray, 2));
        assertEquals(switchIdString.substring(6), switchId.colonSeparatedBytes(hexArray, 4));
        assertEquals(switchIdString.substring(9), switchId.colonSeparatedBytes(hexArray, 6));
        assertEquals(switchIdString.substring(12), switchId.colonSeparatedBytes(hexArray, 8));
        assertEquals(switchIdString.substring(15), switchId.colonSeparatedBytes(hexArray, 10));
        assertEquals(switchIdString.substring(18), switchId.colonSeparatedBytes(hexArray, 12));
        assertEquals(switchIdString.substring(21), switchId.colonSeparatedBytes(hexArray, 14));
    }

    @Test
    public void trimStringForSwitchId() {
        String switchIdString = "  fe:dc:ba:98:76:54:32:10  ";
        SwitchId switchId = new SwitchId(switchIdString);

        assertEquals(new SwitchId("fe:dc:ba:98:76:54:32:10"), switchId);
    }
}
