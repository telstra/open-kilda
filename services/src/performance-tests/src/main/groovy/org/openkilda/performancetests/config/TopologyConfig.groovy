package org.openkilda.performancetests.config

import org.openkilda.performancetests.model.CustomTopology

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

@Configuration
class TopologyConfig {

    @Bean
    @Scope("prototype")
    CustomTopology topologyDefinitionNew() throws IOException {
        return new CustomTopology();
    }
}
