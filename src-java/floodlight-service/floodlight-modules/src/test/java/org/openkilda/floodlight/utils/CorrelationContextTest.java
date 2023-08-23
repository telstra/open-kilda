package org.openkilda.floodlight.utils;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class CorrelationContextTest {

    @Test
    public void shouldRestoreCorrelationIdOnClose() {
        // given
        CorrelationContext.create(String.format("test-%s", UUID.randomUUID()));
        String before = CorrelationContext.getId();

        // when
        try (CorrelationContextClosable closable = CorrelationContext
                .create(String.format("test-%s", UUID.randomUUID()))) {

            //then
            MatcherAssert.assertThat(CorrelationContext.getId(), not(equalTo(before)));
        }

        MatcherAssert.assertThat(CorrelationContext.getId(), equalTo(before));
    }

    @Disabled("Fix applying aspects to test classes")
    @Test
    @NewCorrelationContextRequired
    public void shouldInitializeCorrelationId() {
        // when
        String correlationId = CorrelationContext.getId();

        //then
        Assertions.assertNotEquals(DEFAULT_CORRELATION_ID, correlationId);
    }
}
