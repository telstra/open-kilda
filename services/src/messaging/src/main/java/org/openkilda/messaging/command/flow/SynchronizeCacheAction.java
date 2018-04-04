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

package org.openkilda.messaging.command.flow;

/**
 * Describes how to synchronize the cache.
 */
public enum SynchronizeCacheAction {
    // Synchronize / refresh cache : compare the data in cache and DB, propagate updates only for the difference.
    SYNCHRONIZE_CACHE,

    // Purge cache : re-read data from DB and forcibly propagate updates for all records.
    INVALIDATE_CACHE,

    NONE
}
