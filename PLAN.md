# JVM Daily — Detailed Plan

> Inspired by [Latent Space AI News](https://news.smol.ai) by Swyx — adapted for the JVM ecosystem (Java, Kotlin, Scala, Groovy, Clojure, GraalVM, Spring, Quarkus, etc.)

---

## 1. Czym jest JVM Daily?

**JVM Daily** to codzienny, zautomatyzowany newsletter/portal z newsami ze świata JVM — agregujący informacje z Twittera/X, Reddita, Discordów, GitHub Trending, blogów i mailing list.

**Kluczowa wartość:** Oszczędność czasu — zamiast śledzić 50+ źródeł, użytkownik dostaje jeden skondensowany przegląd dziennie.

**Model:** ~99% generowane przez AI agentów, ~1% ludzkiej kuracji (wybór najlepszego outputu + opcjonalny komentarz redakcyjny).

---

## 2. Jak działa Latent Space AI News (wzorzec)

### Pipeline w skrócie:
1. **Scraping** — codziennie zbiera ~200-300K słów z 500+ kont Twitter, 20+ Discordów (200+ kanałów), 7-12 subredditów
2. **Link enrichment** — gdy w dyskusji pojawia się link, system go otwiera i generuje summary zawartości
3. **Multi-stage summarization** — LLM podsumowuje: kanał → serwer → klaster tematyczny
4. **Best-of-N selection** — 4 równoległe pipeline'y, człowiek wybiera najlepszy output
5. **Publikacja** — automatyczna publikacja na stronie + email newsletter

### Technologie Latent Space:
- **Model AI:** Claude 3.5 Sonnet (best of 4 runs)
- **Frontend:** Astro (static site generator)
- **Hosting:** Vercel
- **Email:** Resend
- **Link scraping:** `scrapedown` (Cloudflare Worker → HTML → Markdown)
- **Backend:** Proprietarny "smol_ai talk backend"

### Format outputu Latent Space:
```
[Nagłówek: data, statystyki źródeł, oszacowany czas czytania]
├── Editorial Lead (opcjonalny, ludzki)
├── AI Twitter Recap (max 6 klastrów tematycznych + memy)
├── AI Reddit Recap (techniczne + ogólne subreddity)
├── AI Discord Recap (summary per serwer + per kanał)
└── Tagi + komentarze
```

---

## 3. Źródła danych dla JVM Daily

### 3.1 Twitter/X (~100-200 kont)

**Kategorie kont do śledzenia:**

| Kategoria | Przykłady kont |
|---|---|
| **Java oficjalnie** | @java, @OpenJDK, @JavaMagazine, @Inside_Java |
| **Kotlin oficjalnie** | @kotlin, @JetBrains, @RomanElizarov |
| **Spring ecosystem** | @springboot, @springframework, @staboromek, @jaboromek, @philwebb |
| **Quarkus/Micronaut** | @QuarkusIO, @miaboromautfw, @maxandersen |
| **GraalVM** | @graboraalvm, @thomaswue |
| **Osoby wpływowe** | @venkat_s, @joshlong, @nipaborafx, @tagaborir, @DonRaab, @mariofusco |
| **Konferencje** | @Devoxx, @JakartaEE, @KotlinConf, @SpringOne |
| **Build tools** | @gradle, @ApacheMaven |
| **Blogerzy/Youtuberzy** | @Java_Brains, @aborodarsh, @aiabororns |
| **Firmy** | @Oracle, @RedHat, @Azul, @BellSoft |
| **Polskie JVM community** | @WarszawaJUG, @KrakowJUG, @4Developers_pl |

**Implementacja:**
- Twitter API v2 (lub scraping via Nitter/podobne)
- Pobieranie tweetów z curated listy (Twitter List)
- Metadata: text, impression_count, retweet_count, like_count, created_at, URLs
- Filtrowanie: minimum threshold engagement lub keyword match

### 3.2 Reddit (~8-12 subredditów)

| Subreddit | Typ | Opis |
|---|---|---|
| r/java | Techniczny | Główny subreddit Java |
| r/Kotlin | Techniczny | Główny subreddit Kotlin |
| r/scala | Techniczny | Główny subreddit Scala |
| r/clojure | Techniczny | Główny subreddit Clojure |
| r/SpringBoot | Techniczny | Spring Boot community |
| r/graalvm | Techniczny | GraalVM dyskusje |
| r/IntelliJIDEA | Narzędzia | IDE community |
| r/javahelp | Edukacyjny | Pytania i odpowiedzi |
| r/programming | Ogólny | Filtrowane pod JVM keywords |
| r/ExperiencedDevs | Ogólny | Filtrowane pod JVM keywords |

**Implementacja:**
- Reddit API (PRAW-equivalent w Kotlinie lub HTTP client)
- Pobieranie: hot/top posts z ostatnich 24h
- Metadata: title, score, num_comments, url, selftext, top comments
- Filtrowanie r/programming i r/ExperiencedDevs: regex/keyword match na JVM-related tematy

### 3.3 Discord (~10-15 serwerów)

| Serwer | Kanały priorytetowe |
|---|---|
| **Kotlin** (oficjalny) | #general, #announcements, #coroutines, #multiplatform |
| **Spring** (oficjalny) | #spring-boot, #spring-cloud, #spring-security |
| **JetBrains** | #intellij, #kotlin, #fleet |
| **Quarkus** | #general, #dev |
| **GraalVM** | #general, #native-image |
| **Virtual Threads / Project Loom** | odpowiednie kanały w OpenJDK |
| **Gradle** | #general, #help |
| **Micronaut** | #general |
| **Jakarta EE** | #general |
| **Java Champions** | (jeśli publiczny) |
| **Unlogged (debug tools)** | #general |

**Implementacja:**
- Discord Bot API (JDA - Java Discord API)
- Scraping wiadomości z ostatnich 24h per kanał
- Metadata: author, content, timestamp, reactions, thread info, attached URLs
- Minimum message threshold per kanał (skip kanały z <5 wiadomości)

### 3.4 GitHub Trending (unikalne dla JVM Daily vs Latent Space)

| Filtr | Wartość |
|---|---|
| **Języki** | Java, Kotlin, Scala, Groovy, Clojure |
| **Okres** | Daily |
| **Dane** | repo name, description, stars gained today, language, topics |

**Implementacja:**
- GitHub API v4 (GraphQL) lub scraping github.com/trending
- Top 10-20 trending repos per język
- Enrichment: README summary, recent release notes

### 3.5 Blogi i RSS Feeds (~20-30 feedów)

| Źródło | URL |
|---|---|
| Inside Java (Oracle) | inside.java |
| Spring Blog | spring.io/blog |
| Kotlin Blog | blog.jetbrains.com/kotlin |
| Baeldung | baeldung.com (nowe artykuły) |
| InfoQ Java | infoq.com/java |
| DZone Java | dzone.com/java |
| Quarkus Blog | quarkus.io/blog |
| Micronaut Blog | micronaut.io/blog |
| GraalVM Blog | graalvm.org/blog |
| foojay.io | foojay.io (OpenJDK community) |
| Gradle Blog | blog.gradle.org |
| JetBrains Blog | blog.jetbrains.com |
| Java Magazine | oracle.com/java/technologies/javase-magazine.html |
| Vlad Mihalcea | vladmihalcea.com |
| Thorben Janssen | thorben-janssen.com |
| Marco Behler | marcobehler.com |
| Adam Bien | adam-bien.com |
| Hacker News | news.ycombinator.com (JVM-filtered) |

**Implementacja:**
- RSS/Atom parser (Rome/JDOM lub kotlinx-io + XML parser)
- Pobieranie nowych wpisów z ostatnich 24h
- Enrichment: scraping treści artykułu → markdown → summary

### 3.6 Mailing Lists (opcjonalnie, faza 2)

| Lista | Opis |
|---|---|
| OpenJDK mailing lists | JEP proposals, JDK updates |
| jakarta.ee-spec | Jakarta EE specification updates |
| kotlin-dev | Kotlin language development |

---

## 4. Pipeline przetwarzania danych

### 4.1 Architektura pipeline'u

```
┌─────────────────────────────────────────────────────────────────┐
│                        FAZA 1: COLLECTION                       │
│                                                                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │ Twitter  │ │  Reddit  │ │ Discord  │ │  GitHub  │  ...RSS  │
│  │ Scraper  │ │ Scraper  │ │   Bot    │ │ Trending │  Scraper │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘          │
│       │             │            │             │                │
│       ▼             ▼            ▼             ▼                │
│  ┌─────────────────────────────────────────────────────┐       │
│  │              Raw Data Store (JSON/DB)                │       │
│  └─────────────────────────┬───────────────────────────┘       │
└────────────────────────────┼───────────────────────────────────┘
                             │
┌────────────────────────────┼───────────────────────────────────┐
│                  FAZA 2: ENRICHMENT                             │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────┐       │
│  │           Link Extractor & Content Scraper           │       │
│  │  (dla każdego URL w wiadomości → fetch → markdown)   │       │
│  └──────────────────────────┬──────────────────────────┘       │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────┐       │
│  │           Link Content Summarizer (LLM)              │       │
│  │  (krótkie summary każdego zlinkowanego artykułu)     │       │
│  └──────────────────────────┬──────────────────────────┘       │
└────────────────────────────┼───────────────────────────────────┘
                             │
┌────────────────────────────┼───────────────────────────────────┐
│             FAZA 3: MULTI-STAGE SUMMARIZATION                  │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────┐       │
│  │  Stage 1: Per-source summarization                   │       │
│  │  - Twitter: per-tweet → per-thread grouping          │       │
│  │  - Reddit: per-post + top comments summary           │       │
│  │  - Discord: per-channel summary                      │       │
│  │  - GitHub: per-repo summary                          │       │
│  │  - Blogs: per-article summary                        │       │
│  └──────────────────────────┬──────────────────────────┘       │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────┐       │
│  │  Stage 2: Thematic clustering                        │       │
│  │  - NER + topic modeling across all sources            │       │
│  │  - Grupowanie w max 8 kategorii tematycznych          │       │
│  │  - Cross-source correlation (ten sam temat na         │       │
│  │    Twitterze + Reddicie + Discordzie = 1 klaster)     │       │
│  └──────────────────────────┬──────────────────────────┘       │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────┐       │
│  │  Stage 3: Final compilation                          │       │
│  │  - Złożenie finalnego newslettera                     │       │
│  │  - Formatowanie markdown                              │       │
│  │  - Generowanie nagłówka i statystyk                   │       │
│  └──────────────────────────┬──────────────────────────┘       │
└────────────────────────────┼───────────────────────────────────┘
                             │
┌────────────────────────────┼───────────────────────────────────┐
│              FAZA 4: BEST-OF-N SELECTION                       │
│                             │                                   │
│  ┌──────────────┐ ┌────────┴───────┐ ┌──────────────┐         │
│  │ Pipeline A   │ │  Pipeline B    │ │ Pipeline C   │  (x4)   │
│  │ (variant 1)  │ │  (variant 2)   │ │ (variant 3)  │         │
│  └──────┬───────┘ └────────┬───────┘ └──────┬───────┘         │
│         │                  │                 │                  │
│         ▼                  ▼                 ▼                  │
│  ┌─────────────────────────────────────────────────────┐       │
│  │      Selector (LLM judge lub human pick)            │       │
│  └─────────────────────────┬───────────────────────────┘       │
└────────────────────────────┼───────────────────────────────────┘
                             │
┌────────────────────────────┼───────────────────────────────────┐
│               FAZA 5: PUBLICATION                              │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────┐       │
│  │  Static Site Generator (Astro/Hugo/custom)           │       │
│  │  → Deploy to Vercel/Netlify                          │       │
│  └──────────────────────────┬──────────────────────────┘       │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────┐       │
│  │  Email Newsletter (Resend/Buttondown/Mailgun)        │       │
│  └──────────────────────────┬──────────────────────────┘       │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────┐       │
│  │  Social media cross-post (Twitter/Bluesky/LinkedIn)  │       │
│  └─────────────────────────────────────────────────────┘       │
└────────────────────────────────────────────────────────────────┘
```

### 4.2 Szczegóły każdej fazy

#### Faza 1: Collection (Kotlin coroutines, parallel)

```kotlin
// Pseudokod architektury
class DailyCollectionJob {
    suspend fun collectAll(): RawDailyData = coroutineScope {
        val twitter = async { twitterCollector.collect() }
        val reddit = async { redditCollector.collect() }
        val discord = async { discordCollector.collect() }
        val github = async { githubTrendingCollector.collect() }
        val blogs = async { rssFeedCollector.collect() }

        RawDailyData(
            twitter = twitter.await(),
            reddit = reddit.await(),
            discord = discord.await(),
            github = github.await(),
            blogs = blogs.await(),
            collectedAt = Instant.now()
        )
    }
}
```

**Scheduling:** Cron job o 06:00 UTC (zbieranie danych z ostatnich 24h)

#### Faza 2: Enrichment

Dla każdego URL znalezionego w zebranych danych:
1. Fetch URL → HTML
2. HTML → clean Markdown (Jsoup + custom cleaner)
3. Markdown → LLM summary (max 200 słów)
4. Dołączenie summary do oryginalnego kontekstu

**Rate limiting:** Max 100 URL enrichments per run (priorytet wg engagement)

#### Faza 3: Multi-stage Summarization

**Stage 1 — Per-source prompts:**

Twitter prompt (przykład):
```
You are a JVM ecosystem news curator. Analyze these tweets from the last 24 hours.
Group them into maximum 8 thematic categories relevant to JVM developers.
Categories should cover: language updates, framework releases, performance,
tooling, community events, tutorials, job market, and memes/humor.

Rules:
- Sort tweets within each category by impression count (descending)
- Minimum 3 tweets per category (merge small categories)
- Include direct quotes (max 150 chars) with attribution
- Always include 1 "Community & Humor" category
- Use Named Entity Recognition to identify: JDK versions, framework names,
  company names, conference names, JEP numbers
```

Reddit prompt (przykład):
```
Summarize the top JVM-related Reddit discussions from the last 24 hours.
Split into:
1. Technical discussions (r/java, r/Kotlin, r/scala) — detailed analysis
   with top comment highlights
2. Broader discussions (r/programming JVM-filtered) — brief overview

For each post include: title, score, comment count, key discussion points,
and any consensus or controversy noted.
```

Discord prompt (przykład):
```
Summarize JVM Discord server activity from the last 24 hours.
For each server provide:
1. One-paragraph high-level summary
2. Per-channel breakdown (channels with 5+ messages only):
   - Message count and activity level (🔥 for >50 msgs, 🔥🔥 for >100)
   - Key topics discussed
   - Notable user quotes (with Discord handle)
   - Links shared
```

**Stage 2 — Cross-source thematic clustering:**
```
You have summaries from Twitter, Reddit, Discord, GitHub Trending, and blogs
about the JVM ecosystem. Identify cross-source themes — topics that appear
in multiple sources. Create a "Top Stories" section with 3-5 stories that
have the most cross-source coverage. Each story should synthesize information
from all sources that mention it.
```

**Stage 3 — Final compilation:**
```
Compile the final JVM Daily newsletter for [DATE].
Structure:
1. Header with stats
2. 🔥 Top Stories (cross-source, 3-5 items)
3. ☕ JVM Twitter Recap (thematic clusters)
4. 💬 JVM Reddit Recap (technical + general)
5. 🎮 JVM Discord Recap (server summaries)
6. ⭐ GitHub Trending JVM (top repos)
7. 📝 Blog & Article Highlights (new posts)
8. Tags

Tone: Professional but accessible. Write for experienced JVM developers.
Use Polish technical jargon where appropriate (project targets Polish JVM community initially).
```

#### Faza 4: Best-of-N Selection

- Uruchomienie 4 wariantów pipeline'u z różnymi parametrami:
  - Variant A: Claude Sonnet — standard temperature (0.3)
  - Variant B: Claude Sonnet — higher temperature (0.7)
  - Variant C: GPT-4o — standard temperature
  - Variant D: Claude Sonnet — z dodatkowym "editorial voice" promptem
- **LLM Judge** ocenia 4 outputy wg kryteriów: completeness, accuracy, readability, engagement
- Alternatywnie: human pick (MVP)

---

## 5. Format outputu JVM Daily

### 5.1 Strona internetowa (per issue)

```markdown
---
title: "JVM Daily — 9 Feb 2026"
date: 2026-02-09
headline: "Spring Boot 4.0 RC1, Kotlin 2.3 Preview, and Virtual Threads Go Mainstream"
stats:
  twitter_accounts: 150
  discord_servers: 12
  discord_channels: 89
  discord_messages: 3420
  reddit_subreddits: 10
  github_repos: 45
  blog_posts: 18
  reading_time_saved: "~180 min"
tags: [spring-boot, kotlin, virtual-threads, graalvm, jdk-25]
---

# JVM Daily — 9 Feb 2026

> Checked 150 Twitter accounts, 12 Discord servers (89 channels, 3,420 messages),
> 10 subreddits, 45 GitHub trending repos, and 18 blog posts.
> Estimated reading time saved: ~180 minutes.

---

## 🔥 Top Stories

### 1. Spring Boot 4.0 RC1 Released
[Cross-source synthesis: Twitter + Reddit + Discord + Blog]
...

### 2. Kotlin 2.3 Introduces Context Parameters
[Cross-source synthesis: Twitter + Reddit + Kotlin Discord]
...

### 3. JDK 25 Virtual Threads Performance Breakthrough
[Cross-source synthesis: Twitter + Reddit + foojay.io blog]
...

---

## ☕ JVM Twitter Recap

### Language & Platform Updates
...

### Framework Releases & Updates
...

### Performance & Optimization
...

### Tooling & Developer Experience
...

### Community Events & Conferences
...

### Community & Humor 😄
...

### Top Tweets by Engagement
| Tweet | Author | Impressions |
|-------|--------|-------------|
| ...   | ...    | ...         |

---

## 💬 JVM Reddit Recap

### Technical Subreddits

#### r/java (42 posts, 580 comments)
...

#### r/Kotlin (28 posts, 320 comments)
...

### Broader Discussions

#### r/programming (JVM-related)
...

---

## 🎮 JVM Discord Recap

### Spring (Official)
**Activity:** 🔥🔥 (247 messages across 12 channels)
...

#### #spring-boot (89 messages)
...

### Kotlin (Official)
**Activity:** 🔥 (156 messages across 8 channels)
...

---

## ⭐ GitHub Trending JVM

| # | Repository | Language | ⭐ Today | Description |
|---|-----------|----------|----------|-------------|
| 1 | spring-projects/spring-boot | Java | +234 | ... |
| 2 | JetBrains/kotlin | Kotlin | +189 | ... |
| ... |

---

## 📝 Blog & Article Highlights

### [Inside Java: JDK 25 Feature Freeze](https://inside.java/...)
> Summary of the article...

### [Baeldung: Guide to Kotlin Context Parameters](https://baeldung.com/...)
> Summary of the article...

...

---

*Tags: #spring-boot #kotlin #virtual-threads #graalvm #jdk-25 #performance #tooling*
```

### 5.2 Email Newsletter Format

- Skrócona wersja (Top Stories + highlights z każdej sekcji)
- Link do pełnej wersji na stronie
- Max 2000 słów w emailu (pełna wersja na stronie może mieć 10K+)

---

## 6. Tech Stack JVM Daily

### 6.1 Backend / Pipeline (Kotlin na JVM)

| Komponent | Technologia | Powód |
|---|---|---|
| **Język** | Kotlin | Projekt JVM, natywne coroutines, concise syntax |
| **Build** | Gradle (Kotlin DSL) | Już skonfigurowany w projekcie |
| **HTTP Client** | Ktor Client | Kotlin-native, coroutines support |
| **JSON** | kotlinx.serialization | Kotlin-native, compile-time safe |
| **XML/RSS** | Rome (JDOM) lub kotlin-xml | RSS/Atom parsing |
| **HTML→Markdown** | Jsoup + Flexmark | Scraping + HTML→MD conversion |
| **Discord** | JDA (Java Discord API) | Mature, well-documented |
| **AI/LLM** | Koog Agents (already in project) + Anthropic SDK | Multi-model support |
| **Scheduling** | Quartz lub kotlinx-coroutines-scheduled | Cron-like scheduling |
| **Database** | DuckDB (via plain JDBC) | Embedded, OLAP-optimized, zero infrastructure |
| **Config** | HOCON (Typesafe Config) lub YAML | Source lists, prompts, settings |

### 6.2 Frontend / Website

| Komponent | Technologia | Powód |
|---|---|---|
| **Static site** | Astro lub Hugo | Fast, markdown-native |
| **Hosting** | Vercel lub Netlify | Free tier, auto-deploy |
| **Theme** | Custom (dark/light) | Developer-friendly |
| **Search** | Pagefind (client-side) | Fast, no backend needed |
| **Comments** | Giscus (GitHub Discussions) | Free, developer-friendly |
| **Analytics** | Plausible lub Umami | Privacy-respecting |

### 6.3 Distribution

| Kanał | Technologia |
|---|---|
| **Email** | Resend API lub Buttondown |
| **RSS feed** | Auto-generated z Astro/Hugo |
| **Twitter/X** | Twitter API v2 (auto-post summary) |
| **Bluesky** | AT Protocol API |
| **LinkedIn** | LinkedIn API (weekly digest) |

---

## 7. Prompty i reguły summaryzacji

### 7.1 Globalne reguły

1. **Język:** Polski (docelowo opcja EN/PL)
2. **Ton:** Profesjonalny, techniczny, ale przystępny
3. **Terminologia:** Angielskie terminy techniczne (nie tłumaczymy "virtual threads" na "wirtualne wątki")
4. **Cytaty:** Oryginalne cytaty w języku źródłowym (EN), komentarz po polsku
5. **Minimum threshold:** Nie raportuj tematów z <3 mentions across sources
6. **Deduplication:** Ten sam temat na Twitterze, Reddicie i Discordzie = 1 Top Story, nie 3 osobne
7. **Fact-checking instruction:** LLM ma flagować informacje, których nie jest pewien

### 7.2 Per-source reguły

**Twitter:**
- Max 8 klastrów tematycznych
- Zawsze 1 klaster "Community & Humor"
- Sortowanie wg impression_count (malejąco)
- Min 3 tweety per klaster
- Cytaty max 150 znaków

**Reddit:**
- Podział: Technical (r/java, r/Kotlin, r/scala) vs Broader (r/programming)
- Dla technical: summary + top 3 komentarze
- Dla broader: tylko lista z linkami i 1-zdaniowymi opisami

**Discord:**
- Skip kanały z <5 wiadomości
- 🔥 system: >50 msg = 🔥, >100 msg = 🔥🔥, >200 msg = 🔥🔥🔥
- Zachowanie Discord handles przy cytatach
- Linkowanie do konkretnych wiadomości (z UTC timestamp)

**GitHub Trending:**
- Top 15 repos (all JVM languages combined)
- Per repo: nazwa, opis, stars today, język, notable recent commits/releases

**Blogs/RSS:**
- Summary max 200 słów per artykuł
- Link do oryginału
- Highlight key takeaways (bullet points)

---

## 8. Konfiguracja i zarządzanie źródłami

### 8.1 Plik konfiguracji źródeł

```yaml
# sources.yaml
twitter:
  list_id: "1234567890"  # curated Twitter List ID
  accounts:
    - handle: "java"
      category: "official"
      priority: "high"
    - handle: "kotlin"
      category: "official"
      priority: "high"
    # ... 150+ kont

  min_impressions: 100  # ignore tweets below this
  max_age_hours: 26     # 24h + 2h buffer

reddit:
  subreddits:
    - name: "java"
      type: "technical"
      min_score: 5
    - name: "Kotlin"
      type: "technical"
      min_score: 5
    - name: "programming"
      type: "general"
      min_score: 20
      keyword_filter: ["java", "kotlin", "jvm", "spring", "gradle", ...]

  max_age_hours: 26
  max_posts_per_sub: 50
  include_top_comments: 5

discord:
  servers:
    - name: "Kotlin"
      guild_id: "123456789"
      channels:
        - id: "111"
          name: "general"
          priority: "high"
        - id: "222"
          name: "announcements"
          priority: "high"
      min_messages: 5

github:
  languages: ["Java", "Kotlin", "Scala", "Groovy", "Clojure"]
  trending_period: "daily"
  max_repos: 20

rss:
  feeds:
    - url: "https://inside.java/feed.xml"
      name: "Inside Java"
      priority: "high"
    - url: "https://spring.io/blog.atom"
      name: "Spring Blog"
      priority: "high"
    # ... 20+ feeds
  max_age_hours: 48  # blogs might be delayed in RSS
```

### 8.2 Plik konfiguracji LLM

```yaml
# llm-config.yaml
summarization:
  primary_model: "claude-sonnet-4-5-20250929"
  fallback_model: "gpt-4o"

  stages:
    per_source:
      temperature: 0.3
      max_tokens: 4000
    thematic_clustering:
      temperature: 0.5
      max_tokens: 6000
    final_compilation:
      temperature: 0.4
      max_tokens: 12000

  best_of_n:
    n: 4
    variants:
      - model: "claude-sonnet-4-5-20250929"
        temperature: 0.3
      - model: "claude-sonnet-4-5-20250929"
        temperature: 0.7
      - model: "gpt-4o"
        temperature: 0.3
      - model: "claude-sonnet-4-5-20250929"
        temperature: 0.4
        system_prompt_suffix: "Write with an opinionated editorial voice."

    judge:
      model: "claude-sonnet-4-5-20250929"
      criteria:
        - completeness (0-10)
        - accuracy (0-10)
        - readability (0-10)
        - engagement (0-10)

link_enrichment:
  max_urls_per_run: 100
  summary_max_words: 200
  model: "claude-haiku-4-5-20251001"  # cheaper model for bulk summarization
  timeout_seconds: 30
```

---

## 9. Struktura modułów kodu

```
jvm-daily/
├── app/
│   └── src/main/kotlin/jvm/daily/
│       ├── Application.kt                    # Entry point, scheduling
│       ├── config/
│       │   ├── SourcesConfig.kt              # Data class for sources.yaml
│       │   └── LlmConfig.kt                  # Data class for llm-config.yaml
│       ├── collection/
│       │   ├── Collector.kt                  # Interface
│       │   ├── TwitterCollector.kt           # Twitter/X API integration
│       │   ├── RedditCollector.kt            # Reddit API integration
│       │   ├── DiscordCollector.kt           # Discord Bot integration
│       │   ├── GitHubTrendingCollector.kt    # GitHub API integration
│       │   └── RssFeedCollector.kt           # RSS/Atom feed parser
│       ├── enrichment/
│       │   ├── LinkExtractor.kt              # Extract URLs from raw data
│       │   ├── ContentScraper.kt             # Fetch URL → Markdown
│       │   └── LinkSummarizer.kt             # LLM summary of link content
│       ├── summarization/
│       │   ├── SummarizationPipeline.kt      # Orchestrator
│       │   ├── PerSourceSummarizer.kt        # Stage 1
│       │   ├── ThematicClusterer.kt          # Stage 2
│       │   └── FinalCompiler.kt              # Stage 3
│       ├── selection/
│       │   ├── BestOfNRunner.kt              # Run N pipeline variants
│       │   └── LlmJudge.kt                  # LLM-based quality judge
│       ├── publication/
│       │   ├── MarkdownRenderer.kt           # Render final markdown
│       │   ├── StaticSitePublisher.kt        # Deploy to Astro/Hugo
│       │   ├── EmailPublisher.kt             # Send via Resend/Buttondown
│       │   └── SocialMediaPublisher.kt       # Cross-post to Twitter/Bluesky
│       ├── model/
│       │   ├── RawData.kt                    # Raw collected data models
│       │   ├── EnrichedData.kt               # Data with link summaries
│       │   ├── Summary.kt                    # Summarization output models
│       │   └── DailyIssue.kt                 # Final issue model
│       └── llm/
│           ├── LlmClient.kt                 # Unified LLM client (Claude/GPT)
│           └── PromptTemplates.kt            # All prompt templates
├── prompts/
│   ├── twitter-summarize.txt                 # Externalized prompt templates
│   ├── reddit-summarize.txt
│   ├── discord-summarize.txt
│   ├── github-summarize.txt
│   ├── blog-summarize.txt
│   ├── thematic-clustering.txt
│   ├── final-compilation.txt
│   └── judge.txt
├── config/
│   ├── sources.yaml
│   └── llm-config.yaml
├── site/                                      # Frontend (Astro/Hugo)
│   ├── src/
│   ├── public/
│   └── astro.config.mjs
└── .github/
    └── workflows/
        ├── gradle.yml                         # Build CI
        └── daily-run.yml                      # Scheduled daily pipeline
```

---

## 10. Harmonogram (Cron / GitHub Actions)

### Codzienny pipeline:

```yaml
# .github/workflows/daily-run.yml
name: JVM Daily Pipeline
on:
  schedule:
    - cron: '0 6 * * 1-5'  # Mon-Fri at 06:00 UTC (07:00 CET)
  workflow_dispatch: {}      # Manual trigger

jobs:
  generate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew run
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
          TWITTER_BEARER_TOKEN: ${{ secrets.TWITTER_BEARER_TOKEN }}
          REDDIT_CLIENT_ID: ${{ secrets.REDDIT_CLIENT_ID }}
          REDDIT_CLIENT_SECRET: ${{ secrets.REDDIT_CLIENT_SECRET }}
          DISCORD_BOT_TOKEN: ${{ secrets.DISCORD_BOT_TOKEN }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          RESEND_API_KEY: ${{ secrets.RESEND_API_KEY }}
```

### Timeline codzienny:
```
06:00 UTC — Start collection (parallel scraping, ~5 min)
06:05 UTC — Start enrichment (link fetching + summarization, ~10 min)
06:15 UTC — Start multi-stage summarization (4x parallel, ~15 min)
06:30 UTC — Best-of-N selection (LLM judge, ~5 min)
06:35 UTC — Publication (site deploy + email + social, ~5 min)
06:40 UTC — Done. Newsletter delivered before European morning coffee ☕
```

---

## 11. Fazy implementacji (Roadmap)

### Faza 0: Proof of Concept (1-2 tygodnie)
- [ ] Pojedynczy scraper (Reddit r/java)
- [ ] Pojedyncze podsumowanie LLM (1 model, 1 run)
- [ ] Output do pliku Markdown
- [ ] Manualne uruchamianie

### Faza 1: MVP (2-4 tygodnie)
- [ ] Twitter + Reddit + RSS collectors
- [ ] Link enrichment pipeline
- [ ] Multi-stage summarization (1 model)
- [ ] Markdown output → prosty static site (Hugo/Astro)
- [ ] GitHub Actions cron scheduling
- [ ] Basic email newsletter (Resend)

### Faza 2: Pełna wersja (4-8 tygodni)
- [ ] Discord Bot integration
- [ ] GitHub Trending integration
- [ ] Best-of-N pipeline (4 warianty)
- [ ] LLM Judge
- [ ] Cross-source thematic clustering (Top Stories)
- [ ] Full website z search, tags, dark mode
- [ ] Social media auto-posting

### Faza 3: Rozszerzenia (ongoing)
- [ ] Wersja dwujęzyczna (PL/EN)
- [ ] Mailing list monitoring (OpenJDK, Jakarta)
- [ ] Podcast/audio version (TTS)
- [ ] Weekly digest (podsumowanie tygodnia)
- [ ] Personalizacja (wybór technologii: "Interesuję się tylko Spring + Kotlin")
- [ ] API publiczne (JSON feed)
- [ ] Community contributions (dodawanie źródeł przez użytkowników)
- [ ] Archiwum z wyszukiwarką full-text

---

## 12. Koszty szacunkowe (dziennie)

| Pozycja | Szacunek |
|---|---|
| **Claude Sonnet API** (4 runs × ~50K tokens in + ~12K out) | ~$1.50-3.00 |
| **Claude Haiku** (link enrichment, 100 URLs) | ~$0.10-0.30 |
| **GPT-4o** (1 run for variety) | ~$0.50-1.00 |
| **Twitter API** | Free tier lub $100/mo (Basic) |
| **Reddit API** | Free (within rate limits) |
| **Discord Bot** | Free |
| **GitHub API** | Free |
| **Vercel hosting** | Free tier |
| **Resend email** | Free tier (3K emails/mo) |
| **Total dzienny** | ~$2-5 |
| **Total miesięczny** | ~$50-150 |

---

## 13. Differentiators vs Latent Space AI News

| Aspekt | Latent Space AI News | JVM Daily |
|---|---|---|
| **Scope** | Cały AI/ML | Tylko JVM ecosystem |
| **Sources** | Twitter + Reddit + Discord | + GitHub Trending + RSS Blogs |
| **Top Stories** | Brak (sekcje osobno) | Cross-source synthesis |
| **Język** | English | Polish (later bilingual) |
| **GitHub Trending** | Brak | Dedykowana sekcja |
| **Blog aggregation** | Brak | RSS feed monitoring |
| **JEP/OpenJDK tracking** | N/A | Śledzenie JDK development |
| **Conference tracking** | Minimal | Devoxx, KotlinConf, SpringOne |
| **Community** | AI general | Targeted JVM dev community |

---

## 14. Metryki sukcesu

| Metryka | Target (3 miesiące) | Target (12 miesięcy) |
|---|---|---|
| **Subskrybenci email** | 500 | 5,000 |
| **Unikalni czytelnicy/dzień** | 200 | 2,000 |
| **Open rate emaila** | >40% | >35% |
| **Twitter followers** | 500 | 3,000 |
| **Issues published** | 60 (daily weekdays) | 250 |
| **Community PRs** | 5 | 50 |

---

*Plan stworzony: 2026-02-09*
*Wzorzec: Latent Space AI News by Swyx (news.smol.ai)*
*Projekt: JVM Daily — github.com/ArturSkowronski/jvm-daily*
