package jvm.daily.workflow

class WorkflowRunner {
    private val workflows = mutableMapOf<String, Workflow>()

    fun register(workflow: Workflow) {
        workflows[workflow.name] = workflow
    }

    suspend fun run(name: String) {
        val workflow = workflows[name]
            ?: throw IllegalArgumentException("Unknown workflow: $name. Available: ${workflows.keys}")
        workflow.execute()
    }

    suspend fun runAll() {
        for ((name, workflow) in workflows) {
            println("=== Running workflow: $name ===")
            workflow.execute()
        }
    }
}
