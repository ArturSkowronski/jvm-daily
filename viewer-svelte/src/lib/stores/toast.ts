import { writable } from 'svelte/store';

interface UndoEntry {
	label: string;
	fn: () => void;
}

export const toastState = writable<{ msg: string; canUndo: boolean; id: number } | null>(null);

const stack: UndoEntry[] = [];
let seq = 0;
let timer: ReturnType<typeof setTimeout> | undefined;

export function toast(msg: string, canUndo = false) {
	const id = ++seq;
	toastState.set({ msg, canUndo, id });
	clearTimeout(timer);
	timer = setTimeout(() => toastState.update((t) => (t?.id === id ? null : t)), canUndo ? 5000 : 2600);
}

/** Record a reversible action and show it with an Undo button (u). */
export function pushUndo(label: string, fn: () => void, msg = label) {
	stack.push({ label, fn });
	if (stack.length > 30) stack.shift();
	toast(msg, true);
}

export function undo() {
	const u = stack.pop();
	if (!u) return toast('Nothing to undo');
	u.fn();
	toast('Undone: ' + u.label.toLowerCase());
}
