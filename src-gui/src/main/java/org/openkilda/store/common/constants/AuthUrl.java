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

package org.openkilda.store.common.constants;

import org.openkilda.store.auth.constants.AuthType;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Gets the urls.
 *
 * @return the urls
 */

@Getter
public enum AuthUrl {

    OAUTH_TWO(AuthType.OAUTH_TWO,
            new Url[] { Url.OAUTH_GENERATE_TOKEN, Url.OAUTH_REFRESH_TOKEN });

    private AuthType authType;
    
    private Url[] urls;

    /**
     * Instantiates a new auth url.
     *
     * @param authType the auth type
     * @param urls the urls
     */
    private AuthUrl(final AuthType authType, final Url[] urls) {
        this.authType = authType;
        this.urls = urls;
    }
    
    /**
     * Gets the url name.
     *
     * @param authType the auth type
     * @return the url name
     */
    public static List<String> getUrlName(String authType) {
        List<String> list = new ArrayList<String>();
        AuthUrl authUrl = null;
        for (AuthUrl authUrlObj : AuthUrl.values()) {
            if (authUrlObj.getAuthType().getCode().equalsIgnoreCase(authType)) {
                authUrl = authUrlObj;
                break;
            }
        }
        for (Url url : authUrl.urls) {
            list.add(url.getName());
        }
        return list;
    }
}
