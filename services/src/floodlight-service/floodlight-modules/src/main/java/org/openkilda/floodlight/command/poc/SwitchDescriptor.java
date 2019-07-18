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

package org.openkilda.floodlight.command.poc;

import lombok.Getter;
import lombok.ToString;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.types.TableId;

import java.util.Iterator;

@Getter
@ToString
class SwitchDescriptor {
    private final IOFSwitch sw;

    private final TableId tableDispatch;
    private final TableId tablePreIngress;
    private final TableId tableIngress;
    private final TableId tablePostIngress;
    private final TableId tableEgress;
    private final TableId tableTransit;

    public SwitchDescriptor(IOFSwitch sw) {
        this.sw = sw;

        Iterator<TableId> tablesIterator = sw.getTables().iterator();
        tableDispatch = tablesIterator.next();
        tablePreIngress = tablesIterator.next();
        tableIngress = tablesIterator.next();
        tablePostIngress = tablesIterator.next();
        tableEgress = tablesIterator.next();
        tableTransit = tablesIterator.next();
    }
}
