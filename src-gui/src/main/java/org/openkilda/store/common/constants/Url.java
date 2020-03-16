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

import org.openkilda.store.model.RequestParamDto;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Gets the request params.
 *
 * @return the request params
 */
@Getter
public enum Url {

    OAUTH_GENERATE_TOKEN("oauth-generate-token", 
            new RequestParams[] { RequestParams.USER_NAME, RequestParams.PASSWORD }), 
            OAUTH_REFRESH_TOKEN("oauth-refresh-token", new RequestParams[] { RequestParams.ACCESS_TOKEN }), 
            GET_LINK("get-link", new RequestParams[] { RequestParams.LINK_ID }),
            GET_STATUS_LIST("get-status-list", new RequestParams[] {}),
            GET_LINKS_WITH_PARAMS("get-link-with-param", 
                    new RequestParams[] { RequestParams.STATUS }),
            GET_CONTRACT("get-contract", new RequestParams[] { RequestParams.LINK_ID }),
            DELETE_CONTRACT("delete-contract",
                    new RequestParams[] { RequestParams.LINK_ID, RequestParams.CONTRACT_ID }),
            GET_ALL_SWITCHES("get-all-switches", new RequestParams[] {}),
            GET_SWITCH("get-switch", new RequestParams[] { RequestParams.SWITCH_ID }),
            GET_SWITCH_PORTS("get-switch-ports",
                    new RequestParams[] { RequestParams.SWITCH_ID }),
            GET_SWITCH_PORT_FLOWS(
                    "get-switch-port-flows",
                    new RequestParams[] { RequestParams.SWITCH_ID, RequestParams.PORT_NUMBER});
    

    private String name;
    
    private RequestParams[] requestParams;

    /**
     * Instantiates a new url.
     *
     * @param name the name
     * @param requestParams the request params
     */
    private Url(final String name, final RequestParams[] requestParams) {
        this.name = name;
        this.requestParams = requestParams;
    }
    
    /**
     * Gets the url names.
     *
     * @return the url names
     */
    public static List<String> getUrlNames() {
        List<String> list = new ArrayList<String>();
        for (Url urlObj : Url.values()) {
            list.add(urlObj.getName());
        }
        return list;
    }
    
    /**
     * Gets the request params by url name.
     *
     * @param name the name
     * @return the request params by url name
     */
    public static List<RequestParamDto> getRequestParamsByUrlName(String name) {
        List<RequestParamDto> list = new ArrayList<RequestParamDto>();
        Url url = null;
        for (Url urlObj : Url.values()) {
            if (urlObj.getName().equalsIgnoreCase(name)) {
                url = urlObj;
                break;
            }
        }
        RequestParamDto requestParamDto = null;
        for (RequestParams requestParam : url.getRequestParams()) {
            requestParamDto = new RequestParamDto();
            requestParamDto.setParamName(requestParam.getName());
            requestParamDto.setParamDescription(requestParam.getDescription());
            list.add(requestParamDto);
        }
        return list;
    }
}
