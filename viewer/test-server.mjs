/**
 * Minimal test server that mimics the Ktor REST API using fixture data.
 * Used by Playwright tests for reproducible, offline testing.
 */
import { createServer } from 'http'
import { readFileSync, readdirSync, existsSync } from 'fs'
import { join } from 'path'

const PORT = 18888
const FIXTURE_DIR = join(import.meta.dirname, 'test-fixtures')
const VIEWER_HTML = join(import.meta.dirname, 'index.html')

const server = createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`)
  const path = url.pathname

  if (req.method === 'GET' && path === '/') {
    res.writeHead(200, { 'Content-Type': 'text/html' })
    res.end(readFileSync(VIEWER_HTML))
    return
  }

  if (req.method === 'GET' && path === '/api/dates') {
    const dates = readdirSync(FIXTURE_DIR)
      .filter(f => f.startsWith('daily-') && f.endsWith('.json'))
      .map(f => f.replace('daily-', '').replace('.json', ''))
      .sort()
      .reverse()
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify(dates))
    return
  }

  if (req.method === 'GET' && path === '/api/files') {
    const files = readdirSync(FIXTURE_DIR)
      .filter(f => f.startsWith('jvm-daily-') && f.endsWith('.md'))
      .sort()
      .reverse()
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify(files))
    return
  }

  const dailyMatch = path.match(/^\/api\/daily\/(\d{4}-\d{2}-\d{2})$/)
  if (req.method === 'GET' && dailyMatch) {
    const file = join(FIXTURE_DIR, `daily-${dailyMatch[1]}.json`)
    if (existsSync(file)) {
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(readFileSync(file))
    } else {
      res.writeHead(404, { 'Content-Type': 'application/json' })
      res.end('{"error":"not found"}')
    }
    return
  }

  if (req.method === 'GET' && path === '/api/pipeline') {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({
      stats: { succeeded: 42, failed: 1, scheduled: 1 },
      recentJobs: [
        { id: 'job-1', state: 'SUCCEEDED', createdAt: '2026-03-23T06:00:00Z', updatedAt: '2026-03-23T06:05:00Z' },
        { id: 'job-2', state: 'SUCCEEDED', createdAt: '2026-03-22T06:00:00Z', updatedAt: '2026-03-22T06:04:30Z' },
        { id: 'job-3', state: 'FAILED', createdAt: '2026-03-21T06:00:00Z', updatedAt: '2026-03-21T06:03:00Z' },
      ],
    }))
    return
  }

  if (req.method === 'POST' && path === '/api/ingest') {
    const auth = req.headers['authorization'] || ''
    if (auth !== 'Bearer test-fixture-key') {
      res.writeHead(401, { 'Content-Type': 'application/json' })
      res.end('{"error":"unauthorized"}')
      return
    }
    let body = ''
    req.on('data', c => body += c)
    req.on('end', () => {
      const articles = JSON.parse(body)
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ saved: articles.length }))
    })
    return
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' })
  res.end('Not found')
})

server.listen(PORT, () => console.log(`Test server on :${PORT}`))
