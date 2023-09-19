package org.openkilda.functionaltests.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan(basePackages = ["org.openkilda.functionaltests.helpers", "org.openkilda.functionaltests.model.stats"])
class HelpersConfig {
}
