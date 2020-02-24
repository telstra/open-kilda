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

import org.openkilda.wfm.ctrl.ICtrlBolt;

import org.apache.storm.topology.BoltDeclarer;

public class CtrlBoltRef {
    private final String boltId;
    private final ICtrlBolt bolt;
    private final BoltDeclarer declarer;

    public CtrlBoltRef(String boltId, ICtrlBolt bolt, BoltDeclarer declarer) {
        this.boltId = boltId;
        this.bolt = bolt;
        this.declarer = declarer;
    }

    public String getBoltId() {
        return boltId;
    }

    public ICtrlBolt getBolt() {
        return bolt;
    }

    public BoltDeclarer getDeclarer() {
        return declarer;
    }
}
