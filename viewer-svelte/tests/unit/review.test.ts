import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
	isHandled,
	nextPending,
	stepFocus,
	adjacentDate,
	nextUnreviewedDate,
	startDate
} from '../../src/lib/utils/review.ts';

const DATES = ['2026-03-25', '2026-03-24', '2026-03-23', '2026-03-22']; // newest first

test('any mark takes an item out of the inbox', () => {
	assert.equal(isHandled({ done: false, later: false, rots: false }), false);
	assert.equal(isHandled({ done: true, later: false, rots: false }), true);
	assert.equal(isHandled({ done: false, later: true, rots: false }), true);
	assert.equal(isHandled({ done: false, later: false, rots: true }), true);
});

test('nextPending prefers the next pending item, then the previous one', () => {
	const order = ['a', 'b', 'c', 'd'];
	assert.equal(nextPending(order, 'b', (k) => k !== 'b'), 'c');
	assert.equal(nextPending(order, 'b', (k) => k === 'a' || k === 'd'), 'd');
	assert.equal(nextPending(order, 'd', (k) => k !== 'd'), 'c');
	assert.equal(nextPending(order, 'a', () => false), null);
	assert.equal(nextPending(order, 'missing', (k) => k === 'c'), 'c');
});

test('stepFocus moves and clamps at both ends', () => {
	const order = ['a', 'b', 'c'];
	assert.equal(stepFocus(order, 'a', 1), 'b');
	assert.equal(stepFocus(order, 'c', 1), 'c');
	assert.equal(stepFocus(order, 'a', -1), 'a');
	assert.equal(stepFocus(order, null, 1), 'a');
	assert.equal(stepFocus([], 'a', 1), null);
});

test('adjacentDate: 1 is older, -1 is newer, null past the ends', () => {
	assert.equal(adjacentDate(DATES, '2026-03-24', 1), '2026-03-23');
	assert.equal(adjacentDate(DATES, '2026-03-24', -1), '2026-03-25');
	assert.equal(adjacentDate(DATES, '2026-03-22', 1), null);
	assert.equal(adjacentDate(DATES, '2026-03-25', -1), null);
});

test('nextUnreviewedDate skips reviewed days, older first, then newer', () => {
	const reviewed = new Set(['2026-03-23']);
	assert.equal(nextUnreviewedDate(DATES, reviewed, '2026-03-24'), '2026-03-22');
	assert.equal(nextUnreviewedDate(DATES, new Set(['2026-03-23', '2026-03-22']), '2026-03-24'), '2026-03-25');
	assert.equal(nextUnreviewedDate(DATES, new Set(DATES), '2026-03-24'), null);
});

test('startDate opens the newest unreviewed day, or the newest when all are reviewed', () => {
	assert.equal(startDate(DATES, new Set()), '2026-03-25');
	assert.equal(startDate(DATES, new Set(['2026-03-25', '2026-03-24'])), '2026-03-23');
	assert.equal(startDate(DATES, new Set(DATES)), '2026-03-25');
	assert.equal(startDate([], new Set()), null);
});
