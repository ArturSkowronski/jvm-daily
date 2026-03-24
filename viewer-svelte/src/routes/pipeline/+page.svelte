<script lang="ts">
	import { onMount } from 'svelte';
	import { fetchPipeline } from '$lib/api/client';
	import type { PipelineStatus } from '$lib/api/types';
	import { fmtTimestamp, fmtDuration } from '$lib/utils/format';

	let data = $state<PipelineStatus | null>(null);
	let offline = $state(false);

	async function load() {
		data = await fetchPipeline();
		offline = data === null;
	}

	onMount(load);

	const stateColors: Record<string, string> = {
		SUCCEEDED: '#22c55e',
		FAILED: '#ef4444',
		PROCESSING: '#3b82f6',
		ENQUEUED: '#f59e0b',
		SCHEDULED: '#8b5cf6'
	};
</script>

<div class="pipeline">
	{#if offline}
		<div class="offline">
			Pipeline scheduler is offline.<br />
			Start the app with no arguments to enable the scheduler.
		</div>
	{:else if data}
		<div class="stats-row">
			<div class="stat-card stat-success">
				<div class="num">{data.stats.succeeded ?? '—'}</div>
				<div class="label">Total runs</div>
			</div>
			<div class="stat-card stat-failed">
				<div class="num">{data.stats.failed}</div>
				<div class="label">Failed</div>
			</div>
			<div class="stat-card stat-scheduled">
				<div class="num">{data.stats.scheduled}</div>
				<div class="label">Scheduled</div>
			</div>
		</div>

		<div class="section-title">
			Recent runs
			<button class="refresh-btn" onclick={load}>Refresh</button>
		</div>

		{#if data.recentJobs.length === 0}
			<div class="empty">No runs yet.</div>
		{:else}
			{#each data.recentJobs as job}
				<div class="job-row">
					<span class="job-date">{fmtTimestamp(job.createdAt)}</span>
					<span class="chip" style:background={stateColors[job.state] || '#999'}>
						{job.state}
					</span>
					<span class="job-dur">{fmtDuration(job.createdAt, job.updatedAt)}</span>
				</div>
			{/each}
		{/if}
	{/if}
</div>

<style>
	.pipeline { padding: 32px; flex: 1; overflow-y: auto; }
	.offline { color: #999; padding: 48px 0; text-align: center; }
	.stats-row { display: flex; gap: 16px; margin-bottom: 32px; }
	.stat-card {
		flex: 1; background: #fff; border: 1px solid #e8e8e8; border-radius: 12px;
		padding: 20px; text-align: center;
	}
	.num { font-size: 2rem; font-weight: 700; }
	.label { font-size: 0.75rem; color: #888; margin-top: 4px; }
	.stat-success .num { color: #22c55e; }
	.stat-failed .num { color: #ef4444; }
	.stat-scheduled .num { color: #8b5cf6; }
	.section-title { font-size: 0.85rem; font-weight: 600; margin-bottom: 16px; display: flex; align-items: center; gap: 12px; }
	.refresh-btn {
		font-size: 0.72rem; padding: 3px 10px; border: 1px solid #ddd; border-radius: 6px;
		background: none; cursor: pointer; color: #666;
	}
	.refresh-btn:hover { border-color: #999; }
	.empty { color: #999; padding: 20px 0; }
	.job-row { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
	.job-date { font-size: 0.82rem; color: #555; min-width: 140px; }
	.chip {
		font-size: 0.65rem; font-weight: 700; color: #fff; padding: 3px 10px;
		border-radius: 10px; text-transform: uppercase; letter-spacing: 0.05em;
	}
	.job-dur { font-size: 0.78rem; color: #888; }
</style>
