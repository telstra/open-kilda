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

package org.openkilda.floodlight.utils.metadata;

import org.openkilda.model.bitops.BitField;

import org.junit.Assert;
import org.projectfloodlight.openflow.types.U64;

public class MetadataBaseTest {
    protected void testFieldsIntersection(BitField[] fields) {
        for (int leftIdx = 0; leftIdx < fields.length; leftIdx++) {
            U64 leftField = MetadataBase.setField(U64.ZERO, -1L, fields[leftIdx]);

            for (int rightIdx = 0; rightIdx < fields.length; rightIdx++) {
                if (leftIdx == rightIdx) {
                    continue;
                }

                U64 rightField = MetadataBase.setField(U64.ZERO, -1, fields[rightIdx]);
                Assert.assertEquals(
                        String.format("Detect bit-fields collision between %s and %s", leftField, rightField),
                        U64.ZERO, leftField.and(rightField));
            }
        }
    }
}
