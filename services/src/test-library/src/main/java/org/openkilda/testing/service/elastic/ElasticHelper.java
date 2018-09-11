package org.openkilda.testing.service.elastic;

import org.springframework.http.HttpEntity;

import java.util.HashMap;

public interface ElasticHelper {
    HashMap getLogs(String uri, HttpEntity query);
}
