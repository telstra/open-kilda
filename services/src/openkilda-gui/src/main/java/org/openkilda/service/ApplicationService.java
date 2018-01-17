package org.openkilda.service;

import static java.util.Base64.getEncoder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.openkilda.utility.ApplicationProperties;

@Service
public class ApplicationService {

    @Autowired
    private ApplicationProperties applicationProperties;

    public String getAuthHeader() {
        String auth =
                applicationProperties.getKildaUsername() + ":"
                        + applicationProperties.getKildaPassword();

        return "Basic " + getEncoder().encodeToString(auth.getBytes());
    }}
