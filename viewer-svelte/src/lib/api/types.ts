export interface DailyDigest {
	date: string;
	generatedAt: string;
	windowHours?: number;
	totalArticles: number;
	clusters: DigestCluster[];
	unclustered?: DigestArticle[];
	debug?: DebugRejected[];
}

export interface DigestCluster {
	id: string;
	title: string;
	summary: string;
	engagementScore: number;
	type: 'topic' | 'release' | 'announcement';
	articles: DigestArticle[];
	bullets?: string[];
}

export interface DigestArticle {
	id: string;
	title: string;
	url?: string | null;
	summary: string;
	topics: string[];
	entities: string[];
	engagementScore: number;
	publishedAt: string;
	ingestedAt: string;
	sourceType: string;
	handle?: string | null;
	socialLinks?: SocialLink[];
}

export interface SocialLink {
	source: string;
	url: string;
	handle: string;
}

export interface DebugRejected {
	title: string;
	url?: string | null;
	sourceType: string;
	reason: string;
}

export interface PipelineStatus {
	stats: {
		succeeded: number | null;
		failed: number;
		scheduled: number;
	};
	recentJobs: PipelineJob[];
}

export interface PipelineJob {
	id?: string;
	state: string;
	createdAt: string;
	updatedAt: string;
}
