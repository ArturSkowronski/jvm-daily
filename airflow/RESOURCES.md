# Airflow Resource Requirements

## Podman Machine Configuration

### Recommended (Tested & Working)

```bash
podman machine init --cpus 2 --memory 4096 --disk-size 20
```

**Why these values:**
- **2 CPUs** - Enough for Airflow scheduler + webserver + PostgreSQL
- **4GB RAM** - Comfortable for all 3 services (~1.5GB used)
- **20GB Disk** - Plenty for:
  - Podman OS image: ~5GB
  - Airflow images: ~3GB
  - PostgreSQL data: ~500MB
  - Logs: ~1GB
  - Buffer: ~10GB

### Minimum (Not Recommended)

```bash
podman machine init --cpus 1 --memory 2048 --disk-size 10
```

Works but slow. Use only for testing.

### Resource Usage (Real World)

After running Airflow for 1 hour:

| Service | CPU | Memory | Notes |
|---------|-----|--------|-------|
| airflow-webserver | ~5% | 400MB | Idle most of the time |
| airflow-scheduler | ~10% | 500MB | Active during DAG runs |
| postgres | ~2% | 50MB | Minimal load |
| **Total** | **~17%** | **~950MB** | Plenty of headroom |

## Docker Images Size

```
apache/airflow:2.8.1-python3.11    ~2.8GB
postgres:14                        ~400MB
Total                              ~3.2GB
```

## Disk Usage

```
/var/lib/containers/storage/       ~8GB   (Podman + images)
airflow/logs/                      ~50MB  (per week)
postgres volume                    ~500MB (metadata)
Total                              ~8.5GB
```

## Real Requirements vs What We Set

| Resource | Minimum | Recommended | Why Not More? |
|----------|---------|-------------|---------------|
| **CPUs** | 1 | 2 | Airflow is I/O bound, not CPU bound |
| **Memory** | 2GB | 4GB | Services use ~1GB total, 4GB gives headroom |
| **Disk** | 10GB | 20GB | Images + logs + data = ~10GB, 20GB is safe |

## Why Not 100GB Disk?

- **Overkill** - Airflow metadata + logs are small (~2GB)
- **Wasted space** - Podman will allocate it immediately
- **No benefit** - We're not storing large datasets in Podman
- **Our data is in DuckDB** - Which lives outside Podman (on host)

## Scaling Up

If you process 1000s of articles per day:

```bash
# For heavy workloads
podman machine init --cpus 4 --memory 8192 --disk-size 30
```

Use this if:
- Processing >500 articles/day
- Running multiple DAGs simultaneously
- Using CeleryExecutor with workers
- Storing large logs

## Monitoring Resources

### Check Podman Machine Stats

```bash
podman machine info
```

### Check Container Resources

```bash
# Real-time stats
podman stats

# Disk usage
podman system df
```

### Check Specific Container

```bash
podman stats airflow-airflow-webserver-1
podman stats airflow-airflow-scheduler-1
```

## Troubleshooting

### Out of Memory

**Symptom:** Containers crashing with "Killed"

**Solution:**
```bash
podman machine stop
podman machine rm -f podman-machine-default
podman machine init --cpus 2 --memory 6144 --disk-size 20
podman machine start
```

### Out of Disk Space

**Symptom:** "no space left on device"

**Solution:**
```bash
# Clean up unused images
podman system prune -a

# Or increase disk size
podman machine stop
podman machine rm -f podman-machine-default
podman machine init --cpus 2 --memory 4096 --disk-size 30
podman machine start
```

### Too Slow

**Symptom:** DAG runs taking forever

**Solution:**
```bash
# Bump CPUs
podman machine stop
podman machine rm -f podman-machine-default
podman machine init --cpus 4 --memory 4096 --disk-size 20
podman machine start
```

## Cost Comparison (Cloud)

If running on cloud VMs:

| Provider | Instance Type | vCPU | RAM | Monthly Cost |
|----------|---------------|------|-----|--------------|
| AWS | t3.medium | 2 | 4GB | ~$30 |
| GCP | e2-medium | 2 | 4GB | ~$25 |
| Azure | B2s | 2 | 4GB | ~$30 |

**For our workload:** t3.small (2 vCPU, 2GB) would work (~$15/month)

## Summary

✅ **Use this:**
```bash
podman machine init --cpus 2 --memory 4096 --disk-size 20
```

❌ **Don't use this:**
```bash
podman machine init --cpus 5 --memory 8192 --disk-size 100  # Wasteful
```

**Why:**
- Airflow is lightweight (~1GB RAM used)
- DuckDB database lives on host (not in Podman)
- Workflows are I/O bound (fetching RSS, calling LLM API)
- 20GB disk is plenty for images + logs
