import org.spockframework.runtime.model.parallel.ExecutionMode

runner {
    parallel {
        enabled true
        defaultSpecificationExecutionMode = ExecutionMode.SAME_THREAD
        defaultExecutionMode = ExecutionMode.SAME_THREAD
    }
}