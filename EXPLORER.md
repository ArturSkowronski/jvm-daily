# DuckDB Explorer Guide

Interactive CLI tool to browse and query the JVM Daily database.

## Quick Start

```bash
./gradlew explore
```

You'll see an interactive prompt:
```
🔍 DuckDB Explorer — Interactive Database Browser
Database: jvm-daily.duckdb
Type 'help' for commands, 'exit' to quit

>
```

## Commands

### Display Information
- **`tables`** — List all tables in the database
- **`schema`** — Show articles table schema with column names and types
- **`count`** — Total number of articles
- **`sources`** — List all RSS feeds with article counts

### Analytics
- **`stats`** — Show article statistics (count, avg content length, max length)
- **`recent`** — Show 10 most recent articles with titles, sources, and timestamps

### Search & Query
- **`find <text>`** — Search articles by title or content (case-insensitive)
  ```
  > find spring boot
  🔎 Found 3 matching articles:
    1. [rss] Spring Boot 3.3 Released
       https://spring.io/blog/...
  ```

- **`sql <query>`** — Execute raw DuckDB SQL
  ```
  > sql SELECT source_type, COUNT(*) FROM articles GROUP BY source_type
  ```

### Other
- **`help`** — Show all available commands
- **`exit`** — Close explorer and exit

## Examples

### View all sources and their article counts
```
> sources
📡 RSS Sources:
  Source                                                       Count
  -----------------------------------------------------------------
  https://feed.gradle.org/blog.atom                            96
  https://quarkus.io/feed.xml                                  50
  ...
```

### Search for articles about Kotlin
```
> find kotlin
🔎 Found 7 matching articles:
  1. [rss] Kotlin 2.3 Preview: Context Parameters
     https://dev.to/...
  2. [rss] Kotlin Coroutines 1.8 Released
     ...
```

### Get database statistics
```
> stats
📊 Statistics:
  Source               Count      Avg Len         Max Len
  ------------------------------------------------------------
  markdown_file        2          472             507
  rss                  299        5872            50333
```

### View recent articles
```
> recent
🆕 Recent Articles:
  1. [rss] LazyConstants in JDK 26 - Inside Java Newscast #106
     inside.java @ 2026-02-10
  2. [rss] Spring Boot 4.0 RC1 Released
     spring.io @ 2026-02-10
```

## Advanced: Raw SQL Queries

You can also run custom SQL queries directly:

```
> sql SELECT COUNT(*) as total FROM articles WHERE source_type = 'rss'

📊 Query Results:
  total |
  -----
  299 |
```

### Useful SQL Queries

**Articles by author:**
```sql
sql SELECT author, COUNT(*) FROM articles WHERE author IS NOT NULL GROUP BY author ORDER BY COUNT(*) DESC LIMIT 5
```

**Articles with comments enabled:**
```sql
sql SELECT COUNT(*) FROM articles WHERE comments IS NOT NULL
```

**Content length distribution:**
```sql
sql SELECT
  CASE
    WHEN LENGTH(content) < 1000 THEN 'Short (<1K)'
    WHEN LENGTH(content) < 5000 THEN 'Medium (1-5K)'
    WHEN LENGTH(content) < 10000 THEN 'Long (5-10K)'
    ELSE 'Very Long (>10K)'
  END as size_category,
  COUNT(*) as count
FROM articles
GROUP BY size_category
ORDER BY count DESC
```

## Environment Variables

| Variable | Default | Example |
|----------|---------|---------|
| `DUCKDB_PATH` | `jvm-daily.duckdb` | `/data/jvm-daily.duckdb` |

```bash
DUCKDB_PATH=/path/to/backup.duckdb ./gradlew explore
```

## Tips

- Commands are **case-insensitive**
- Results are limited to 10 items for brevity
- Use `find` for quick text searches, `sql` for complex queries
- Press `Ctrl+C` to exit at any time
