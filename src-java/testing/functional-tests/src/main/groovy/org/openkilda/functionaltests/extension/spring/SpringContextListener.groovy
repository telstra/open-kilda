package org.openkilda.functionaltests.extension.spring

import org.springframework.context.ApplicationContext

interface SpringContextListener {
    void notifyContextInitialized(ApplicationContext applicationContext)
}