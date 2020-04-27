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

package org.openkilda.model;

import org.openkilda.model.bitops.BitField;

import org.junit.Assert;

abstract class GenericCookieTest {
    protected void testFieldsIntersection(BitField[] fields) {
        for (int leftIdx = 0; leftIdx < fields.length; leftIdx++) {
            long leftField = CookieBase.setField(0L, fields[leftIdx], -1L);

            for (int rightIdx = 0; rightIdx < fields.length; rightIdx++) {
                if (leftIdx == rightIdx) {
                    continue;
                }

                long rightField = CookieBase.setField(0L, fields[rightIdx], -1);
                Assert.assertEquals(
                        String.format(
                                "Detect cookie fields collision between 0x%016x and 0x%016x", leftField, rightField),
                        0, (leftField & rightField));
            }
        }
    }

    protected void testFieldReadWrite(long blank, long expected, BitField field, long value) {
        testFieldReadWrite(blank, expected, field, value, value);
    }

    protected void testFieldReadWrite(
            long blank, long expected, BitField field, long valueWrite, long valueRead) {
        long result = CookieBase.setField(blank, field, valueWrite);
        Assert.assertEquals(expected, result);
        Assert.assertEquals(valueRead, new Cookie(result).getField(field));
    }
}
