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

package org.openkilda.wfm.topology.discovery.controller;

public enum BfdPortFsmEvent {
    NEXT, KILL, FAIL,

    HISTORY,
    BI_ISL_UP, BI_ISL_MOVE,
    PORT_UP, PORT_DOWN,

    SPEAKER_SUCCESS, SPEAKER_FAIL, SPEAKER_TIMEOUT,

    _INIT_CHOICE_CLEAN, _INIT_CHOICE_DIRTY,
    _CLEANING_CHOICE_READY, _CLEANING_CHOICE_HOLD
}
