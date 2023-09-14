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

package org.openkilda.testing.service.tsdb.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.HashMap;
import java.util.List;

@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class StatsResponseDto {
    String status;
    ResultData data;
    Stats stats;

    @Data
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    class Stats {
        int seriesFetched;
    }

    @Data
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    class ResultData {
        String resultType;
        List<Result> result;

        @Data
        @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
        class Result {
            HashMap<String, String> metric;
            List<List<Object>> values;
        }
    }
}
