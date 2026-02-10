# JVM Daily - Quick Start Guide

## Run Workflows Locally (No Airflow Required)

### Prerequisites
- Java 21+
- Gradle (wrapper included)
- DuckDB CLI (optional, for querying)

### 1. Run Complete Pipeline

```bash
# Set environment (optional)
export DUCKDB_PATH="jvm-daily.duckdb"
export LLM_PROVIDER="mock"  # or openai, anthropic

# Step 1: Collect articles from RSS feeds
./gradlew run --args="ingress"

# Step 2: Enrich with LLM (summaries, entities, topics)
./gradlew run --args="enrichment"

# Step 3: Group into thematic clusters
./gradlew run --args="clustering"
```

### 2. Check Results

```bash
# Count articles
duckdb jvm-daily.duckdb "SELECT COUNT(*) FROM articles"

# Count processed articles
duckdb jvm-daily.duckdb "SELECT COUNT(*) FROM processed_articles"

# View recent articles
duckdb jvm-daily.duckdb "SELECT title, source_type FROM articles LIMIT 5"

# Or use the interactive explorer
./gradlew explore
```

### 3. Explore Database

```bash
./gradlew explore

# Then type commands:
> count          # Total articles
> sources        # RSS feeds with counts
> recent         # Latest articles
> find kotlin    # Search articles
> help           # All commands
```

## Run with Airflow (Orchestration)

### Prerequisites
- Docker or Podman
- Docker Compose

### 1. Setup Airflow

```bash
cd airflow

# Copy environment template
cp .env.example .env

# Edit if needed (optional)
nano .env
```

### 2. Start Airflow

```bash
# Initialize database and create admin user
docker-compose up airflow-init

# Start all services (webserver + scheduler + postgres)
docker-compose up -d

# Check status
docker-compose ps
```

Expected output:
```
NAME                    STATUS
airflow-scheduler       Up
airflow-webserver       Up (healthy)
postgres                Up (healthy)
```

### 3. Access Airflow UI

Open http://localhost:8080

**Login:**
- Username: `airflow`
- Password: `airflow`

### 4. Configure LLM Provider

In Airflow UI:
1. Go to **Admin → Variables**
2. Click **"+"** to add new variable
3. Add three variables:

| Key | Value | Example |
|-----|-------|---------|
| `llm_provider` | Provider name | `mock` (or `openai`, `anthropic`) |
| `llm_api_key` | Your API key | `sk-...` (leave empty for mock) |
| `llm_model` | Model to use | `gpt-4` |

### 5. Enable and Run DAG

1. Find `jvm_daily_pipeline` DAG in the list
2. Toggle the switch to **ON** (blue)
3. Click the **▶️ Play** button to trigger manually

**Or wait for schedule:** DAG runs automatically daily at 7am UTC

### 6. Monitor Execution

Click on the DAG name → Graph view to see:
- ✅ Green = Success
- 🟡 Yellow = Running
- 🔴 Red = Failed

Click any task to view logs.

### 7. Stop Airflow

```bash
cd airflow
docker-compose down

# To remove all data (optional)
docker-compose down -v
```

## Troubleshooting

### Docker/Podman not running

**Error:** `Cannot connect to the Docker daemon`

**Solution:**
```bash
# For Docker
open -a Docker

# For Podman
podman machine start
```

### Gradle command not found

**Solution:**
```bash
# Use wrapper
./gradlew run --args="ingress"

# Not
gradlew run --args="ingress"
```

### Database locked

**Error:** `database is locked`

**Solution:** Close any open DuckDB connections:
```bash
# Kill DuckDB processes
pkill -9 duckdb

# Or use different database
export DUCKDB_PATH="jvm-daily-test.duckdb"
```

### Airflow DAG not showing

**Check DAG file syntax:**
```bash
cd airflow
docker-compose exec airflow-scheduler python /opt/airflow/dags/jvm_daily_pipeline.py
```

**Check scheduler logs:**
```bash
docker-compose logs airflow-scheduler | grep ERROR
```

### LLM API errors

**Using mock provider (no API key needed):**
```bash
export LLM_PROVIDER="mock"
./gradlew run --args="enrichment"
```

**Using real provider:**
```bash
export LLM_PROVIDER="openai"
export LLM_API_KEY="sk-..."
export LLM_MODEL="gpt-4"
./gradlew run --args="enrichment"
```

## Example Full Run

```bash
# Clean start
rm -f jvm-daily.duckdb*

# Run pipeline
./gradlew run --args="ingress"
# Output: Total articles in DB: 302

./gradlew run --args="enrichment"
# Output: Total processed articles: 302

./gradlew run --args="clustering"
# Output: Created 1 thematic clusters

# Check results
./gradlew explore
> count
📈 Total Articles: 302

> sources
📡 RSS Sources:
  Source                                                       Count
  -----------------------------------------------------------------
  https://feed.gradle.org/blog.atom                            96
  https://quarkus.io/feed.xml                                  50
  ...
```

## What's Happening

1. **Ingress** (`./gradlew run --args="ingress"`)
   - Reads `config/sources.yml`
   - Fetches from 17 RSS feeds
   - Saves to `articles` table
   - Deduplicates automatically
   - ~302 articles collected

2. **Enrichment** (`./gradlew run --args="enrichment"`)
   - Reads unprocessed articles
   - Calls LLM for each:
     - Summary (100-150 words)
     - Entities (JDK versions, frameworks, etc.)
     - Topics (framework-releases, performance, etc.)
   - Saves to `processed_articles` table
   - Takes ~5-30min depending on LLM and article count

3. **Clustering** (`./gradlew run --args="clustering"`)
   - Groups articles by shared topics/entities
   - Creates thematic clusters (max 8/day)
   - LLM generates cluster titles and synthesis
   - Currently logs only (persistence TODO)

## Next Steps

- Add real LLM integration (OpenAI, Anthropic, Koog Agents)
- Implement cluster persistence
- Build compilation workflow (generate newsletter)
- Set up monitoring/alerting
- Deploy to production

## Getting Help

- **Documentation:** See `airflow/README.md` for detailed Airflow guide
- **Architecture:** See `PLAN.md` for full system design
- **Issues:** https://github.com/ArturSkowronski/jvm-daily/issues
