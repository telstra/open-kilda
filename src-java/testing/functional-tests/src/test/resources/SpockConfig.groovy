import org.spockframework.runtime.model.parallel.ExecutionMode

runner {
    parallel {
        enabled true
        fixed(System.getProperty("parallel.topologies", "1").toInteger())
        defaultSpecificationExecutionMode ExecutionMode.CONCURRENT
        defaultExecutionMode ExecutionMode.SAME_THREAD
    }
}