package org.openkilda.functionaltests


abstract class HealthCheckBaseSpecification extends BaseSpecification {

    def setupSpec() {
        if (healthCheckRan && !healthCheckError) {
            return
        }
        if (healthCheckRan && healthCheckError) {
            throw healthCheckError
        }
        try {
            healthCheck()
        } catch (Throwable t) {
            healthCheckError = t
            throw t
        } finally {
            healthCheckRan = true
        }
    }

    abstract boolean getHealthCheckRan()
    abstract Throwable getHealthCheckError()
    abstract void setHealthCheckRan(boolean healthCheckRan)
    abstract void setHealthCheckError(Throwable t)

    abstract def healthCheck()
}
