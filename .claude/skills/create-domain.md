# Skill: Create a New Daily News Domain

Create a complete domain configuration for the Daily Engine — taxonomy, domain profile, and sources config — so the engine can aggregate and classify news for any domain.

## When to use

When the user wants to create a new news aggregator domain (e.g., "AI Daily", "Rust Daily", "DevOps Daily") or modify the existing JVM Daily domain.

## Workflow

### Phase 1: Domain Discovery

Ask the user about their domain:

1. **Name and audience**: "What's the domain name and who's the target audience?" (e.g., "AI Daily for ML engineers")
2. **Key technologies/areas**: "What are the 10-15 major areas to track?" (e.g., LLMs, computer vision, MLOps, training infrastructure)
3. **Sources**: "What RSS feeds, subreddits, Bluesky accounts, and GitHub repos should we track?"
4. **What makes something relevant**: "What should be included vs excluded from the digest?"

### Phase 2: Generate Taxonomy (`config/taxonomy-{slug}.json`)

Create a hierarchical taxonomy following the schema at `config/taxonomy-schema.json`:

```json
{
  "$schema": "./taxonomy-schema.json",
  "version": "2025-01",
  "meta": {
    "name": "{Domain Name}",
    "description": "{one-line description}",
    "domain": "{slug}"
  },
  "nodes": [
    {
      "slug": "{area-slug}",
      "title": "{Area Title}",
      "classification_hints": {
        "summary": "Articles about {description of what belongs here}",
        "topics": ["specific signal 1", "specific signal 2", "..."],
        "not_this_node": ["disambiguation: if article is about X, classify as Y instead"],
        "boundary": "When to classify at this level vs a sub-area"
      },
      "children": [
        {
          "slug": "{sub-area-slug}",
          "title": "{Sub-Area Title}",
          "classification_hints": {
            "summary": "{what belongs here}",
            "signals": ["specific detection signals"],
            "boundary": "When to use parent vs this sub-area"
          }
        }
      ]
    }
  ],
  "tag_sets": {
    "impact": [
      {
        "slug": "breaking-change",
        "name": "Breaking Change",
        "classification_hints": {
          "description": "Article discusses backward-incompatible changes",
          "signals": ["Breaking API change", "Migration required", "Deprecation with removal"]
        }
      }
    ]
  }
}
```

**Rules for good taxonomies:**
- 10-15 top-level areas (not too many, not too few)
- 2-5 sub-areas per area (only where needed — some areas need none)
- Each `classification_hints.summary` should be 1-2 sentences that an LLM can use to decide
- `not_this_node` is critical for disambiguation between similar areas
- `boundary` clarifies when to use parent vs child
- Impact tags should be 4-8 orthogonal dimensions

### Phase 3: Generate Domain Profile (`config/domain-{slug}.json`)

Create the domain profile following the structure in `config/domain.json`:

```json
{
  "name": "{Domain Name}",
  "slug": "{slug}",
  "description": "{Domain} news aggregator",
  "audience": "{target audience description}",
  "relevanceGate": {
    "include": ["5-8 inclusion criteria specific to this domain"],
    "exclude": ["3-5 exclusion criteria"]
  },
  "enrichment": {
    "systemPrompt": "You are a {domain} news analyst writing for a daily developer digest. Your audience is {audience}.",
    "entityExamples": ["4-6 example entities with versions"],
    "topicExamples": ["4-6 example topic tags"]
  },
  "clustering": {
    "systemPrompt": "You are an expert {domain} news curator creating a daily digest. {audience description}.",
    "audienceDescription": "{audience} who want signal, not noise",
    "exampleClusters": ["3-5 example cluster names"],
    "majorReleaseRules": ["3-5 rules for what counts as a MAJOR release"]
  }
}
```

### Phase 4: Generate Sources Config (`config/sources-{slug}.yml`)

Create sources following the schema in `config/sources.yml`:

```yaml
rss:
  - url: "{feed URL}"
    splitRoundups: true  # for digest/roundup feeds

reddit:
  - subreddit: "{subreddit}"
    timeWindow: "day"

githubTrending:
  languages: ["{lang1}", "{lang2}"]
  minStars: 10

githubReleases:
  repos:
    - "{org/repo}"

bluesky:
  accounts:
    - "{handle}"
```

**Tips for source selection:**
- RSS: look for official blogs, community aggregators, news sites with RSS
- Reddit: find the main subreddits for the domain (check subscriber count)
- GitHub: pick the most important repos in the ecosystem
- Bluesky: find key people and organizations (check bsky.app/search)
- OpenJDK Mail / JEP sources are JVM-specific — skip for other domains

### Phase 5: Verify

1. Validate taxonomy JSON against schema: `python3 -c "import json; json.load(open('config/taxonomy-{slug}.json'))"`
2. Validate domain profile: `python3 -c "import json; json.load(open('config/domain-{slug}.json'))"`
3. Test pipeline:
```bash
DOMAIN_CONFIG_PATH=config/domain-{slug}.json \
CONFIG_PATH=config/sources-{slug}.yml \
LLM_PROVIDER=gemini GEMINI_API_KEY=<key> \
./app/build/install/app/bin/app pipeline
```

### Phase 6: Deploy (optional)

Update `fly.toml` env vars or create a new Fly.io app for the new domain.

## Files referenced

- `config/taxonomy-schema.json` — JSON Schema for taxonomy validation
- `config/taxonomy.json` — JVM Daily taxonomy (reference example)
- `config/domain.json` — JVM Daily domain profile (reference example)
- `config/sources.yml` — JVM Daily sources (reference example)
- `app/src/main/kotlin/jvm/daily/config/DomainProfile.kt` — DomainProfile data class
- `app/src/main/kotlin/jvm/daily/workflow/TaxonomyLoader.kt` — taxonomy loader
