package org.openkilda.functionaltests.healthcheck


interface HealthCheck {
    List<HealthCheckException> getPotentialProblems()
    //Use default for checks which don't have the way to be fixed automatically
    default  List<HealthCheckException> attemptToFix(List<HealthCheckException> problems) {
        return problems
    }
}