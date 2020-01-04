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

package org.openkilda.wfm.share.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatchDog {
    private final String name;
    private final long safePeriod;
    private long lastReset;
    private boolean failedNow = false;

    public WatchDog(String name, long safePeriod, long timestamp) {
        this.name = name;
        this.lastReset = timestamp;
        this.safePeriod = safePeriod;
    }

    public void reset(long timestamp) {
        lastReset = timestamp;
        log.debug("WatchDog {}: being kicked", name);
    }

    /**
     * Check and return current "target" status.
     */
    public boolean detectFailure(long timestamp) {
        if (timestamp < lastReset) {
            lastReset = timestamp;
        }

        boolean isFailed = safePeriod < (timestamp - lastReset);
        if (failedNow != isFailed) {
            log.info("WatchDog {}: become {}", name, isFailed ? "failed" : "recovered");
        }
        failedNow = isFailed;

        return isFailed;
    }
}
