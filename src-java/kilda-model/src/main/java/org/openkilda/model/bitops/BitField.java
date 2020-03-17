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

import org.openkilda.model.Cookie;

import lombok.Getter;

@Getter
public class BitField {
    private final long mask;
    private final int offset;

    public BitField(long mask) {
        Integer start = null;
        Integer end = null;

        long probe = 1;
        for (int i = 0; i < 8 * 8; i++) {
            boolean isSet = (mask & probe) != 0;
            if (start == null && isSet) {
                start = i;
            } else if (start != null && end == null && !isSet) {
                end = i;
            } else if (end != null && isSet) {
                throw new IllegalArgumentException(String.format(
                        "Illegal bit field mask %s - it contain gaps", Cookie.toString(mask)));
            }

            probe <<= 1;
        }
        if (start == null) {
            throw new IllegalArgumentException("Bit field mask must not be 0");
        }

        this.mask = mask;
        this.offset = start;
    }
}
