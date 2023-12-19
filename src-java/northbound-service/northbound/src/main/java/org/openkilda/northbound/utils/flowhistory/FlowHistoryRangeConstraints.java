/* Copyright 2023 Telstra Open Source
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

package org.openkilda.northbound.utils.flowhistory;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.northbound.converter.LongToInstantConverter;
import org.openkilda.northbound.utils.RequestCorrelationId;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

@Value
public class FlowHistoryRangeConstraints {
    private static final int DEFAULT_MAX_HISTORY_RECORD_COUNT = 100;
    Instant timeFrom;
    Instant timeTo;
    int maxCount;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    Predicate<Integer> contentRangeRequiredPredicate;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public FlowHistoryRangeConstraints(Optional<Long> timeFromInput,
                                       Optional<Long> timeToInput,
                                       Optional<Integer> maxCountInput) {
        if (timeFromInput.isPresent() && timeToInput.isPresent() && timeFromInput.get() > timeToInput.get()) {
            throw new MessageException(RequestCorrelationId.getId(), System.currentTimeMillis(),
                    ErrorType.PARAMETERS_INVALID, format("Invalid 'timeFrom' and 'timeTo' arguments: %s and %s",
                    timeFromInput.get(), timeToInput.get()),
                    "'timeFrom' must be less than or equal to 'timeTo'");
        }

        this.timeFrom = timeFromInput.map(LongToInstantConverter::convert).orElseGet(() -> Instant.ofEpochSecond(0L));
        this.timeTo = timeToInput.map(LongToInstantConverter::convert).orElseGet(Instant::now);

        this.maxCount = maxCountInput.orElseGet(() -> {
            if (timeFromInput.isPresent() || timeToInput.isPresent()) {
                return Integer.MAX_VALUE;
            } else {
                return DEFAULT_MAX_HISTORY_RECORD_COUNT;
            }
        });

        if (this.getMaxCount() < 1) {
            throw new MessageException(RequestCorrelationId.getId(), System.currentTimeMillis(),
                    ErrorType.PARAMETERS_INVALID, format("Invalid `max_count` argument '%s'.",
                    this.getMaxCount()),
                    "`max_count` argument must be positive.");
        }

        contentRangeRequiredPredicate = size ->
                (!maxCountInput.isPresent() && !timeFromInput.isPresent() && !timeToInput.isPresent()
                        && size > DEFAULT_MAX_HISTORY_RECORD_COUNT);
    }

    public boolean isContentRangeRequired(int size) {
        return contentRangeRequiredPredicate.test(size);
    }
}
