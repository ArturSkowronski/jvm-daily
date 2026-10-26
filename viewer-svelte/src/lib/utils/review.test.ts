import { test, expect } from 'vitest';
import {
	isHandled,
	nextPending,
	stepFocus,
	adjacentDate,
	nextUnreviewedDate,
	startDate
} from './review';

const DATES = ['2026-03-25', '2026-03-24', '2026-03-23', '2026-03-22']; // newest first

test('any mark takes an item out of the inbox', () => {
	expect(isHandled({ done: false, later: false, rots: false })).toBe(false);
	expect(isHandled({ done: true, later: false, rots: false })).toBe(true);
	expect(isHandled({ done: false, later: true, rots: false })).toBe(true);
	expect(isHandled({ done: false, later: false, rots: true })).toBe(true);
});

test('nextPending prefers the next pending item, then the previous one', () => {
	const order = ['a', 'b', 'c', 'd'];
	expect(nextPending(order, 'b', (k) => k !== 'b')).toBe('c');
	expect(nextPending(order, 'b', (k) => k === 'a' || k === 'd')).toBe('d');
	expect(nextPending(order, 'd', (k) => k !== 'd')).toBe('c');
	expect(nextPending(order, 'a', () => false)).toBe(null);
	expect(nextPending(order, 'missing', (k) => k === 'c')).toBe('c');
});

test('stepFocus moves and clamps at both ends', () => {
	const order = ['a', 'b', 'c'];
	expect(stepFocus(order, 'a', 1)).toBe('b');
	expect(stepFocus(order, 'c', 1)).toBe('c');
	expect(stepFocus(order, 'a', -1)).toBe('a');
	expect(stepFocus(order, null, 1)).toBe('a');
	expect(stepFocus([], 'a', 1)).toBe(null);
});

test('adjacentDate: 1 is older, -1 is newer, null past the ends', () => {
	expect(adjacentDate(DATES, '2026-03-24', 1)).toBe('2026-03-23');
	expect(adjacentDate(DATES, '2026-03-24', -1)).toBe('2026-03-25');
	expect(adjacentDate(DATES, '2026-03-22', 1)).toBe(null);
	expect(adjacentDate(DATES, '2026-03-25', -1)).toBe(null);
});

test('nextUnreviewedDate skips reviewed days, older first, then newer', () => {
	const reviewed = new Set(['2026-03-23']);
	expect(nextUnreviewedDate(DATES, reviewed, '2026-03-24')).toBe('2026-03-22');
	expect(nextUnreviewedDate(DATES, new Set(['2026-03-23', '2026-03-22']), '2026-03-24')).toBe('2026-03-25');
	expect(nextUnreviewedDate(DATES, new Set(DATES), '2026-03-24')).toBe(null);
});

test('startDate opens the newest unreviewed day, or the newest when all are reviewed', () => {
	expect(startDate(DATES, new Set())).toBe('2026-03-25');
	expect(startDate(DATES, new Set(['2026-03-25', '2026-03-24']))).toBe('2026-03-23');
	expect(startDate(DATES, new Set(DATES))).toBe('2026-03-25');
	expect(startDate([], new Set())).toBe(null);
});
