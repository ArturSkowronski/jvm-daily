<script lang="ts">
	import { onMount } from 'svelte';
	import { fetchPipeline, fetchFeedRuns } from '$lib/api/client';
	import type { PipelineStatus, FeedRunSummary } from '$lib/api/types';
	import { fmtTimestamp, fmtDuration } from '$lib/utils/format';

	let data = $state<PipelineStatus | null>(null);
	let feedRuns = $state<FeedRunSummary[]>([]);
	let offline = $state(false);

	async function load() {
		const [pipeline, runs] = await Promise.all([fetchPipeline(), fetchFeedRuns()]);
		data = pipeline;
		feedRuns = runs;
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

	const statusColors: Record<string, string> = {
		SUCCESS: '#22c55e',
		PARTIAL_SUCCESS: '#f59e0b',
		FAILED: '#ef4444',
	};

	function timeAgo(iso: string): string {
		if (!iso) return '—';
		const diff = Date.now() - new Date(iso).getTime();
		const mins = Math.floor(diff / 60000);
		if (mins < 60) return `${mins}m ago`;
		const hrs = Math.floor(mins / 60);
		if (hrs < 24) return `${hrs}h ago`;
		const days = Math.floor(hrs / 24);
		return `${days}d ago`;
	}

	// Group feed runs by sourceType
	function grouped(runs: FeedRunSummary[]): Map<string, FeedRunSummary[]> {
		const m = new Map<string, FeedRunSummary[]>();
		for (const r of runs) {
			const arr = m.get(r.sourceType) ?? [];
			arr.push(r);
			m.set(r.sourceType, arr);
		}
		return m;
	}

	const typeLabels: Record<string, string> = {
		rss: 'RSS Feeds',
		bluesky: 'Bluesky',
		reddit: 'Reddit',
		github_trending: 'GitHub Trending',
		github_releases: 'GitHub Releases',
		openjdk_mail: 'OpenJDK Mailing Lists',
		jep: 'JEP Tracker',
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

	{#if feedRuns.length > 0}
		<div class="section-title feed-health-title">
			Feed health (last 24 h)
		</div>

		{#each [...grouped(feedRuns)] as [sourceType, runs]}
			<div class="feed-group">
				<div class="feed-group-header">{typeLabels[sourceType] ?? sourceType}</div>
				<table class="feed-table">
					<thead>
						<tr>
							<th class="col-source">Source</th>
							<th class="col-status">Status</th>
							<th class="col-last">Last run</th>
							<th class="col-last">Last success</th>
							<th class="col-num">Runs</th>
							<th class="col-num">New</th>
						</tr>
					</thead>
					<tbody>
						{#each runs as run}
							<tr class:failed-row={run.last24hFailures > 0 && run.last24hSuccesses === 0}>
								<td class="col-source">{run.sourceId}</td>
								<td class="col-status">
									<span class="status-dot" style:background={statusColors[run.lastRunStatus] || '#999'}></span>
									{run.lastRunStatus}
								</td>
								<td class="col-last">{timeAgo(run.lastRunAt)}</td>
								<td class="col-last">{run.lastSuccessAt ? timeAgo(run.lastSuccessAt) : '—'}</td>
								<td class="col-num">{run.last24hRuns}</td>
								<td class="col-num">{run.last24hNewCount}</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		{/each}
	{/if}
</div>

<style>
	.pipeline { padding: 32px; flex: 1; overflow-y: auto; max-width: 900px; margin: 0 auto; }
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

	/* Feed health */
	.feed-health-title { margin-top: 40px; }
	.feed-group { margin-bottom: 24px; }
	.feed-group-header {
		font-size: 0.78rem; font-weight: 600; color: #555;
		padding: 6px 0; border-bottom: 2px solid #e8e8e8; margin-bottom: 4px;
	}
	.feed-table { width: 100%; border-collapse: collapse; font-size: 0.78rem; }
	.feed-table th {
		text-align: left; font-weight: 500; color: #999; padding: 6px 8px;
		border-bottom: 1px solid #f0f0f0; font-size: 0.7rem;
	}
	.feed-table td { padding: 5px 8px; border-bottom: 1px solid #f8f8f8; color: #444; }
	.col-source { min-width: 160px; }
	.col-status { min-width: 100px; white-space: nowrap; }
	.col-last { min-width: 80px; color: #888; }
	.col-num { text-align: right; min-width: 40px; }
	.status-dot {
		display: inline-block; width: 7px; height: 7px; border-radius: 50%;
		margin-right: 5px; vertical-align: middle;
	}
	.failed-row td { color: #ef4444; }
	.feed-table th.col-num { text-align: right; }
</style>
