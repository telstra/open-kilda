/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.error;

import org.openkilda.wfm.AbstractBolt;

import lombok.Getter;
import org.apache.storm.tuple.Tuple;

public class PipelineException extends Exception {
    @Getter
    private final Tuple input;
    @Getter
    private final String field;

    public PipelineException(AbstractBolt consumer, Tuple input, String field, String reason) {
        super(formatMessage(consumer, input, field, reason));

        this.input = input;
        this.field = field;
    }

    private static String formatMessage(AbstractBolt consumer, Tuple input, String field, String reason) {
        String source = input.getSourceComponent();
        String stream = input.getSourceStreamId();
        return String.format(
                "Invalid input tuple %s(%s) ==> %s field %s - %s",
                source, stream, consumer.getClass().getCanonicalName(), field, reason);
    }
}
