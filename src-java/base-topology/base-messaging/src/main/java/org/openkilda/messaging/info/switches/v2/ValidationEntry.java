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

package org.openkilda.messaging.info.switches.v2;

import org.openkilda.messaging.Utils;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
@Builder
public class ValidationEntry<E> {
    List<E> missing;
    List<E> excess;
    List<E> proper;
    List<MisconfiguredInfo<E>> misconfigured;

    static <E> List<ValidationEntry<E>> split(
            int firstChunkSize, int chunkSize, List<E> missing, List<E> excess, List<E> proper,
            List<MisconfiguredInfo<E>> misconfigured) {

        List<ValidationEntry<E>> result = new ArrayList<>();
        ValidationEntryBuilder<E> current = ValidationEntry.builder();
        int currentSize = firstChunkSize;
        boolean addCurrent = true;

        if (excess != null) {
            for (List<E> entry : Utils.split(excess, currentSize, chunkSize)) {
                current.excess(entry);
                currentSize -= entry.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    current = ValidationEntry.builder();
                    currentSize = chunkSize;
                    addCurrent = false;
                }
            }
        }

        if (proper != null) {
            for (List<E> entry : Utils.split(proper, currentSize, chunkSize)) {
                current.proper(entry);
                currentSize -= entry.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    current = ValidationEntry.builder();
                    currentSize = chunkSize;
                    addCurrent = false;
                }
            }
        }

        if (missing != null) {
            for (List<E> entry : Utils.split(missing, currentSize, chunkSize)) {
                current.missing(entry);
                currentSize -= entry.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    current = ValidationEntry.builder();
                    currentSize = chunkSize;
                    addCurrent = false;
                }
            }
        }

        if (misconfigured != null) {
            for (List<MisconfiguredInfo<E>> entry : Utils.split(
                    misconfigured, currentSize, chunkSize)) {
                current.misconfigured(entry);
                currentSize -= entry.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    current = ValidationEntry.builder();
                    currentSize = chunkSize;
                    addCurrent = false;
                }
            }
        }

        if (addCurrent) {
            result.add(current.build());
        }
        return result;
    }
}
