import { createSavedList } from './savedList';

/** "Rest of the Story" — clusters picked for the newsletter's ROTS section. Key kept for existing data. */
export const rots = createSavedList('jvm-daily-rots');

/** Read later — Instapaper-style queue of clusters to come back to. */
export const later = createSavedList('jvm-daily-later');
