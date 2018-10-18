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

package org.openkilda.persistence.repositories;

import org.openkilda.model.FlowCookie;

import java.util.Optional;

public interface FlowCookieRepository extends Repository<FlowCookie> {
    Optional<FlowCookie> findByCookie(long unmaskedCookie);

    /**
     * Find an unmasked cookie which is not assigned to any flow.
     * Use the provided {@code defaultCookie} as the first candidate.
     *
     * @param defaultCookie the potential cookie to be checked first.
     * @return an unmasked cookie value or {@link Optional#empty()} if no cookie available.
     */
    Optional<Long> findUnassignedCookie(long defaultCookie);
}
