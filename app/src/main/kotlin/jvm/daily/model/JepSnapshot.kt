package jvm.daily.model

data class JepSnapshot(
    val jepNumber: Int,
    val title: String,
    val status: String,
    val targetRelease: String?,   // e.g. "JDK 26", null if unassigned
    val updatedDate: String?,     // "YYYY/MM/DD" from individual JEP page
    val summary: String?,         // first paragraph of JEP description
    val lastSeenAt: String,       // ISO-8601 timestamp
)
