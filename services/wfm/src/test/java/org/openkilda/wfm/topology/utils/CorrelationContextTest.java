package org.openkilda.wfm.topology.utils;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.openkilda.wfm.topology.utils.CorrelationContext.CorrelationContextClosable;

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
}