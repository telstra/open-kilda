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

package org.openkilda.floodlight.command.meter;

import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.model.MeterId;
import org.openkilda.model.of.MeterSchema;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Optional;

@Value
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MeterReport extends SpeakerCommandReport {
    private final MeterId meterId;
    private final MeterSchema schema;

    public MeterReport(MeterId meterId) {
        this(meterId, null, null);
    }

    public MeterReport(MeterSchema schema) {
        this(null, schema, null);
    }

    public MeterReport(Exception error) {
        this(null, null, error);
    }

    private MeterReport(MeterId meterId, MeterSchema schema, Exception error) {
        super(error);
        if (meterId == null && schema != null) {
            meterId = schema.getMeterId();
        }

        this.meterId = meterId;
        this.schema = schema;
    }

    public Optional<MeterSchema> getSchema() {
        return Optional.ofNullable(schema);
    }
}
