/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.snmp.service;

import org.openkilda.wfm.topology.snmp.model.SwitchDto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * This class will update registered parties with topology change information. At this moment, only SNMP collector is
 * considered. Also we just use HTTP method for update, it is not strictly REST API.
 */
public class TopologyUpdateService {

    private static Logger LOG = LoggerFactory.getLogger(TopologyUpdateService.class);

    private final String endpoint;
    private final CloseableHttpClient httpClient;


    public TopologyUpdateService(String endpoint, String username, String password) {
        // the initial version only support http and basic authentication.
        this.endpoint = endpoint;
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
        credentialsProvider.setCredentials(AuthScope.ANY, credentials);

        this.httpClient = HttpClients.custom().setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultHeaders(Arrays.asList(
                        new BasicHeader(HttpHeaders.CONTENT_TYPE, "Application/json"),
                        new BasicHeader(HttpHeaders.AUTHORIZATION,
                                        String.format("Basic %s", Base64.encodeBase64String(
                                                String.format("%s:%s", username, password).getBytes())))))
                .build();
    }

    /**
     * Update registered parties with real time change of the topology, e.g., switch added/removed.
     * @param action Add/Remove switches
     * @param dtos  the collection of switches to add/remove, may be null for clear action
     */
    public void publishTopologyUpdate(UpdateAction action, Collection<SwitchDto> dtos) {

        try {
            HttpPost post = new HttpPost(String.format("%s/%s", endpoint, action.toString()));
            if (action != action.CLEAR) {
                ObjectMapper mapper = new ObjectMapper();
                HttpEntity entity = EntityBuilder.create().setText(mapper.writeValueAsString(dtos)).build();
                post.setEntity(entity);
            }

            sendRequest(post);

        } catch (Exception e) {
            LOG.error("publish topology update encountered an exception", e);
        }
    }

    @VisibleForTesting
    void sendRequest(HttpUriRequest request) {
        try {

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                StatusLine statusLine = response.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                EntityUtils.consume(response.getEntity());

                if (statusCode == HttpStatus.SC_OK) {
                    LOG.info("Sending topology update request succeeded");
                } else {
                    LOG.warn("Sending topology update request failed with status code={}: {}",
                            statusCode, statusLine.getReasonPhrase());
                }
            }
        } catch (IOException e) {
            LOG.error("Sending topology update request encountered a problem", e);
        }
    }
}
