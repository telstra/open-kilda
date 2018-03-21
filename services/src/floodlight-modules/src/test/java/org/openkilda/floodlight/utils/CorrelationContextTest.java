package org.openkilda.floodlight.utils;

import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

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
    public void shouldAppendCorrelationIdToPrevious() {
        // given
        CorrelationContext.create(String.format("test-%s", UUID.randomUUID()));
        String before = CorrelationContext.getId();

        // when
        try (CorrelationContextClosable closable = CorrelationContext
                .append(String.format("test-%s", UUID.randomUUID()))) {
            //then
            assertThat(CorrelationContext.getId(), startsWith(before));
        }

        assertThat(CorrelationContext.getId(), equalTo(before));
    }
}