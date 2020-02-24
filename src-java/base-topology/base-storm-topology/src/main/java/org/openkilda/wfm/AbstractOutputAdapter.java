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

package org.openkilda.wfm;

import org.openkilda.wfm.error.PipelineException;

import lombok.Getter;
import org.apache.storm.tuple.Tuple;

import java.util.List;

public class AbstractOutputAdapter {
    private final AbstractBolt owner;
    protected final Tuple tuple;

    @Getter
    protected final CommandContext context;

    public AbstractOutputAdapter(AbstractBolt owner, Tuple tuple) {
        this.owner = owner;
        this.tuple = tuple;

        CommandContext context;
        try {
            context = owner.pullContext(tuple);
        } catch (PipelineException e) {
            context = null;
        }

        this.context = context;
    }

    protected void emit(List<Object> payload) {
        owner.emit(tuple, payload);
    }

    protected void emit(String stream, List<Object> payload) {
        owner.emit(stream, tuple, payload);
    }
}
