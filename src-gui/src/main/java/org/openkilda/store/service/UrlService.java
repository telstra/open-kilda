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

package org.openkilda.store.service;

import org.openkilda.store.common.constants.AuthUrl;
import org.openkilda.store.common.constants.StoreUrl;
import org.openkilda.store.common.constants.Url;
import org.openkilda.store.model.RequestParamDto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The Class UrlService.
 */

@Service
public class UrlService {
    
    /**
     * Gets the auth urls.
     *
     * @param type the type
     * @return the auth urls
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<String> getAuthUrls(final String type) {
        return AuthUrl.getUrlName(type);
    }
    
    /**
     * Gets the store urls.
     *
     * @param type the type
     * @return the store urls
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<String> getStoreUrls(final String type) {
        return StoreUrl.getUrlName(type);
    }
    
    /**
     * Gets the request url params.
     *
     * @param urlName the url name
     * @return the request url params
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<RequestParamDto> getRequestUrlParams(String urlName) {
        return Url.getRequestParamsByUrlName(urlName);
    }
    
}
