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

package org.openkilda.messaging.info;

import org.openkilda.bluegreen.kafka.TransportAdapter;
import org.openkilda.bluegreen.kafka.TransportErrorReport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

import java.util.Optional;

/**
 * Class represents error info data.
 */
//FIXME: should be deleted after OpenTSDB topology messaging is refactored to use Messages instead of InfoData
@Data
@EqualsAndHashCode(callSuper = false)
public class TransportErrorWrapper extends InfoData implements TransportAdapter {
    private final TransportErrorReport errorReport;

    public TransportErrorWrapper(@NonNull TransportErrorReport errorReport) {
        this.errorReport = errorReport;
    }

    @JsonIgnore
    @Override
    public Optional<TransportErrorReport> getErrorReport() {
        return Optional.of(errorReport);
    }
}
