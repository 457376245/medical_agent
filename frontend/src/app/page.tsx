import { HomeOverview } from "../components/home/HomeOverview";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

type TimelineResponse = {
  data?: {
    batches?: Array<{
      batch_id?: string;
      batchId?: string;
      disease_name?: string;
      diseaseName?: string;
      record_count?: number;
      recordCount?: number;
      latest_record_at?: string;
      latestRecordAt?: string;
      latest_record_id?: string;
      latestRecordId?: string;
      latest_record_title?: string;
      latestRecordTitle?: string;
      latest_parse_status?: string;
      latestParseStatus?: string;
    }>;
  };
};

export default async function HomePage() {
  let batches: Array<{
    batchId: string;
    diseaseName: string;
    recordCount: number;
    latestRecordAt?: string;
    latestRecordId?: string;
    latestRecordTitle?: string;
    latestParseStatus?: string;
  }> = [];

  try {
    const response = await fetch(`${API_BASE}/timeline`, { cache: "no-store" });
    if (response.ok) {
      const payload = (await response.json()) as TimelineResponse;
      batches = (payload.data?.batches ?? []).map((item) => ({
        batchId: item.batchId ?? item.batch_id ?? "unknown-batch",
        diseaseName: item.diseaseName ?? item.disease_name ?? "未分类疾病",
        recordCount: item.recordCount ?? item.record_count ?? 0,
        latestRecordAt: item.latestRecordAt ?? item.latest_record_at,
        latestRecordId: item.latestRecordId ?? item.latest_record_id,
        latestRecordTitle: item.latestRecordTitle ?? item.latest_record_title,
        latestParseStatus: item.latestParseStatus ?? item.latest_parse_status,
      }));
    }
  } catch {
    batches = [];
  }

  return <HomeOverview batches={batches} />;
}
