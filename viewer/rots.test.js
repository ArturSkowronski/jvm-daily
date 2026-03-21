/**
 * Tests for ROTS (Rest of the Story) localStorage logic.
 * Run: node viewer/rots.test.js
 */

const assert = require('node:assert/strict');
const { describe, it, beforeEach } = require('node:test');

// ── Mock localStorage ──
const storage = {};
const localStorage = {
  getItem(k) { return storage[k] ?? null; },
  setItem(k, v) { storage[k] = v; },
  removeItem(k) { delete storage[k]; },
  clear() { Object.keys(storage).forEach(k => delete storage[k]); },
};

// ── Extract pure ROTS functions (mirroring serve.py JS) ──
const ROTS_KEY = 'jvm-daily-rots';

function getRots() {
  try { return JSON.parse(localStorage.getItem(ROTS_KEY) || '{}'); }
  catch { return {}; }
}

function saveRots(obj) {
  const clean = {};
  for (const [d, arr] of Object.entries(obj)) { if (arr.length) clean[d] = arr; }
  localStorage.setItem(ROTS_KEY, JSON.stringify(clean));
}

function toggleRots(date, title) {
  const rots = getRots();
  const arr = rots[date] || [];
  const idx = arr.indexOf(title);
  if (idx >= 0) arr.splice(idx, 1); else arr.push(title);
  rots[date] = arr;
  saveRots(rots);
}

function isBookmarked(date, title) {
  const rots = getRots();
  return (rots[date] || []).includes(title);
}

function rotsCount() {
  const rots = getRots();
  return Object.values(rots).reduce((s, a) => s + a.length, 0);
}

// ── Classification logic (mirrors applyRotsSection) ──
function classifyClusters(clusterKeys, bookmarkedSet, dismissedSet) {
  const rotsTopic = [], rotsRelease = [];
  const archTopic = [], archRelease = [];
  const normal = [];
  for (const { key, isRelease } of clusterKeys) {
    if (bookmarkedSet.has(key)) {
      (isRelease ? rotsRelease : rotsTopic).push(key);
    } else if (dismissedSet.has(key)) {
      (isRelease ? archRelease : archTopic).push(key);
    } else {
      normal.push(key);
    }
  }
  return { rotsTopic, rotsRelease, archTopic, archRelease, normal };
}

// ── URL encoding/decoding (mirrors shareRotsUrl / renderSharedRots) ──
function encodeRotsUrl(rotsObj, clustersByDate) {
  const parts = [];
  for (const [date, titles] of Object.entries(rotsObj).sort(([a], [b]) => b.localeCompare(a))) {
    const clusters = clustersByDate[date] || [];
    const indices = [];
    clusters.forEach((c, idx) => { if (titles.includes(c.title)) indices.push(idx); });
    if (indices.length) parts.push(date + ':' + indices.join(','));
  }
  return parts.join('|');
}

function decodeRotsUrl(encoded) {
  return encoded.split('|').map(seg => {
    const [date, idxStr] = seg.split(':');
    return { date, indices: idxStr.split(',').map(Number) };
  });
}

// ── Tests ──

describe('getRots / saveRots', () => {
  beforeEach(() => localStorage.clear());

  it('returns empty object when no data', () => {
    assert.deepEqual(getRots(), {});
  });

  it('returns empty object on corrupt JSON', () => {
    localStorage.setItem(ROTS_KEY, '{broken');
    assert.deepEqual(getRots(), {});
  });

  it('round-trips data correctly', () => {
    const data = { '2026-03-21': ['Kotlin 2.2', 'Spring Boot'] };
    saveRots(data);
    assert.deepEqual(getRots(), data);
  });

  it('prunes empty date arrays on save', () => {
    saveRots({ '2026-03-21': ['A'], '2026-03-20': [] });
    assert.deepEqual(getRots(), { '2026-03-21': ['A'] });
  });
});

describe('toggleRots', () => {
  beforeEach(() => localStorage.clear());

  it('adds a bookmark', () => {
    toggleRots('2026-03-21', 'Kotlin 2.2');
    assert.deepEqual(getRots(), { '2026-03-21': ['Kotlin 2.2'] });
  });

  it('removes a bookmark on second toggle', () => {
    toggleRots('2026-03-21', 'Kotlin 2.2');
    toggleRots('2026-03-21', 'Kotlin 2.2');
    assert.deepEqual(getRots(), {});
  });

  it('handles multiple titles per date', () => {
    toggleRots('2026-03-21', 'A');
    toggleRots('2026-03-21', 'B');
    toggleRots('2026-03-21', 'C');
    assert.deepEqual(getRots()['2026-03-21'], ['A', 'B', 'C']);
  });

  it('handles multiple dates', () => {
    toggleRots('2026-03-21', 'A');
    toggleRots('2026-03-20', 'B');
    assert.deepEqual(getRots(), { '2026-03-21': ['A'], '2026-03-20': ['B'] });
  });

  it('removing last title from a date prunes that date', () => {
    toggleRots('2026-03-21', 'A');
    toggleRots('2026-03-20', 'B');
    toggleRots('2026-03-21', 'A');
    assert.deepEqual(getRots(), { '2026-03-20': ['B'] });
  });
});

