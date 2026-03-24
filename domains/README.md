# Domains

Each directory contains the configuration for one news aggregator domain.

## Structure

```
domains/
├── _template/          # Blank template — copy this to start a new domain
│   ├── domain.json     # LLM prompts, audience, relevance rules
│   ├── taxonomy.json   # Hierarchical classification (areas, sub-areas, tags)
│   └── sources.yml     # RSS feeds, subreddits, Bluesky accounts, GitHub repos
│
├── jvm-daily/          # JVM ecosystem (Java, Kotlin, Spring, Quarkus, ...)
│   ├── domain.json
│   ├── taxonomy.json
│   └── sources.yml
│
└── README.md           # This file
```

## Creating a new domain

```bash
# 1. Copy the template
cp -r domains/_template domains/my-daily

# 2. Edit the three config files
$EDITOR domains/my-daily/domain.json      # prompts, audience, examples
$EDITOR domains/my-daily/taxonomy.json    # areas, sub-areas, impact tags
$EDITOR domains/my-daily/sources.yml      # feeds, subreddits, accounts

# 3. Run the pipeline
DOMAIN_CONFIG_PATH=domains/my-daily/domain.json \
CONFIG_PATH=domains/my-daily/sources.yml \
LLM_PROVIDER=gemini GEMINI_API_KEY=<key> \
./app/build/install/app/bin/app pipeline
```

Or use the Claude Code skill: `/create-domain`

## What each file does

| File | Purpose | Used by |
|------|---------|---------|
| `domain.json` | LLM system prompts, relevance gate criteria, entity/topic examples, audience description, branding | EnrichmentWorkflow, ClusteringWorkflow, OutgressWorkflow |
| `taxonomy.json` | Hierarchical classification tree with LLM-optimized hints for each area/sub-area | TaxonomyClassifier |
| `sources.yml` | Data source URLs (RSS, Reddit, GitHub, Bluesky) | IngressWorkflow (via SourceRegistry) |
