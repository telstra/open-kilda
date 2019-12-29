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

package org.openkilda.model;

import org.junit.Assert;

abstract class AbstractCookieTest {
    protected void testFieldsIntersection(Cookie.BitField[] fields, Cookie.BitField[] parentFields) {
        long parentMask = 0;
        for (Cookie.BitField field : parentFields) {
            parentMask |= new Cookie(0L).setField(field, -1).getValue();
        }

        for (int leftIdx = 0; leftIdx < fields.length; leftIdx++) {
            long leftField = new Cookie(0L).setField(fields[leftIdx], -1L).getValue();
            testIntersection(leftField, parentMask);

            for (int rightIdx = 0; rightIdx < fields.length; rightIdx++) {
                if (leftIdx == rightIdx) {
                    continue;
                }

                long rightField = new Cookie(0L).setField(fields[rightIdx], -1).getValue();
                testIntersection(leftField, rightField);
            }
        }
    }

    private void testIntersection(long leftField, long rightField) {
        Assert.assertTrue(
                String.format("Detect cookie fields collision between 0x%016x and 0x%016x", leftField, rightField),
                (leftField & rightField) == 0);
    }
}
