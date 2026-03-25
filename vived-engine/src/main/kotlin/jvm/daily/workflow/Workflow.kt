package jvm.daily.workflow

interface Workflow {
    val name: String
    suspend fun execute()
}
