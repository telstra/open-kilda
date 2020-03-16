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

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Gets the urls.
 *
 * @return the urls
 */
@Getter
public enum StoreUrl {

    LINK_STORE(StoreType.LINK_STORE, new Url[] { Url.GET_LINK, Url.GET_STATUS_LIST, Url.GET_LINKS_WITH_PARAMS,
            Url.GET_CONTRACT, Url.DELETE_CONTRACT }),
    SWITCH_STORE(StoreType.SWITCH_STORE, new Url[] { Url.GET_ALL_SWITCHES, Url.GET_SWITCH,
            Url.GET_SWITCH_PORTS, Url.GET_SWITCH_PORT_FLOWS});

    private StoreType storeType;
    
    private Url[] urls;

    /**
     * Instantiates a new store url.
     *
     * @param storeType the store type
     * @param urls the urls
     */
    private StoreUrl(final StoreType storeType, final Url[] urls) {
        this.storeType = storeType;
        this.urls = urls;
    }
    
    
    /**
     * Gets the url name.
     *
     * @param storeType the store type
     * @return the url name
     */
    public static List<String> getUrlName(String storeType) {
        List<String> list = new ArrayList<String>();
        StoreUrl storeUrl = null;
        for (StoreUrl storeUrlObj : StoreUrl.values()) {
            if (storeUrlObj.getStoreType().getCode().equalsIgnoreCase(storeType)) {
                storeUrl = storeUrlObj;
                break;
            }
        }
        for (Url url : storeUrl.urls) {
            list.add(url.getName());
        }
        return list;
    }
}
