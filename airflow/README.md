# Airflow Integration for JVM Daily

Apache Airflow orchestration for the JVM Daily processing pipeline.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       Airflow DAG                            │
│                   (jvm_daily_pipeline)                       │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  Task 1: Ingress (BashOperator)        │
        │  ./gradlew run --args="ingress"        │
        │  → Collect articles from RSS feeds     │
        └────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  Task 2: Check New Articles            │
        │  (BranchPythonOperator)                │
        │  → Query DuckDB for unprocessed        │
        └────────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                    ▼                 ▼
         ┌──────────────────┐  ┌─────────────┐
         │ Skip Processing  │  │  Process    │
         │                  │  │  Articles   │
         └──────────────────┘  └─────────────┘
                                      │
                  ┌───────────────────┼───────────────────┐
                  ▼                   ▼                   ▼
        ┌─────────────────┐  ┌────────────────┐  ┌─────────────┐
        │  Enrichment     │  │   Clustering   │  │ Compilation │
        │  (LLM process)  │  │   (Group)      │  │  (TODO)     │
        └─────────────────┘  └────────────────┘  └─────────────┘
                                      │
                                      ▼
                          ┌────────────────────┐
                          │ Generate Stats     │
                          └────────────────────┘
```

## Features

- **Daily scheduled execution** (7am UTC)
- **Conditional branching** (skip if no new articles)
- **Task groups** for logical organization
- **Retry logic** (2 retries, 5min delay)
- **Timeout protection** (30min enrichment, 20min clustering)
- **Environment-based configuration** (DB path, LLM credentials)

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 21+ (for Gradle tasks)
- DuckDB CLI (for health checks)

### 1. Setup

```bash
cd airflow

# Copy environment template
cp .env.example .env

# Edit .env with your settings
nano .env
```

### 2. Start Airflow

```bash
# Initialize Airflow database and create admin user
docker-compose up airflow-init

# Start all services
docker-compose up -d

# Check status
docker-compose ps
```

### 3. Access Airflow UI

Open http://localhost:8080

- **Username:** `airflow` (or value from `.env`)
- **Password:** `airflow` (or value from `.env`)

### 4. Configure LLM Variables

In Airflow UI, go to **Admin → Variables** and add:

| Key | Value | Example |
|-----|-------|---------|
| `llm_provider` | LLM provider name | `openai`, `anthropic`, `koog`, `mock` |
| `llm_api_key` | Your API key | `sk-...` |
| `llm_model` | Model to use | `gpt-4`, `claude-3-opus-20240229` |

### 5. Trigger DAG

**Option 1: Manual trigger**
- In UI, toggle the DAG to "ON"
- Click the play button to run manually

**Option 2: Wait for schedule**
- DAG runs automatically daily at 7am UTC

## DAG Configuration

### Schedule

```python
schedule=PIPELINE_CRON  # defaults to 0 7 * * *
```

To change:
- Edit `airflow/dags/jvm_daily_pipeline.py`
- Use cron expression or timedelta
- Or set `PIPELINE_CRON` in scheduler environment (recommended to keep parity with JobRunr daemon mode)

### Scheduler Contract (Phase 6)

- Canonical default cron: `0 7 * * *` (daily 07:00 UTC)
- JobRunr daemon and Airflow DAG both use `PIPELINE_CRON` with the same default.
- Daily contract means running the same stage sequence: `ingress -> enrichment -> clustering -> outgress`.

### Task Timeouts

| Task | Timeout | Configurable |
|------|---------|--------------|
| Ingress | None | No timeout |
| Enrichment | 30 minutes | `execution_timeout` |
| Clustering | 20 minutes | `execution_timeout` |

### Retry Logic

```python
'retries': 2,
'retry_delay': timedelta(minutes=5),
```

## Workflows

### Ingress Workflow

**Command:** `./gradlew run --args="ingress"`

**What it does:**
- Reads `config/sources.yml`
- Fetches articles from 17 RSS feeds
- Saves to DuckDB `articles` table
- Deduplicates automatically

**Environment variables:**
- `DUCKDB_PATH` - Database file path (default: `jvm-daily.duckdb`)
- `CONFIG_PATH` - RSS config path (default: `config/sources.yml`)
- `SOURCES_DIR` - Markdown sources (default: `sources`)

### Enrichment Workflow

**Command:** `./gradlew run --args="enrichment"`

**What it does:**
- Finds unprocessed articles in DuckDB
- Calls LLM for each article:
  - Generates summary (100-150 words)
  - Extracts entities (JDK versions, frameworks, etc.)
  - Assigns topic tags
- Saves to `processed_articles` table

**Environment variables:**
- `DUCKDB_PATH` - Database file path
- `LLM_PROVIDER` - Provider (`openai`, `anthropic`, `koog`, `mock`)
- `LLM_API_KEY` - API key for provider
- `LLM_MODEL` - Model name

### Clustering Workflow

**Command:** `./gradlew run --args="clustering"`

**What it does:**
- Loads processed articles from last 24h
- Groups by shared topics/entities
- Creates max 8 thematic clusters
- Generates cluster titles and synthesis via LLM

**Environment variables:**
- `DUCKDB_PATH` - Database file path
- `LLM_PROVIDER` - Provider
- `LLM_API_KEY` - API key
- `LLM_MODEL` - Model name

## Monitoring

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f airflow-scheduler
docker-compose logs -f airflow-webserver
```

