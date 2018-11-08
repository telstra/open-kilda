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

package org.openkilda.testing.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.UnknownHttpStatusCodeException;

import java.io.IOException;

public class ExtendedErrorHandler extends DefaultResponseErrorHandler {

    private ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Overrides default errors and puts formatted body to the actual error message text. This helps to see
     * the error response body right in the test results output without digging into log files
     */
    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        try {
            super.handleError(response);
        } catch (HttpClientErrorException c) {
            throw new HttpClientErrorException(c.getStatusCode(), c.getStatusText() + "\n"
                    + prettyJson(c.getResponseBodyAsString()), c.getResponseHeaders(), c.getResponseBodyAsByteArray(),
                    this.getCharset(response));
        } catch (HttpServerErrorException s) {
            throw new HttpServerErrorException(s.getStatusCode(), s.getStatusText() + "\n"
                    + prettyJson(s.getResponseBodyAsString()), s.getResponseHeaders(), s.getResponseBodyAsByteArray(),
                    this.getCharset(response));
        } catch (UnknownHttpStatusCodeException u) {
            throw new UnknownHttpStatusCodeException(u.getRawStatusCode(), u.getStatusText() + "\n"
                    + prettyJson(u.getResponseBodyAsString()), u.getResponseHeaders(), u.getResponseBodyAsByteArray(),
                    this.getCharset(response));
        }
    }

    private String prettyJson(String str) {
        try {
            return mapper.writeValueAsString(mapper.readValue(str, Object.class));
        } catch (IOException t) {
            return str;
        }
    }
}
