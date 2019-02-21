package org.openkilda.floodlight.utils;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import org.junit.Test;
import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;

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
            assertThat(CorrelationContext.getId(), not(equalTo(before)));
        }

        assertThat(CorrelationContext.getId(), equalTo(before));
    }

    @Test
    @NewCorrelationContextRequired
    public void shouldInitializeCorrelationId() {
        // when
        String correlationId = CorrelationContext.getId();

        //then
        assertNotEquals(DEFAULT_CORRELATION_ID, correlationId);
    }

}