### Scheduler + Telemetry Smoke Check (Phase 6)

```bash
# 1) Trigger DAG manually in UI or CLI
docker-compose exec airflow-scheduler airflow dags trigger jvm_daily_pipeline

# 2) Validate stage task execution order
docker-compose exec airflow-scheduler airflow tasks list jvm_daily_pipeline --tree

# 3) Check JVM app logs for structured telemetry lines
docker-compose logs airflow-scheduler | grep "[pipeline][telemetry]"
```

### Task Logs

In Airflow UI:
1. Click on DAG run
2. Click on task
3. View logs in the popup

### Database Status

```bash
# Count articles
duckdb ../jvm-daily.duckdb "SELECT COUNT(*) FROM articles"

# Count processed
duckdb ../jvm-daily.duckdb "SELECT COUNT(*) FROM processed_articles"

# Unprocessed count
duckdb ../jvm-daily.duckdb "
SELECT COUNT(*) FROM articles a
WHERE NOT EXISTS (SELECT 1 FROM processed_articles p WHERE p.id = a.id)
"
```

## Troubleshooting

### DAG not showing up

1. Check DAG file syntax:
   ```bash
   docker-compose exec airflow-scheduler python /opt/airflow/dags/jvm_daily_pipeline.py
   ```

2. Check scheduler logs:
   ```bash
   docker-compose logs airflow-scheduler | grep ERROR
   ```

### Gradle tasks failing

1. Verify project mount:
   ```bash
   docker-compose exec airflow-webserver ls /jvm-daily
   ```

2. Test Gradle locally:
   ```bash
   cd ..
   ./gradlew run --args="ingress"
   ```

### LLM tasks failing

1. Check variables are set in Airflow UI
2. Verify API key is valid
3. Check task logs for detailed error

### Database locked

DuckDB uses file-level locking. Ensure:
- Only one process writes at a time
- Tasks are sequential (enrichment → clustering)
- No manual queries during DAG run

## Advanced Configuration

### Custom LLM Provider

To add a new LLM provider:

1. Edit `app/src/main/kotlin/jvm/daily/App.kt`:
   ```kotlin
   private fun createLLMClient(provider: String, apiKey: String?, model: String): LLMClient {
       return when (provider) {
           "mock" -> MockLLMClient()
           "openai" -> OpenAIClient(apiKey!!, model)  // Add this
           // ...
       }
   }
   ```

2. Implement the client (see `LLMClient` interface)

3. Update Airflow variable `llm_provider`

### Parallel Execution

To process multiple articles in parallel:

1. Change Airflow executor to `CeleryExecutor` or `KubernetesExecutor`
2. Modify enrichment workflow to batch process
3. Ensure DuckDB can handle concurrent writes (use WAL mode)

### Email Notifications

Add to `default_args` in DAG:

```python
'email': ['your-email@example.com'],
'email_on_failure': True,
'email_on_retry': True,
```

Configure SMTP in `airflow/config/airflow.cfg` or via environment variables.

## Production Checklist

- [ ] Set strong admin password in `.env`
- [ ] Configure real LLM provider (not `mock`)
- [ ] Set up email notifications
- [ ] Configure secrets management (Airflow Connections/Variables encryption)
- [ ] Set up monitoring/alerting (Prometheus, Datadog, etc.)
- [ ] Configure log retention policy
- [ ] Set up database backups (DuckDB file)
- [ ] Use external PostgreSQL (not Docker volume)
- [ ] Configure resource limits (CPU, memory)
- [ ] Set up SSL/TLS for Airflow webserver

## Development

### Testing DAG locally

```bash
# Validate DAG syntax
docker-compose exec airflow-scheduler python /opt/airflow/dags/jvm_daily_pipeline.py

# Test specific task
docker-compose exec airflow-scheduler airflow tasks test jvm_daily_pipeline ingress 2026-02-10
```

### Debugging

Add breakpoints or print statements in `jvm_daily_pipeline.py`, then:

```bash
docker-compose restart airflow-scheduler
docker-compose logs -f airflow-scheduler
```

## References

- [Apache Airflow Docs](https://airflow.apache.org/docs/)
- [Airflow Best Practices](https://airflow.apache.org/docs/apache-airflow/stable/best-practices.html)
- [DuckDB Concurrency](https://duckdb.org/docs/connect/concurrency)
- [Latent Space AI News](https://news.smol.ai) (inspiration)
