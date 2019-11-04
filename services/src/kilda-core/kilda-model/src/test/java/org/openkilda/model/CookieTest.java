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
import org.junit.Test;

public class CookieTest extends AbstractCookieTest {
    @Test
    public void ensureNoEmptyMasks() {
        try {
            new Cookie.BitField(0);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void ensureNoGapsInMask() {
        try {
            new Cookie.BitField(5);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void ensureNoChangeOutsideFieldBoundary() {
        testFieldReadWrite(new Cookie(0x0009L), 0x000BL, new Cookie.BitField(0x6), 0x1);
        testFieldReadWrite(new Cookie(0x0000L), 0x0002L, new Cookie.BitField(0x6), 0x1);
        testFieldReadWrite(new Cookie(0x0000L), 0x0006L, new Cookie.BitField(0x6), 0x7, 0x3);
    }

    @Test
    public void goBeyondUpperBoundary() {
        testFieldReadWrite(new Cookie(0), 0x8000_0000_0000_0000L, new Cookie.BitField(0x8000_0000_0000_0000L), 3);
    }

    @Test
    public void ensureOverrideZeroBitsInsideField() {
        testFieldReadWrite(new Cookie(0x0004L), 0x000AL, new Cookie.BitField(0xE), 0x5);
    }

    @Test
    public void defaultFlagLocation() {
        testFieldReadWrite(new Cookie(-1L), ~0x8000_0000_0000_0000L, Cookie.DEFAULT_FLAG, 0);
    }

    @Test
    public void flowForwardDirectionFlagLocation() {
        testFieldReadWrite(new Cookie(-1L), ~0x4000_0000_0000_0000L, Cookie.FLOW_FORWARD_DIRECTION_FLAG, 0);
    }

    @Test
    public void flowReverseDirectionFlagLocation() {
        testFieldReadWrite(new Cookie(-1L), ~0x2000_0000_0000_0000L, Cookie.FLOW_REVERSE_DIRECTION_FLAG, 0);
    }

    @Test
    public void typeFieldLocation() {
        testFieldReadWrite(new Cookie(-1L), ~0x1FF0_0000_0000_0000L, Cookie.TYPE_FIELD, 0);
    }

    @Test
    public void effectiveFlowIdFieldLocation() {
        testFieldReadWrite(new Cookie(-1), ~0x0000_0000_000F_FFFF, Cookie.FLOW_EFFECTIVE_ID_FIELD, 0);
    }

    private void testFieldReadWrite(Cookie cookie, long expected, Cookie.BitField field, long value) {
        testFieldReadWrite(cookie, expected, field, value, value);
    }

    private void testFieldReadWrite(
            Cookie cookie, long expected, Cookie.BitField field, long valueWrite, long valueRead) {
        cookie.setField(field, valueWrite);
        Assert.assertEquals(expected, cookie.getValue());
        Assert.assertEquals(valueRead, cookie.getField(field));
    }

    @Test
    public void ensureNoFieldsIntersection() {
        testFieldsIntersection(Cookie.ALL_FIELDS, new Cookie.BitField[0]);
    }
}
