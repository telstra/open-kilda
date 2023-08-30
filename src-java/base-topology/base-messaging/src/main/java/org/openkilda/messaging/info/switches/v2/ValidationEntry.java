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
import org.openkilda.messaging.split.SplitIterator;

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
        SplitIterator<ValidationEntry<E>, ValidationEntry.ValidationEntryBuilder<E>> iterator = new SplitIterator<>(
                firstChunkSize, chunkSize, ValidationEntryBuilder::build, ValidationEntry::builder);

        if (excess != null) {
            for (List<E> entry : Utils.split(excess, iterator.getRemainingChunkSize(), chunkSize)) {
                iterator.getCurrentBuilder().excess(entry);
                iterator.next(entry.size()).ifPresent(result::add);
            }
        }

        if (proper != null) {
            for (List<E> entry : Utils.split(proper, iterator.getRemainingChunkSize(), chunkSize)) {
                iterator.getCurrentBuilder().proper(entry);
                iterator.next(entry.size()).ifPresent(result::add);
            }
        }

        if (missing != null) {
            for (List<E> entry : Utils.split(missing, iterator.getRemainingChunkSize(), chunkSize)) {
                iterator.getCurrentBuilder().missing(entry);
                iterator.next(entry.size()).ifPresent(result::add);
            }
        }

        if (misconfigured != null) {
            for (List<MisconfiguredInfo<E>> entry : Utils.split(
                    misconfigured, iterator.getRemainingChunkSize(), chunkSize)) {
                iterator.getCurrentBuilder().misconfigured(entry);
                iterator.next(entry.size()).ifPresent(result::add);
            }
        }

        if (iterator.isAddCurrent()) {
            result.add(iterator.getCurrentBuilder().build());
        }
        return result;
    }
}