describe('isBookmarked', () => {
  beforeEach(() => localStorage.clear());

  it('returns false when nothing bookmarked', () => {
    assert.equal(isBookmarked('2026-03-21', 'X'), false);
  });

  it('returns true for bookmarked title', () => {
    toggleRots('2026-03-21', 'X');
    assert.equal(isBookmarked('2026-03-21', 'X'), true);
  });

  it('returns false for different date', () => {
    toggleRots('2026-03-21', 'X');
    assert.equal(isBookmarked('2026-03-20', 'X'), false);
  });

  it('returns false after un-bookmark', () => {
    toggleRots('2026-03-21', 'X');
    toggleRots('2026-03-21', 'X');
    assert.equal(isBookmarked('2026-03-21', 'X'), false);
  });
});

describe('rotsCount', () => {
  beforeEach(() => localStorage.clear());

  it('returns 0 when empty', () => {
    assert.equal(rotsCount(), 0);
  });

  it('counts across all dates', () => {
    toggleRots('2026-03-21', 'A');
    toggleRots('2026-03-21', 'B');
    toggleRots('2026-03-20', 'C');
    assert.equal(rotsCount(), 3);
  });
});

describe('classifyClusters', () => {
  it('separates bookmarked, dismissed, and normal clusters', () => {
    const clusters = [
      { key: 'A', isRelease: false },
      { key: 'B', isRelease: true },
      { key: 'C', isRelease: false },
      { key: 'D', isRelease: true },
      { key: 'E', isRelease: false },
    ];
    const bookmarked = new Set(['A', 'B']);
    const dismissed = new Set(['C', 'D']);

    const result = classifyClusters(clusters, bookmarked, dismissed);
    assert.deepEqual(result.rotsTopic, ['A']);
    assert.deepEqual(result.rotsRelease, ['B']);
    assert.deepEqual(result.archTopic, ['C']);
    assert.deepEqual(result.archRelease, ['D']);
    assert.deepEqual(result.normal, ['E']);
  });

  it('bookmarked takes precedence over dismissed', () => {
    const clusters = [{ key: 'A', isRelease: false }];
    const result = classifyClusters(clusters, new Set(['A']), new Set(['A']));
    assert.deepEqual(result.rotsTopic, ['A']);
    assert.deepEqual(result.archTopic, []);
  });

  it('all normal when no bookmarks or dismissals', () => {
    const clusters = [{ key: 'X', isRelease: false }, { key: 'Y', isRelease: true }];
    const result = classifyClusters(clusters, new Set(), new Set());
    assert.deepEqual(result.normal, ['X', 'Y']);
    assert.deepEqual(result.rotsTopic, []);
    assert.deepEqual(result.rotsRelease, []);
  });
});

describe('URL encoding/decoding', () => {
  it('encodes bookmarks as date:indices', () => {
    const rots = { '2026-03-21': ['Spring Boot', 'GraalVM'] };
    const clusters = { '2026-03-21': [
      { title: 'Kotlin 2.2' },
      { title: 'Spring Boot' },
      { title: 'Quarkus' },
      { title: 'GraalVM' },
    ]};
    assert.equal(encodeRotsUrl(rots, clusters), '2026-03-21:1,3');
  });

  it('encodes multiple dates separated by pipe', () => {
    const rots = { '2026-03-21': ['A'], '2026-03-20': ['B'] };
    const clusters = {
      '2026-03-21': [{ title: 'A' }],
      '2026-03-20': [{ title: 'B' }, { title: 'C' }],
    };
    assert.equal(encodeRotsUrl(rots, clusters), '2026-03-21:0|2026-03-20:0');
  });

  it('decodes URL back to date + indices', () => {
    const result = decodeRotsUrl('2026-03-21:1,3|2026-03-20:0');
    assert.deepEqual(result, [
      { date: '2026-03-21', indices: [1, 3] },
      { date: '2026-03-20', indices: [0] },
    ]);
  });

  it('round-trips encode → decode', () => {
    const rots = { '2026-03-21': ['B', 'D'] };
    const clusters = { '2026-03-21': [
      { title: 'A' }, { title: 'B' }, { title: 'C' }, { title: 'D' },
    ]};
    const encoded = encodeRotsUrl(rots, clusters);
    const decoded = decodeRotsUrl(encoded);
    assert.equal(decoded[0].date, '2026-03-21');
    // Verify indices map back to correct titles
    const titles = decoded[0].indices.map(i => clusters['2026-03-21'][i].title);
    assert.deepEqual(titles, ['B', 'D']);
  });
});
