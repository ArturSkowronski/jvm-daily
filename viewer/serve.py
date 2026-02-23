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
import sys
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.parent
OUTPUT_DIR   = Path(os.environ.get("OUTPUT_DIR", str(PROJECT_ROOT / "output")))
PORT         = int(sys.argv[1]) if len(sys.argv) > 1 else 8888
JOBRUNR_URL  = os.environ.get("JOBRUNR_URL", "http://localhost:8000")

# ──────────────────────────────────────────────────────────────────────────────

HTML = r"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>JVM Daily</title>
  <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
           background: #0d1117; color: #e6edf3;
           display: flex; flex-direction: column; height: 100vh; overflow: hidden; }

    /* ── Header ── */
    header { background: #161b22; border-bottom: 1px solid #30363d;
             padding: 0 20px; display: flex; align-items: center; height: 48px; flex-shrink: 0; }
    header h1 { font-size: 1.05rem; color: #58a6ff; margin-right: 24px; }
    .tab       { background: transparent; border: none; border-bottom: 2px solid transparent;
                 color: #8b949e; font-size: 0.9rem; padding: 0 4px; height: 48px;
                 margin-right: 20px; cursor: pointer; }
    .tab.active { color: #e6edf3; border-bottom-color: #58a6ff; }
    #meta { margin-left: auto; font-size: 0.8rem; color: #8b949e; }

    /* ── Layout ── */
    .view   { display: flex; flex: 1; overflow: hidden; }
    .hidden { display: none !important; }

    /* ── Date sidebar ── */
    aside { width: 160px; flex-shrink: 0; background: #161b22;
            border-right: 1px solid #30363d; overflow-y: auto;
            padding: 10px 8px; display: flex; flex-direction: column; gap: 5px; }
    aside h2 { font-size: 0.65rem; text-transform: uppercase; letter-spacing: .08em;
               color: #8b949e; padding: 2px 8px 8px; }
    .date-btn { background: transparent; border: 1px solid #30363d; border-radius: 6px;
                color: #b1bac4; font-size: 0.82rem; padding: 7px 10px; cursor: pointer;
                text-align: left; }
    .date-btn:hover  { background: #21262d; color: #e6edf3; }
    .date-btn.active { background: #1f3a5f; color: #58a6ff;
                       border-color: #58a6ff; font-weight: 600; }

    /* ── Article content ── */
    #article-view main { flex: 1; overflow-y: auto; padding: 28px 40px 64px; }
    #md h1 { color: #58a6ff; font-size: 1.6rem; margin-bottom: 8px; }
    #md h2 { color: #e6edf3; font-size: 1rem; margin: 22px 0 8px;
             padding-bottom: 5px; border-bottom: 1px solid #21262d; }
    #md p  { line-height: 1.7; color: #b1bac4; margin-bottom: 6px; }
    #md strong { color: #e6edf3; }
    #md a  { color: #58a6ff; text-decoration: none; }
    #md a:hover { text-decoration: underline; }
    #md hr { border: none; border-top: 1px solid #21262d; margin: 4px 0 18px; }
    #no-files { color: #8b949e; padding: 60px 40px; line-height: 2; }
    #no-files code { background: #21262d; padding: 2px 6px; border-radius: 4px; }

    /* ── Pipeline view ── */
    #pipeline-view { flex: 1; overflow-y: auto; padding: 24px 32px 64px; }
    .stats-row { display: flex; gap: 12px; margin-bottom: 24px; flex-wrap: wrap; }
    .stat-card { background: #161b22; border: 1px solid #30363d; border-radius: 8px;
                 padding: 14px 20px; min-width: 110px; }
    .stat-card .num   { font-size: 1.8rem; font-weight: 700; }
    .stat-card .label { font-size: 0.72rem; color: #8b949e; text-transform: uppercase;
                        letter-spacing: .07em; margin-top: 2px; }
    .stat-success { border-color: #2ea04322; }
    .stat-success .num { color: #3fb950; }
    .stat-failed  { border-color: #f8514922; }
    .stat-failed  .num { color: #f85149; }
    .stat-scheduled .num { color: #58a6ff; }

    .section-title { font-size: 0.7rem; text-transform: uppercase; letter-spacing: .08em;
                     color: #8b949e; margin-bottom: 10px; }
    .job-row { display: flex; align-items: center; gap: 12px; padding: 11px 16px;
               background: #161b22; border: 1px solid #30363d; border-radius: 8px;
               margin-bottom: 7px; }
    .job-date { font-size: 0.82rem; color: #8b949e; font-family: monospace; min-width: 160px; }
    .job-dur  { font-size: 0.75rem; color: #8b949e; margin-left: auto; }
    .job-link { font-size: 0.75rem; color: #58a6ff; text-decoration: none;
                padding: 3px 10px; border: 1px solid #58a6ff44; border-radius: 4px; }
    .job-link:hover { background: #58a6ff11; }

    .chip { font-size: 0.7rem; font-weight: 600; padding: 2px 9px;
            border-radius: 10px; white-space: nowrap; }
    .state-SUCCEEDED  { background: #1a3a1a; color: #3fb950; }
    .state-FAILED     { background: #3a1a1a; color: #f85149; }
    .state-PROCESSING { background: #3a3a1a; color: #d29922; }
    .state-ENQUEUED   { background: #1a2a3a; color: #58a6ff; }
    .state-SCHEDULED  { background: #21262d; color: #8b949e; }

    .offline-msg { color: #8b949e; padding: 32px 0; font-size: 0.9rem; }
    .offline-msg a { color: #58a6ff; }
    .refresh-btn { background: #21262d; border: 1px solid #30363d; border-radius: 6px;
                   color: #8b949e; font-size: 0.78rem; padding: 3px 10px;
                   cursor: pointer; margin-left: 8px; }
  </style>
</head>
<body>
  <header>
    <h1>☕ JVM Daily</h1>
    <button class="tab active" onclick="showTab('articles', this)">Articles</button>
    <button class="tab"        onclick="showTab('pipeline', this)">Pipeline</button>
    <span id="meta"></span>
  </header>

  <!-- Articles -->
  <div id="article-view" class="view">
    <aside id="date-sidebar"><h2>Dates</h2></aside>
    <main>
      <div id="md"></div>
      <div id="no-files" class="hidden">
        No output files found.<br>
        Run: <code>./gradlew run --args="pipeline"</code>
      </div>
    </main>
  </div>

  <!-- Pipeline -->
  <div id="pipeline-view" class="view hidden">
    <div id="pipeline-content"><span style="color:#8b949e">Loading…</span></div>
  </div>

  <script>
  const JOBRUNR_DASHBOARD = location.hostname + ':8000';

  // ── Tabs ──────────────────────────────────────────────────────────────────
  function showTab(name, btn) {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById('article-view').classList.toggle('hidden', name !== 'articles');
    document.getElementById('pipeline-view').classList.toggle('hidden', name !== 'pipeline');
    if (name === 'pipeline') loadPipeline();
  }

  // ── Articles ──────────────────────────────────────────────────────────────
  async function loadArticle(filename, btn) {
    document.querySelectorAll('.date-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    const md = await fetch('/output/' + filename).then(r => r.text());
    document.getElementById('md').innerHTML = marked.parse(md);
    const m = md.match(/Articles: (\d+)/);
    document.getElementById('meta').textContent = m ? m[1] + ' articles' : '';
  }

  async function initArticles() {
    const files = await fetch('/api/files').then(r => r.json());
    const sidebar = document.getElementById('date-sidebar');
    if (!files.length) { document.getElementById('no-files').classList.remove('hidden'); return; }
    files.forEach((f, i) => {
      const date = f.replace('jvm-daily-', '').replace('.md', '');
      const btn  = document.createElement('button');
      btn.className = 'date-btn';
      btn.textContent = date;
      btn.onclick = () => loadArticle(f, btn);
      sidebar.appendChild(btn);
      if (i === 0) loadArticle(f, btn);
    });
  }

  // ── Pipeline ──────────────────────────────────────────────────────────────
  function stateChip(state) {
    const labels = { SUCCEEDED:'✓ success', FAILED:'✗ failed',
                     PROCESSING:'⟳ running', ENQUEUED:'· queued', SCHEDULED:'◷ scheduled' };
    return `<span class="chip state-${state}">${labels[state] || state}</span>`;
  }

  function fmtDate(iso) {
    if (!iso) return '—';
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
        JobRunr dashboard is offline or not running.<br>
        Start the app with no arguments to enable the scheduler.<br><br>
        <a href="http://${JOBRUNR_DASHBOARD}/dashboard" target="_blank">
          Open JobRunr dashboard ↗
        </a>
      </div>`;
      return;
    }

    const stats = await statsRes.json();
    const succ  = (await succRes.json()).items || [];
    const fail  = (await failRes.json()).items || [];
    const jobs  = [...fail, ...succ].sort((a, b) =>
      new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 20);

    el.innerHTML = `
      <div class="stats-row">
        <div class="stat-card stat-success">
          <div class="num">${stats.allTimeSucceeded ?? stats.succeeded ?? '—'}</div>
          <div class="label">All-time runs</div>
        </div>
        <div class="stat-card stat-failed">
          <div class="num">${stats.failed ?? 0}</div>
          <div class="label">Failed</div>
        </div>
        <div class="stat-card stat-scheduled">
          <div class="num">${stats.scheduled ?? 0}</div>
          <div class="label">Scheduled</div>
        </div>
      </div>

      <div class="section-title">
        Recent runs
        <button class="refresh-btn" onclick="loadPipeline()">↺ refresh</button>
      </div>
      ${jobs.length === 0
        ? '<div style="color:#8b949e;padding:20px 0">No jobs yet — pipeline hasn\'t run.</div>'
        : jobs.map(j => `
          <div class="job-row">
            <span class="job-date">${fmtDate(j.createdAt)}</span>
            ${stateChip(j.state)}
            <span class="job-dur">${fmtDur(j.createdAt, j.updatedAt)}</span>
            <a class="job-link"
               href="http://${JOBRUNR_DASHBOARD}/dashboard#/jobs/${j.id}"
               target="_blank">logs ↗</a>
          </div>`).join('')}
    `;
  }

  // ── Init ──────────────────────────────────────────────────────────────────
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
