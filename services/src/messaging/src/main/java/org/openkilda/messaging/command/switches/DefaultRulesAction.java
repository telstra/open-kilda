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

package org.openkilda.messaging.command.switches;

/**
 * Describes what to do about the switch default rules.
 */
public enum DefaultRulesAction {
    // Drop base rules.
    DROP,

    // Drop base rules, then add them back.
    DROP_ADD,

    // Don't drop the base rules - i.e. ignore them.
    IGNORE,

    // The same as IGNORE and ADD - i.e. re-install the base rules again.
    OVERWRITE
}

