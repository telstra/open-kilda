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

package org.openkilda.constants;

/**
 * The Class IAuthConstants.
 */
public abstract class IAuthConstants {

    private IAuthConstants() {}

    public interface Header {
        String AUTHORIZATION = "Authorization";
        String CORRELATION_ID = "correlation_id";
        String BASIC_AUTH = "BasicAuth";
        String EXTRA_AUTH = "EXTRA_AUTH";
    }

    public interface ContentType {
        String JSON = "application/json";
    }

    public interface Code {
        int SUCCESS_CODE = 20000;
        String SUCCESS_AUXILIARY = "Success";
        String ERROR_AUXILIARY = "Error";
        
        String RESPONSE_PARSING_FAIL_ERROR = "response.parsing.fail.error";
    }
}
