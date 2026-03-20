#!/usr/bin/env python3
"""
JVM Daily Viewer — articles + JobRunr pipeline monitoring.
Usage: python3 viewer/serve.py [port]
Default port: 8888

Env vars:
  JOBRUNR_URL   JobRunr base URL (default: http://localhost:8000)
"""

import json
import os
import re
import sys
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.parent
OUTPUT_DIR   = Path(os.environ.get("OUTPUT_DIR", str(Path.home() / ".jvm-daily" / "output")))
PORT         = int(sys.argv[1]) if len(sys.argv) > 1 else 8888
JOBRUNR_URL  = os.environ.get("JOBRUNR_URL", "http://localhost:8000")
DATE_PATTERN = re.compile(r'^\d{4}-\d{2}-\d{2}$')

# ──────────────────────────────────────────────────────────────────────────────

HTML = r"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>JVM Daily</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Newsreader:ital,wght@0,400;0,600;1,400&display=swap" rel="stylesheet">
  <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Inter', -apple-system, sans-serif;
           background: #fafafa; color: #1a1a1a;
           display: flex; flex-direction: column; height: 100vh; overflow: hidden; }

    /* ── Header ── */
    header { background: #fff; border-bottom: 1px solid #e5e5e5;
             padding: 0 24px; display: flex; align-items: center; height: 56px; flex-shrink: 0; }
    header h1 { font-family: 'Newsreader', Georgia, serif; font-size: 1.25rem;
                font-weight: 600; color: #1a1a1a; margin-right: 32px; letter-spacing: -0.01em; }
    .tab { background: transparent; border: none; border-bottom: 2px solid transparent;
           color: #888; font-size: 0.85rem; font-weight: 500; padding: 0 2px; height: 56px;
           margin-right: 24px; cursor: pointer; transition: color 0.15s; }
    .tab:hover { color: #555; }
    .tab.active { color: #1a1a1a; border-bottom-color: #1a1a1a; }
    #meta { margin-left: auto; font-size: 0.78rem; color: #999; font-weight: 400; }

    /* ── Layout ── */
    .view { display: flex; flex: 1; overflow: hidden; }
    .hidden { display: none !important; }

    /* ── Date sidebar ── */
    aside { width: 140px; flex-shrink: 0; background: #fff;
            border-right: 1px solid #e5e5e5; overflow-y: auto;
            padding: 16px 10px; display: flex; flex-direction: column; gap: 3px; }
    aside h2 { font-size: 0.6rem; text-transform: uppercase; letter-spacing: .1em;
               color: #aaa; padding: 0 8px 10px; font-weight: 600; }
    .date-btn { background: transparent; border: none; border-radius: 6px;
                color: #666; font-size: 0.8rem; padding: 8px 10px; cursor: pointer;
                text-align: left; font-weight: 400; transition: all 0.12s; }
    .date-btn:hover { background: #f0f0f0; color: #1a1a1a; }
    .date-btn.active { background: #1a1a1a; color: #fff; font-weight: 500; }

    /* ── Main content ── */
    #article-view main { flex: 1; overflow-y: auto; padding: 0; }
    .content-scroll { max-width: 860px; margin: 0 auto; padding: 40px 24px 80px; }

    /* ── Markdown fallback ── */
    #md h1 { font-family: 'Newsreader', serif; font-size: 1.5rem; margin-bottom: 8px; color: #1a1a1a; }
    #md h2 { font-size: 0.95rem; margin: 20px 0 8px; padding-bottom: 5px; border-bottom: 1px solid #eee; }
    #md p { line-height: 1.7; color: #555; margin-bottom: 6px; font-size: 0.9rem; }
    #md a { color: #2563eb; text-decoration: none; }
    #md a:hover { text-decoration: underline; }
    #md hr { border: none; border-top: 1px solid #eee; margin: 4px 0 18px; }
    #no-files { color: #999; padding: 60px 24px; line-height: 2; font-size: 0.9rem; }
    #no-files code { background: #f0f0f0; padding: 2px 6px; border-radius: 4px; font-size: 0.85rem; }

    /* ── Digest header ── */
    .digest-header { margin-bottom: 40px; }
    .digest-date { font-family: 'Newsreader', serif; font-size: 1.8rem; font-weight: 600;
                   color: #1a1a1a; letter-spacing: -0.02em; margin-bottom: 6px; }
    .digest-stats { font-size: 0.8rem; color: #999; }
    .digest-stats span { margin-right: 16px; }

    /* ── Cluster ── */
    #md { display: flex; flex-direction: column; }
    .cluster { margin-bottom: 48px; order: 0; transition: opacity 0.2s; }
    .cluster.dismissed { order: 1; opacity: 0.35; }
    .cluster-head { margin-bottom: 16px; display: flex; align-items: flex-start; gap: 10px; }
    .cluster-head-text { flex: 1; min-width: 0; }
    .cluster-title { font-family: 'Newsreader', serif; font-size: 1.2rem; font-weight: 600;
                     color: #1a1a1a; letter-spacing: -0.01em; line-height: 1.3; }
    .cluster-count { font-size: 0.7rem; color: #999; font-weight: 400; margin-left: 8px; }
    .cluster-synthesis { font-size: 0.88rem; color: #555; line-height: 1.7; margin-top: 8px; }
    .tick-btn { flex-shrink: 0; width: 22px; height: 22px; border-radius: 50%;
                border: 1.5px solid #ddd; background: transparent; cursor: pointer;
                display: flex; align-items: center; justify-content: center;
                font-size: 0.7rem; color: transparent; margin-top: 3px;
                transition: all 0.15s; }
    .tick-btn:hover { border-color: #16a34a; color: #16a34a; }
    .tick-btn.ticked { background: #16a34a; border-color: #16a34a; color: #fff; }
    .digest-header { order: -1; }

    /* ── Article row ── */
    .article-list { display: flex; flex-direction: column; gap: 0; }
    .article-row { padding: 14px 0; border-bottom: 1px solid #f0f0f0;
                   display: flex; align-items: flex-start; gap: 12px; }
    .article-row:last-child { border-bottom: none; }
    .article-favicon { width: 16px; height: 16px; margin-top: 3px; flex-shrink: 0;
                       border-radius: 2px; }
    .article-body { flex: 1; min-width: 0; }
    .article-title-row { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }
    .article-title { color: #1a1a1a; font-size: 0.9rem; font-weight: 500;
                     text-decoration: none; line-height: 1.4; }
    .article-title:hover { color: #2563eb; }
    .article-source { font-size: 0.72rem; color: #bbb; white-space: nowrap; flex-shrink: 0; }
    .article-summary { color: #777; font-size: 0.82rem; line-height: 1.55; margin-top: 4px; }
    .article-meta { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 6px; align-items: center; }
    .topic-tag { font-size: 0.65rem; color: #999; background: #f5f5f5; padding: 2px 8px;
                 border-radius: 3px; font-weight: 500; letter-spacing: 0.02em; }
    .source-badge { font-size: 0.62rem; font-weight: 600; padding: 2px 7px;
                    border-radius: 3px; letter-spacing: 0.03em; text-transform: uppercase; }
    .source-reddit { background: #fff1f0; color: #e25822; }
    .source-rss { background: #f0f4ff; color: #2563eb; }
    .source-bluesky { background: #e8f4ff; color: #0085ff; }
    .source-openjdk { background: #f0fff4; color: #16a34a; }
    .social-links { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 5px; }
    .social-link { font-size: 0.68rem; color: #999; text-decoration: none;
                   padding: 1px 7px; border: 1px solid #e5e5e5; border-radius: 3px;
                   transition: all 0.12s; white-space: nowrap; }
    .social-link:hover { border-color: #bbb; color: #555; }
    .social-link-bluesky:hover { border-color: #0085ff; color: #0085ff; }
    .social-link-reddit:hover  { border-color: #e25822; color: #e25822; }

    /* ── Compact social card (bluesky pure post in cluster) ── */
    .article-row-social { padding: 8px 0; gap: 8px; }
    .social-card-header { display: flex; align-items: center; gap: 6px; margin-bottom: 3px; }
    .social-card-icon { font-size: 0.75rem; flex-shrink: 0; }
    .social-card-author { font-size: 0.72rem; color: #0085ff; text-decoration: none; white-space: nowrap; }
    .social-card-author:hover { text-decoration: underline; }
    .social-card-text { color: #444; font-size: 0.85rem; line-height: 1.5; margin: 0; }

    /* ── Standalone tweets section ── */
    .tweets-section { margin-top: 48px; }
    .tweets-section-title { font-size: 0.68rem; text-transform: uppercase; letter-spacing: .1em;
                            color: #aaa; margin-bottom: 12px; font-weight: 600; }
    .tweets-grid { display: flex; flex-direction: column; gap: 8px; }
    .tweet-card { background: #fff; border: 1px solid #e8e8e8; border-radius: 8px;
                  padding: 10px 14px; }
    .tweet-card .social-card-text { font-size: 0.82rem; }

    /* ── Pipeline view ── */
    #pipeline-view { flex: 1; overflow-y: auto; padding: 32px; }
    #pipeline-view .content-scroll { max-width: 720px; margin: 0 auto; }
    .stats-row { display: flex; gap: 12px; margin-bottom: 28px; flex-wrap: wrap; }
    .stat-card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px;
                 padding: 16px 20px; min-width: 110px; }
    .stat-card .num { font-size: 1.8rem; font-weight: 700; color: #1a1a1a; }
    .stat-card .label { font-size: 0.68rem; color: #999; text-transform: uppercase;
                        letter-spacing: .07em; margin-top: 2px; }
    .stat-success .num { color: #16a34a; }
    .stat-failed .num { color: #dc2626; }
    .stat-scheduled .num { color: #2563eb; }
    .section-title { font-size: 0.68rem; text-transform: uppercase; letter-spacing: .1em;
                     color: #aaa; margin-bottom: 12px; font-weight: 600; }
    .job-row { display: flex; align-items: center; gap: 12px; padding: 12px 16px;
               background: #fff; border: 1px solid #e5e5e5; border-radius: 8px;
               margin-bottom: 6px; }
    .job-date { font-size: 0.8rem; color: #888; font-family: 'SF Mono', monospace; min-width: 150px; }
    .job-dur { font-size: 0.75rem; color: #aaa; margin-left: auto; }
    .job-link { font-size: 0.72rem; color: #2563eb; text-decoration: none;
                padding: 3px 10px; border: 1px solid #dbeafe; border-radius: 4px; }
    .job-link:hover { background: #eff6ff; }
    .chip { font-size: 0.68rem; font-weight: 600; padding: 3px 9px;
            border-radius: 4px; white-space: nowrap; }
    .state-SUCCEEDED { background: #dcfce7; color: #16a34a; }
    .state-FAILED { background: #fee2e2; color: #dc2626; }
    .state-PROCESSING { background: #fef9c3; color: #ca8a04; }
    .state-ENQUEUED { background: #dbeafe; color: #2563eb; }
    .state-SCHEDULED { background: #f5f5f5; color: #888; }
    .offline-msg { color: #999; padding: 32px 0; font-size: 0.9rem; }
    .offline-msg a { color: #2563eb; }
    .refresh-btn { background: #f5f5f5; border: 1px solid #e5e5e5; border-radius: 6px;
                   color: #888; font-size: 0.75rem; padding: 3px 10px;
                   cursor: pointer; margin-left: 8px; }
    .refresh-btn:hover { background: #eee; }

    .debug-panel { margin-top: 60px; border-top: 1px solid #e5e5e5; padding-top: 20px; }
    .debug-toggle { background: none; border: none; cursor: pointer; font-size: 0.75rem; color: #bbb; padding: 0; }
    .debug-toggle:hover { color: #888; }
    .debug-list { margin-top: 12px; display: none; }
    .debug-list.open { display: block; }
    .debug-item { padding: 6px 0; border-bottom: 1px solid #f5f5f5; font-size: 0.78rem; color: #aaa; display: flex; gap: 8px; align-items: baseline; }
    .debug-reason { font-size: 0.65rem; background: #f5f5f5; color: #999; padding: 1px 6px; border-radius: 3px; white-space: nowrap; }
    .debug-title { color: #bbb; }
    .debug-url { color: #c5d5f5; font-size: 0.7rem; }
  </style>
</head>
<body>
  <header>
    <h1>JVM Daily</h1>
    <button class="tab active" onclick="showTab('articles', this)">Digest</button>
    <button class="tab"        onclick="showTab('pipeline', this)">Pipeline</button>
    <span id="meta"></span>
  </header>

  <div id="article-view" class="view">
    <aside id="date-sidebar"><h2>Archive</h2></aside>
    <main>
      <div class="content-scroll">
        <div id="md"></div>
        <div id="no-files" class="hidden">
          No output files found.<br>
          Run: <code>./gradlew run --args="pipeline"</code>
        </div>
      </div>
    </main>
  </div>

  <div id="pipeline-view" class="view hidden">
    <div class="content-scroll" id="pipeline-content"><span style="color:#999">Loading...</span></div>
  </div>

  <script>
  const JOBRUNR_DASHBOARD = location.hostname + ':8000';

  function showTab(name, btn) {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById('article-view').classList.toggle('hidden', name !== 'articles');
    document.getElementById('pipeline-view').classList.toggle('hidden', name !== 'pipeline');
    if (name === 'pipeline') loadPipeline();
  }

  function esc(s) {
    return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;')
      .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  }

  function getDomain(url) {
    try { return new URL(url).hostname.replace('www.', ''); } catch { return ''; }
  }

  function faviconUrl(url) {
    const d = getDomain(url);
    if (!d) return '';
    return 'https://www.google.com/s2/favicons?domain=' + d + '&sz=32';
  }

  function sourceBadge(a) {
    if (a.sourceType === 'reddit') return '<span class="source-badge source-reddit">Reddit</span>';
    if (a.sourceType === 'bluesky') return '<span class="source-badge source-bluesky">Bluesky</span>';
    if (a.sourceType === 'openjdk_mail') return '<span class="source-badge source-openjdk">OpenJDK</span>';
    return '<span class="source-badge source-rss">RSS</span>';
  }

  function fmtDigestDate(dateStr) {
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('en-US', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
  }

  async function loadDate(date, btn, pushState = true) {
    document.querySelectorAll('.date-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById('no-files').classList.add('hidden');
    if (pushState) history.pushState({date}, '', '?date=' + date);

    const jsonRes = await fetch('/api/daily/' + date);
    if (jsonRes.ok) {
      const data = await jsonRes.json();
      renderClusters(data);
      document.getElementById('meta').textContent = data.totalArticles + ' articles';
    } else {
      const mdRes = await fetch('/output/jvm-daily-' + date + '.md');
      if (!mdRes.ok) { document.getElementById('md').innerHTML = ''; return; }
      const md = await mdRes.text();
      document.getElementById('md').innerHTML = marked.parse(md);
      const m = md.match(/Articles: (\d+)/);
      document.getElementById('meta').textContent = m ? m[1] + ' articles' : '';
    }
  }

  function renderClusters(data) {
    const clusters = data.clusters; // order is authoritative from backend (major → normal → Releases)
    const clusterCount = clusters.reduce((s, c) => s + c.articles.length, 0);
    const unclusteredCount = (data.unclustered || []).length;

    let html = `<div class="digest-header">
      <div class="digest-date">${fmtDigestDate(data.date)}</div>
      <div class="digest-stats">
        <span>${data.totalArticles} articles</span>
        <span>${clusters.length} topics</span>
        <span>${clusterCount} clustered</span>
      </div>
    </div>`;

    function isSocialPost(a) {
      return a.sourceType === 'bluesky' && (a.url || '').includes('bsky.app');
    }

    function extractTweetText(title) {
      const m = title.match(/^\[.*?\]\s*([\s\S]+)/);
      return m ? m[1] : title;
    }

    function socialLinksHtml(links) {
      if (!links || !links.length) return '';
      const items = links.map(l => {
        const label = l.source === 'bluesky' ? '🦋 ' + esc(l.handle || 'Bluesky') : l.source === 'reddit' ? '↗ ' + esc(l.handle || 'Reddit') : '↗ ' + esc(l.handle || l.source);
        return `<a class="social-link social-link-${esc(l.source)}" href="${esc(l.url)}" target="_blank" rel="noopener">${label}</a>`;
      }).join('');
      return `<div class="social-links">${items}</div>`;
    }

    function socialCardHtml(a) {
      const topics = (a.topics || []).map(t => `<span class="topic-tag">${esc(t)}</span>`).join('');
      const handle = a.handle ? '@' + a.handle : 'Bluesky';
      const text = extractTweetText(a.title);
      return `<div class="article-row article-row-social">
        <div class="article-body">
          <div class="social-card-header">
            <span class="social-card-icon">🦋</span>
            <a class="social-card-author" href="${esc(a.url || '#')}" target="_blank" rel="noopener">${esc(handle)}</a>
          </div>
          <p class="social-card-text">${esc(text)}</p>
          <div class="article-meta">${sourceBadge(a)}${topics}</div>
        </div>
      </div>`;
    }

    function articleHtml(a, clusterSize) {
      if (clusterSize > 1 && isSocialPost(a)) return socialCardHtml(a);
      const domain = getDomain(a.url || '');
      const favicon = faviconUrl(a.url || '');
      const topics = (a.topics || []).map(t => `<span class="topic-tag">${esc(t)}</span>`).join('');
      const faviconImg = favicon
        ? `<img class="article-favicon" src="${favicon}" alt="" loading="lazy" onerror="this.style.display='none'">`
        : '';
      return `<div class="article-row">
        ${faviconImg}
        <div class="article-body">
          <div class="article-title-row">
            <a class="article-title" href="${esc(a.url || '#')}" target="_blank" rel="noopener">${esc(a.title)}</a>
            <span class="article-source">${esc(domain)}</span>
          </div>
          <p class="article-summary">${esc(a.summary)}</p>
          <div class="article-meta">${sourceBadge(a)}${topics}</div>
          ${socialLinksHtml(a.socialLinks)}
        </div>
      </div>`;
    }

    function clusterHtml(key, titleHtml, synthesisHtml, artsHtml, extraClass) {
      return `<div class="cluster${extraClass ? ' ' + extraClass : ''}" data-key="${esc(key)}">
        <div class="cluster-head">
          <div class="cluster-head-text">
            ${titleHtml}
            ${synthesisHtml}
          </div>
          <button class="tick-btn" title="Dismiss" onclick="toggleDismiss(this)">✓</button>
        </div>
        <div class="article-list">${artsHtml}</div>
      </div>`;
    }

    const standaloneTweets = [];

    for (const cluster of clusters) {
      const arts = [...cluster.articles].sort((a, b) => b.engagementScore - a.engagementScore);
      if (arts.length === 1 && isSocialPost(arts[0])) {
        standaloneTweets.push(arts[0]);
        continue;
      }
      html += clusterHtml(
        cluster.title,
        `<div class="cluster-title">${esc(cluster.title)}<span class="cluster-count">${arts.length} articles</span></div>`,
        `<div class="cluster-synthesis">${marked.parse(cluster.summary)}</div>`,
        arts.map(a => articleHtml(a, arts.length)).join(''),
        ''
      );
    }

    if (data.unclustered && data.unclustered.length > 0) {
      const allUnclustered = [...data.unclustered].sort((a, b) => b.engagementScore - a.engagementScore);
      const unclusteredNormal = allUnclustered.filter(a => !isSocialPost(a));
      allUnclustered.filter(isSocialPost).forEach(a => standaloneTweets.push(a));

      if (unclusteredNormal.length > 0) {
        html += clusterHtml(
          '__unclustered__',
          `<div class="cluster-title">Other<span class="cluster-count">${unclusteredNormal.length} articles</span></div>`,
          '',
          unclusteredNormal.map(a => articleHtml(a, unclusteredNormal.length)).join(''),
          'unclustered'
        );
      }
    }

    if (standaloneTweets.length > 0) {
      const cards = standaloneTweets.map(a => {
        const handle = a.handle ? '@' + a.handle : 'Bluesky';
        const text = extractTweetText(a.title);
        const topics = (a.topics || []).map(t => `<span class="topic-tag">${esc(t)}</span>`).join('');
        return `<div class="tweet-card">
          <div class="social-card-header">
            <span class="social-card-icon">🦋</span>
            <a class="social-card-author" href="${esc(a.url || '#')}" target="_blank" rel="noopener">${esc(handle)}</a>
          </div>
          <p class="social-card-text">${esc(text)}</p>
          <div class="article-meta">${topics}</div>
        </div>`;
      }).join('');
      html += `<div class="tweets-section">
        <div class="tweets-section-title">Tweets</div>
        <div class="tweets-grid">${cards}</div>
      </div>`;
    }

    if (data.debug && data.debug.length > 0) {
      const items = data.debug.map(d =>
        `<div class="debug-item">
          <span class="debug-reason">${esc(d.reason)}</span>
          <span class="debug-title">${esc(d.title)}</span>
          ${d.url ? `<a class="debug-url" href="${esc(d.url)}" target="_blank">${esc(getDomain(d.url))}</a>` : ''}
        </div>`
      ).join('');
      html += `<div class="debug-panel">
        <button class="debug-toggle" data-count="${data.debug.length}" onclick="toggleDebug(this)">▼ Debug (${data.debug.length} rejected)</button>
        <div class="debug-list">${items}</div>
      </div>`;
    }

    document.getElementById('md').innerHTML = html;
    applyDismissed(data.date);
  }

  function dismissedKey(date) { return 'jvm-daily-dismissed-' + date; }
  function getDismissed(date) {
    try { return new Set(JSON.parse(localStorage.getItem(dismissedKey(date)) || '[]')); }
    catch { return new Set(); }
  }
  function saveDismissed(date, set) {
    localStorage.setItem(dismissedKey(date), JSON.stringify([...set]));
  }

  let _currentDate = '';

  function applyDismissed(date) {
    _currentDate = date;
    const dismissed = getDismissed(date);
    document.querySelectorAll('.cluster[data-key]').forEach(el => {
      const key = el.dataset.key;
      const btn = el.querySelector('.tick-btn');
      if (dismissed.has(key)) {
        el.classList.add('dismissed');
        btn && btn.classList.add('ticked');
      }
    });
  }

  function toggleDebug(btn) {
    const list = btn.nextElementSibling;
    list.classList.toggle('open');
    btn.textContent = list.classList.contains('open') ? '▲ Debug' : '▼ Debug (' + btn.dataset.count + ' rejected)';
  }

  function toggleDismiss(btn) {
    const cluster = btn.closest('.cluster');
    const key = cluster.dataset.key;
    const dismissed = getDismissed(_currentDate);
    if (dismissed.has(key)) {
      dismissed.delete(key);
      cluster.classList.remove('dismissed');
      btn.classList.remove('ticked');
    } else {
      dismissed.add(key);
      cluster.classList.add('dismissed');
      btn.classList.add('ticked');
    }
    saveDismissed(_currentDate, dismissed);
  }

  async function initArticles() {
    const files = await fetch('/api/files').then(r => r.json());
    const sidebar = document.getElementById('date-sidebar');
    if (!files.length) { document.getElementById('no-files').classList.remove('hidden'); return; }
    const requestedDate = new URLSearchParams(location.search).get('date');
    const btnMap = {};
    files.forEach((f, i) => {
      const date = f.replace('jvm-daily-', '').replace('.md', '');
      const btn = document.createElement('button');
      btn.className = 'date-btn';
      const d = new Date(date + 'T00:00:00');
      btn.textContent = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
      btn.onclick = () => loadDate(date, btn);
      sidebar.appendChild(btn);
      btnMap[date] = btn;
    });
    const initialDate = (requestedDate && btnMap[requestedDate]) ? requestedDate : files[0].replace('jvm-daily-', '').replace('.md', '');
    loadDate(initialDate, btnMap[initialDate], false);

    window.addEventListener('popstate', e => {
      const d = e.state?.date || files[0].replace('jvm-daily-', '').replace('.md', '');
      if (btnMap[d]) loadDate(d, btnMap[d], false);
    });
  }

  function stateChip(state) {
    const labels = { SUCCEEDED:'Passed', FAILED:'Failed',
                     PROCESSING:'Running', ENQUEUED:'Queued', SCHEDULED:'Scheduled' };
    return `<span class="chip state-${state}">${labels[state] || state}</span>`;
  }

  function fmtDate(iso) {
    if (!iso) return '\u2014';
    return new Date(iso).toISOString().replace('T', ' ').slice(0, 16) + ' UTC';
  }

  function fmtDur(a, b) {
    if (!a || !b) return '';
    const sec = Math.round((new Date(b) - new Date(a)) / 1000);
    if (sec < 60) return sec + 's';
    return Math.floor(sec / 60) + 'm ' + (sec % 60) + 's';
  }

  async function loadPipeline() {
    const el = document.getElementById('pipeline-content');
    const [statsRes, succRes, failRes] = await Promise.all([
      fetch('/api/jobrunr/stats'),
      fetch('/api/jobrunr/jobs?state=SUCCEEDED&pageSize=15'),
      fetch('/api/jobrunr/jobs?state=FAILED&pageSize=5'),
    ]);
    if (!statsRes.ok) {
      el.innerHTML = `<div class="offline-msg">
        Pipeline scheduler is offline.<br>
        Start the app with no arguments to enable the scheduler.<br><br>
        <a href="http://${JOBRUNR_DASHBOARD}/dashboard" target="_blank">Open dashboard</a>
      </div>`;
      return;
    }
    const stats = await statsRes.json();
    const succ = (await succRes.json()).items || [];
    const fail = (await failRes.json()).items || [];
    const jobs = [...fail, ...succ].sort((a, b) =>
      new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 20);
    el.innerHTML = `
      <div class="stats-row">
        <div class="stat-card stat-success"><div class="num">${stats.allTimeSucceeded ?? stats.succeeded ?? '\u2014'}</div><div class="label">Total runs</div></div>
        <div class="stat-card stat-failed"><div class="num">${stats.failed ?? 0}</div><div class="label">Failed</div></div>
        <div class="stat-card stat-scheduled"><div class="num">${stats.scheduled ?? 0}</div><div class="label">Scheduled</div></div>
      </div>
      <div class="section-title">Recent runs <button class="refresh-btn" onclick="loadPipeline()">Refresh</button></div>
      ${jobs.length === 0
        ? '<div style="color:#999;padding:20px 0">No runs yet.</div>'
        : jobs.map(j => `<div class="job-row">
            <span class="job-date">${fmtDate(j.createdAt)}</span>
            ${stateChip(j.state)}
            <span class="job-dur">${fmtDur(j.createdAt, j.updatedAt)}</span>
            <a class="job-link" href="http://${JOBRUNR_DASHBOARD}/dashboard#/jobs/${j.id}" target="_blank">Logs</a>
          </div>`).join('')}`;
  }

  initArticles();
  </script>
</body>
</html>"""

# ──────────────────────────────────────────────────────────────────────────────

def jobrunr_get(path):
    url = f"{JOBRUNR_URL}/api/{path}"
    req = urllib.request.Request(url)
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            return resp.read(), resp.status
    except urllib.error.HTTPError as e:
        return e.read(), e.code
    except Exception:
        return None, 503


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(f"  {self.command} {self.path}")

    def send_bytes(self, code, content_type, body):
        data = body.encode() if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", len(data))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        p = self.path.split("?")[0]
        qs = self.path[len(p):]  # query string including '?'

        if p in ("/", ""):
            self.send_bytes(200, "text/html", HTML)

        elif p == "/api/files":
            files = sorted([f.name for f in OUTPUT_DIR.glob("jvm-daily-*.md")], reverse=True)
            self.send_bytes(200, "application/json", json.dumps(files))

        elif p.startswith("/api/daily/"):
            date_seg = p[len("/api/daily/"):]
            if not DATE_PATTERN.match(date_seg):
                self.send_bytes(400, "application/json", json.dumps({"error": "invalid date"}))
                return
            path = OUTPUT_DIR / f"daily-{date_seg}.json"
            if not path.exists() or not str(path.resolve()).startswith(str(OUTPUT_DIR.resolve())):
                self.send_bytes(404, "application/json", json.dumps({"error": "not found"}))
                return
            self.send_bytes(200, "application/json", path.read_bytes())

        elif p.startswith("/output/"):
            name = Path(p[len("/output/"):]).name
            path = OUTPUT_DIR / name
            if not path.exists() or not str(path.resolve()).startswith(str(OUTPUT_DIR)):
                self.send_bytes(404, "text/plain", "Not found")
                return
            self.send_bytes(200, "text/plain; charset=utf-8", path.read_text())

        elif p.startswith("/api/jobrunr/"):
            # Proxy to JobRunr REST API
            sub = p[len("/api/jobrunr/"):]
            body, status = jobrunr_get(sub + qs)
            if body is None:
                self.send_bytes(503, "application/json",
                                json.dumps({"error": f"JobRunr unreachable at {JOBRUNR_URL}"}))
            else:
                self.send_bytes(status, "application/json", body)

        else:
            self.send_bytes(404, "text/plain", "Not found")


if __name__ == "__main__":
    OUTPUT_DIR.mkdir(exist_ok=True)
    print(f"JVM Daily Viewer  →  http://localhost:{PORT}")
    print(f"Output dir  : {OUTPUT_DIR}")
    print(f"JobRunr URL : {JOBRUNR_URL}")
    HTTPServer(("", PORT), Handler).serve_forever()
