# Kotlin 2.3 Preview: Context Parameters

JetBrains announced Kotlin 2.3 preview with the long-awaited context parameters feature.

Context parameters allow functions to implicitly receive context objects without explicit parameter passing:

```kotlin
context(logger: Logger)
fun processData(data: Data) {
    logger.info("Processing ${data.name}")
}
```

This replaces the experimental context receivers from earlier versions with a more refined design. The feature is expected to stabilize in Kotlin 2.4.
