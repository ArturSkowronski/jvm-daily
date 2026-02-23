"""
JVM Daily Processing Pipeline DAG

Orchestrates the multi-stage processing pipeline:
1. Ingress: Collect articles from RSS feeds
2. Enrichment: LLM-based article processing (summaries, entities, topics)
3. Clustering: Group articles into thematic clusters
4. Outgress: Write processed articles to markdown file

Inspired by Latent Space AI News architecture.
"""

from datetime import datetime, timedelta
from pathlib import Path

from airflow import DAG
from airflow.operators.bash import BashOperator
from airflow.operators.python import PythonOperator, BranchPythonOperator
from airflow.utils.task_group import TaskGroup

# Default args for all tasks
default_args = {
    'owner': 'jvm-daily',
    'depends_on_past': False,
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 2,
    'retry_delay': timedelta(minutes=5),
}

# Project root - mounted at /jvm-daily in container (see docker-compose.yml)
PROJECT_ROOT = Path("/jvm-daily")
GRADLE_WRAPPER = PROJECT_ROOT / "gradlew"
DB_PATH = PROJECT_ROOT / "jvm-daily.duckdb"

# Java environment (required for Gradle)
JAVA_HOME = "/opt/java"
JAVA_ENV = {
    'JAVA_HOME': JAVA_HOME,
    'PATH': f"{JAVA_HOME}/bin:/usr/local/bin:/usr/bin:/bin",
}

def check_new_articles(**context):
    """
    Check if there are new articles to process.
    Returns task_id to branch to: 'process_articles' or 'skip_processing'
    """
    import subprocess
    import json

    # Query DuckDB to check for unprocessed articles
    query = """
    SELECT COUNT(*) as unprocessed FROM articles a
    WHERE NOT EXISTS (
        SELECT 1 FROM processed_articles p WHERE p.id = a.id
    )
    """

    result = subprocess.run(
        ["duckdb", str(DB_PATH), "-json", "-c", query],
        capture_output=True,
        text=True,
        check=True
    )

    data = json.loads(result.stdout)
    unprocessed_count = data[0]['unprocessed'] if data else 0

    print(f"Found {unprocessed_count} unprocessed articles")

    if unprocessed_count > 0:
        return 'process_articles.enrichment'
    else:
        return 'skip_processing'


with DAG(
    'jvm_daily_pipeline',
    default_args=default_args,
    description='JVM Daily processing pipeline - ingress, enrichment, clustering, outgress',
    schedule='0 7 * * *',  # Daily at 7am UTC
    start_date=datetime(2026, 2, 10),
    catchup=False,
    tags=['jvm-daily', 'processing', 'newsletter'],
) as dag:

    # Task 1: Ingress - Collect articles from RSS feeds
    ingress = BashOperator(
        task_id='ingress',
        bash_command=f'cd {PROJECT_ROOT} && {GRADLE_WRAPPER} run --args="ingress"',
        env={
            **JAVA_ENV,
            'DUCKDB_PATH': str(DB_PATH),
            'CONFIG_PATH': 'config/sources.yml',
        },
    )

    # Task 2: Check if there are new articles to process
    check_articles = BranchPythonOperator(
        task_id='check_new_articles',
        python_callable=check_new_articles,
        provide_context=True,
    )

    # Task 3: Skip processing if no new articles
    skip_processing = BashOperator(
        task_id='skip_processing',
        bash_command='echo "No new articles to process, skipping enrichment and clustering"',
    )

    # Task Group: Process articles (enrichment + clustering)
    with TaskGroup('process_articles') as process_articles:

        # Task 3a: Enrichment - LLM processing
        enrichment = BashOperator(
            task_id='enrichment',
            bash_command=f'cd {PROJECT_ROOT} && {GRADLE_WRAPPER} run --args="enrichment"',
            env={
                **JAVA_ENV,
                'DUCKDB_PATH': str(DB_PATH),
                'LLM_PROVIDER': '{{ var.value.llm_provider }}',
                'LLM_API_KEY': '{{ var.value.llm_api_key }}',
                'LLM_MODEL': '{{ var.value.llm_model }}',
            },
            execution_timeout=timedelta(minutes=30),
        )

        # Task 3b: Clustering - Group articles by theme
        clustering = BashOperator(
            task_id='clustering',
            bash_command=f'cd {PROJECT_ROOT} && {GRADLE_WRAPPER} run --args="clustering"',
            env={
                **JAVA_ENV,
                'DUCKDB_PATH': str(DB_PATH),
                'LLM_PROVIDER': '{{ var.value.llm_provider }}',
                'LLM_API_KEY': '{{ var.value.llm_api_key }}',
                'LLM_MODEL': '{{ var.value.llm_model }}',
            },
            execution_timeout=timedelta(minutes=20),
        )

        # Task 3c: Outgress - Write processed articles to markdown file
        outgress = BashOperator(
            task_id='outgress',
            bash_command=f'cd {PROJECT_ROOT} && {GRADLE_WRAPPER} run --args="outgress"',
            env={
                **JAVA_ENV,
                'DUCKDB_PATH': str(DB_PATH),
                'OUTPUT_DIR': str(PROJECT_ROOT / 'output'),
                'OUTGRESS_DAYS': '1',
            },
        )

        enrichment >> clustering >> outgress

    # Task 4: Generate statistics
    generate_stats = PythonOperator(
        task_id='generate_stats',
        python_callable=lambda **context: print("Pipeline completed successfully"),
        trigger_rule='none_failed',  # Run even if skip_processing branch was taken
    )

    # DAG flow
    ingress >> check_articles >> [skip_processing, process_articles] >> generate_stats
