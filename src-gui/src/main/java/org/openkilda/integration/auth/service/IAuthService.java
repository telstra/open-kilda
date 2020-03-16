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

package org.openkilda.integration.auth.service;

import org.openkilda.store.auth.constants.AuthType;
import org.openkilda.store.model.AuthConfigDto;
import org.openkilda.store.model.UrlDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

public abstract class IAuthService {

    private static Map<AuthType, IAuthService> serviceByAuthType = new HashMap<AuthType, IAuthService>();

    public IAuthService() {

    }
    
    @PostConstruct
    public void init() {
        serviceByAuthType.put(getAuthType(), this);
    }

    public abstract AuthType getAuthType();

    public abstract <T> T getResponse(final UrlDto request, final AuthConfigDto authDto,
            final Class<T> responseClass);

    public abstract <T> List<T> getResponseList(final UrlDto request, final AuthConfigDto authDto,
            final Class<T> responseClass);

    public static IAuthService getService(final AuthType authType) {
        return serviceByAuthType.get(authType);
    }
}
