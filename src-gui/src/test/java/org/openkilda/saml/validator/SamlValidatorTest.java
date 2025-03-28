/* Copyright 2025 Telstra Open Source
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

package org.openkilda.saml.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.openkilda.saml.dao.entity.SamlConfigEntity;
import org.openkilda.saml.repository.SamlRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.util.MessageUtils;

import java.util.ArrayList;

class SamlValidatorTest {

    @Mock
    private MessageUtils messageUtil;

    @Mock
    private SamlRepository samlRepository;

    @InjectMocks
    private SamlValidator samlConfigService;

    @Mock
    private MultipartFile file;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(samlRepository.findByEntityIdOrNameEqualsIgnoreCase(any(), eq("new"))).thenReturn(null);
        when(samlRepository.findByUuid(any())).thenReturn(new SamlConfigEntity());
        when(messageUtil.getAttributeInvalid(any(), any())).thenReturn("URL is not valid");
    }

    // Test Case 1: Valid URL for create provider (http/https)
    @Test
    public void validateCreateProviderValidHttpUrl() {
        String httpUrl = "http://example.com";

        // Call the method under test
        Exception exception = assertThrows(RequestValidationException.class, () -> {
            samlConfigService.validateCreateProvider(null, "new", "entityId",
                    httpUrl, false, new ArrayList<>());
        });

        String unexpectedMessage = "URL is not valid";
        String actualMessage = exception.getMessage();
        assertNotEquals(unexpectedMessage, actualMessage);

        // Call the method under test
        String httpsUrl = "https://example.com";
        exception = assertThrows(RequestValidationException.class, () -> {
            samlConfigService.validateCreateProvider(null, "new", "entityId",
                    httpsUrl, false, new ArrayList<>());
        });

        actualMessage = exception.getMessage();
        assertNotEquals(unexpectedMessage, actualMessage);
    }

    // Test Case 2: Invalid URL with unsupported protocol for create provider, URL with internal/private IP
    @Test
    public void validateCreateProviderInvalidProtocolUrl() {
        String invalidUrl = "ftp://example.com";

        RequestValidationException thrown = assertThrows(RequestValidationException.class, () ->
                samlConfigService.validateCreateProvider(file, "name", "entityId",
                        invalidUrl, false, new ArrayList<>()));

        assertEquals("URL is not valid", thrown.getMessage());

        // URL with internal/private IP
        String internalUrl = "http://127.0.0.1";
        thrown = assertThrows(RequestValidationException.class, () ->
                samlConfigService.validateCreateProvider(file, "name", "entityId",
                        internalUrl, false, new ArrayList<>()));

        assertEquals("URL is not valid", thrown.getMessage());

        String malformedUrl = "htp://example.com";

        thrown = assertThrows(RequestValidationException.class, () ->
                samlConfigService.validateCreateProvider(file, "name", "entityId",
                        malformedUrl, false, new ArrayList<>()));

        assertEquals("URL is not valid", thrown.getMessage());
    }

    // Test Case 3: Valid URL for update provider (http/https)
    @Test
    public void validateUpdateProviderValidHttpUrl() {
        String httpUrl = "http://example.com";

        // Call the method under test
        Exception exception = assertThrows(RequestValidationException.class, () -> {
            samlConfigService.validateUpdateProvider(null, null, "name",
                    "entityId", httpUrl, false, new ArrayList<>());
        });

        String unexpectedMessage = "URL is not valid";
        String actualMessage = exception.getMessage();
        assertNotEquals(unexpectedMessage, actualMessage);

        // Call the method under test
        String httpsUrl = "https://example.com";
        exception = assertThrows(RequestValidationException.class, () -> {
            samlConfigService.validateUpdateProvider(null, null, "name",
                    "entityId", httpsUrl, false, new ArrayList<>());
        });

        actualMessage = exception.getMessage();
        assertNotEquals(unexpectedMessage, actualMessage);
    }

    // Test Case 4: Invalid URL with unsupported protocol for update provider, URL with internal/private IP
    @Test
    public void validateUpdateProviderInvalidProtocolUrl() {
        String invalidUrl = "ftp://example.com";

        RequestValidationException thrown = assertThrows(RequestValidationException.class, () ->
                samlConfigService.validateUpdateProvider(null, null, "name",
                        "entityId", invalidUrl, false, new ArrayList<>()));

        assertEquals("URL is not valid", thrown.getMessage());

        // URL with internal/private IP
        String internalUrl = "http://127.0.0.1";
        thrown = assertThrows(RequestValidationException.class, () ->
                samlConfigService.validateUpdateProvider(null, null, "name",
                        "entityId", internalUrl, false, new ArrayList<>()));

        assertEquals("URL is not valid", thrown.getMessage());

        String malformedUrl = "htp://example.com";

        thrown = assertThrows(RequestValidationException.class, () ->
                samlConfigService.validateUpdateProvider(null, null, "name",
                        "entityId", malformedUrl, false, new ArrayList<>()));

        assertEquals("URL is not valid", thrown.getMessage());
    }

}
