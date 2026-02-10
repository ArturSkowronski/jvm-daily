# Findings

## 2026-02-09 — Initial Setup

- Starting with plugin engine architecture for source ingestion
- DuckDB + plain JDBC as storage layer (Exposed doesn't support DuckDB — no built-in dialect)
- Koog Agents for AI workflow orchestration

## 2026-02-09 — DuckDB Timestamp Gotcha

- DuckDB's `TIMESTAMP` column type converts ISO 8601 strings (e.g. `2026-02-09T10:00:00Z`) into its own format (`2026-02-09 10:00:00`), stripping the `T` and `Z`
- `kotlinx.datetime.Instant.parse()` then fails on read because the format doesn't match ISO 8601
- **Fix:** Use `VARCHAR` column for `ingested_at` to preserve the ISO 8601 string as-is
- Alternative: use `java.sql.Timestamp` for proper JDBC timestamp handling, but VARCHAR is simpler for now

## 2026-02-09 — DuckDB JDBC

- Maven coordinate: `org.duckdb:duckdb_jdbc:1.1.3`
- In-memory: `jdbc:duckdb:` — great for tests
- Persistent: `jdbc:duckdb:path/to/file.duckdb`
- `INSERT OR REPLACE` works for upserts (SQLite-style syntax supported)

## 2026-02-09 — RSS Source Implementation

- **Rome** (`com.rometools:rome:2.1.0`) is the best RSS/Atom parser for JVM — handles both formats, extracts title, author, description, content, comments
- **kaml** (`com.charleskorn.kaml:kaml:0.67.0`) works well with kotlinx.serialization for YAML config parsing
- Rome's `XmlReader(URL)` constructor is deprecated — use `XmlReader(InputStream)` instead via `URL.openStream()`
- RSS entries without `<link>` or `<title>` are skipped (mapNotNull) — defensive parsing
- Test RSS feeds with local file:// URIs works perfectly for unit tests without network calls

## 2026-02-10 — RSS Feed Verification (17 feeds from PLAN.md)

- **User-Agent required** — Baeldung (FeedBlitz) and dev.to reject requests without User-Agent header. Set `User-Agent: JVM-Daily/1.0` on all feed requests.
- **Feed URL corrections from PLAN.md:**
  - Baeldung: `baeldung.com/feed` → `feeds.feedblitz.com/baeldung` (hosted on FeedBlitz)
  - Gradle Blog: `blog.gradle.org/feed.xml` → `feed.gradle.org/blog.atom` (different subdomain)
  - Marco Behler: no RSS on marcobehler.com → `dev.to/feed/marcobehler` (dev.to profile)
- **All 17 feeds verified working:**
  - Inside Java, Spring Blog, Kotlin Blog, Baeldung, InfoQ Java, Quarkus Blog, Micronaut Blog, foojay.io, Gradle Blog, JetBrains Blog, Vlad Mihalcea, Thorben Janssen, Marco Behler (dev.to), Adam Bien, DZone Java, Hacker News (JVM-filtered via hnrss.org), GraalVM Blog (Medium)
- **Hacker News** — no native JVM feed, but `hnrss.org` provides keyword-filtered feeds: `hnrss.org/newest?q=java+OR+kotlin+OR+jvm+OR+spring+OR+graalvm`
- **GraalVM Blog** — hosted on Medium, feed at `medium.com/feed/graalvm`
