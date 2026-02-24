import { HomeOverview } from "../components/home/HomeOverview";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

type TimelineResponse = {
  data?: {
    profiles?: Array<{
      profile_id?: string;
      profileId?: string;
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
  let profiles: Array<{
    profileId: string;
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
      profiles = (payload.data?.profiles ?? [])
        .map((item) => ({
          profileId: item.profileId ?? item.profile_id ?? "unknown-profile",
          diseaseName: item.diseaseName ?? item.disease_name ?? "未分类疾病",
          recordCount: item.recordCount ?? item.record_count ?? 0,
          latestRecordAt: item.latestRecordAt ?? item.latest_record_at,
          latestRecordId: item.latestRecordId ?? item.latest_record_id,
          latestRecordTitle: item.latestRecordTitle ?? item.latest_record_title,
          latestParseStatus: item.latestParseStatus ?? item.latest_parse_status,
        }))
        .filter((item) => item.profileId !== "unknown" && item.diseaseName !== "Unassigned");
    }
  } catch {
    profiles = [];
  }

  return <HomeOverview profiles={profiles} />;
}

