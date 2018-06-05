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

package org.openkilda.atdd.staging.tools;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class LoggingRequestInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger log = LoggerFactory.getLogger(LoggingRequestInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        traceRequest(request, body);
        ClientHttpResponse response = execution.execute(request, body);
        traceResponse(response);
        return response;
    }

    private void traceRequest(HttpRequest request, byte[] body) throws IOException {
        log.debug(format("\n%1$s request begin %1$s\n%2$s%1$s request end %1$s",
                Strings.repeat("=", 20), genereateCurl(request, body)));
    }

    private void traceResponse(ClientHttpResponse response) throws IOException {
        StringBuilder inputStringBuilder = new StringBuilder();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody(), "UTF-8"));
        String line = bufferedReader.readLine();
        while (line != null) {
            inputStringBuilder.append(line);
            inputStringBuilder.append('\n');
            line = bufferedReader.readLine();
        }
        log.debug(format("\n%s response begin %1$s\n"
                        + "Status code  : %s\nStatus text  : %s\nHeaders      : %s\nResponse body: %s\n"
                        + "%1$s response end %1$s", Strings.repeat("=", 20),
                response.getStatusCode(), response.getStatusText(), response.getHeaders(),
                toJson(inputStringBuilder.toString())));
    }

    private String toJson(String str) {
        try {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            Object o = mapper.readValue(str, Object.class);
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return str;
        }
    }

    private String genereateCurl(HttpRequest request, byte[] payload) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(format("curl -X %s \\\n", request.getMethodValue()));
        sb.append(request.getURI().toString()).append(" \\\n");
        request.getHeaders().forEach((k, v) -> sb.append(format("-H '%s: %s' \\\n", k,
                StringUtils.substringBetween(v.toString(), "[", "]"))));
        String body = new String(payload, "UTF-8");
        if (!StringUtils.isEmpty(body)) {
            sb.append(format("-d '%s' \n", body));
        }
        return sb.toString();
    }
}
