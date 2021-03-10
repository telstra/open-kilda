/* Copyright 2021 Telstra Open Source
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

package org.openkilda.config.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class EnumLowerCaseConverterTest {
    private enum TestEnum {
        FIRST_VALUE, VALUE_2
    }

    private final EnumLowerCaseConverter converter = new EnumLowerCaseConverter();

    @Test
    public void toStringTest() {
        assertEquals("first_value", converter.toString(TestEnum.class, TestEnum.FIRST_VALUE, null));
        assertEquals("value_2", converter.toString(TestEnum.class, TestEnum.VALUE_2, null));
        assertNull(converter.toString(TestEnum.class, null, null));
    }

    @Test
    public void fromValidStringTest() {
        assertEquals(TestEnum.FIRST_VALUE, converter.fromString(TestEnum.class, "first_value", null));
        assertEquals(TestEnum.FIRST_VALUE, converter.fromString(TestEnum.class, "First_Value", null));
        assertEquals(TestEnum.FIRST_VALUE, converter.fromString(TestEnum.class, "FIRST_VALUE", null));
        assertEquals(TestEnum.VALUE_2, converter.fromString(TestEnum.class, "value_2", null));
        assertEquals(TestEnum.VALUE_2, converter.fromString(TestEnum.class, "Value_2", null));
        assertEquals(TestEnum.VALUE_2, converter.fromString(TestEnum.class, "VALUE_2", null));
        assertNull(converter.fromString(TestEnum.class, null, null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromInvalidStringTest() {
        converter.fromString(TestEnum.class, "123", null);
    }
}
