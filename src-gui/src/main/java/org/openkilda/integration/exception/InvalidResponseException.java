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

package org.openkilda.integration.exception;

public class InvalidResponseException extends RuntimeException {

    private static final long serialVersionUID = 6981852888050194806L;

    private final int code;
    private final String response;

    public InvalidResponseException(final int code, final String response) {
        super();
        this.code = code;
        this.response = response;
    }

    public InvalidResponseException(final int code, final String response, final String errorMessage) {
        super(errorMessage);
        this.code = code;
        this.response = response;
    }

    public InvalidResponseException(final int code, final String response, final String errorMessage,
            final Throwable e) {
        super(errorMessage, e);
        this.code = code;
        this.response = response;
    }

    public int getCode() {
        return code;
    }

    public String getResponse() {
        return response;
    }
}
