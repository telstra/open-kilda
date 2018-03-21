package org.openkilda.northbound.utils;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.openkilda.messaging.Utils.MISSING_CORRELATION_ID;

import org.junit.Test;
import org.openkilda.northbound.utils.RequestCorrelation.RequestCorrelationClosable;

import java.util.UUID;

public class RequestCorrelationTest {

    @Test
    public void shouldResetCorrelationIdOnClose() {
        // given
        RequestCorrelation.create(String.format("test-%s", UUID.randomUUID()));
        String before = RequestCorrelation.getId();

        // when
        try (RequestCorrelationClosable closable = RequestCorrelation
                .create(String.format("test-%s", UUID.randomUUID()))) {

            //then
            assertThat(RequestCorrelation.getId(), not(equalTo(before)));
        }

        assertThat(RequestCorrelation.getId(), equalTo(MISSING_CORRELATION_ID));
    }
}