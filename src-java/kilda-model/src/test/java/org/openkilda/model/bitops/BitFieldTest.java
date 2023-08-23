/* Copyright 2020 Telstra Open Source
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

package org.openkilda.model.bitops;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BitFieldTest {
    @Test
    public void ensureNoEmptyMasks() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new BitField(0);
        });
    }

    @Test
    public void ensureNoGapsInMask() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new BitField(5);
        });
    }

    @Test
    public void verifyOffset() {
        BitField field = new BitField(6);

        Assertions.assertEquals(1, field.getOffset());
        Assertions.assertEquals(6, field.getMask());
    }
}
