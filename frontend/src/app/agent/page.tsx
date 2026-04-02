import { AgentWorkbench } from "../../components/agent/AgentWorkbench";
import type { AgentProfile, AgentRecord } from "../../components/agent/types";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

type SearchParams = {
  profileId?: string;
  recordId?: string;
};

export default async function AgentPage({
  searchParams,
}: {
  searchParams?: SearchParams;
}) {
  const initialProfileId = searchParams?.profileId?.trim() || "";
  const initialRecordId = searchParams?.recordId?.trim() || "";

  const loadProfiles = async (): Promise<AgentProfile[]> => {
    try {
      const response = await fetch(`${API_BASE}/disease-profiles/overview`, { cache: "no-store" });
      if (!response.ok) {
        return [];
      }
      const payload = await response.json();
      const profiles = Array.isArray(payload?.data?.profiles) ? payload.data.profiles : [];
      return profiles
        .map((item: Record<string, unknown>) => ({
          profileId: String(item.profileId ?? item.profile_id ?? item.id ?? ""),
          diseaseName: String(item.diseaseName ?? item.disease_name ?? item.name ?? "未分类疾病"),
          recordCount: Number(item.recordCount ?? item.record_count ?? 0),
          latestRecordAt: typeof item.latestRecordAt === "string" ? item.latestRecordAt : typeof item.latest_record_at === "string" ? item.latest_record_at : undefined,
          latestRecordTitle: typeof item.latestRecordTitle === "string" ? item.latestRecordTitle : typeof item.latest_record_title === "string" ? item.latest_record_title : undefined,
          latestParseStatus: typeof item.latestParseStatus === "string" ? item.latestParseStatus : typeof item.latest_parse_status === "string" ? item.latest_parse_status : undefined,
        }))
        .filter((item: AgentProfile) => Boolean(item.profileId));
    } catch {
      return [];
    }
  };

  const loadInitialRecords = async (): Promise<AgentRecord[]> => {
    if (!initialProfileId) {
      return [];
    }
    try {
      const response = await fetch(`${API_BASE}/disease-profiles/${encodeURIComponent(initialProfileId)}/records`, { cache: "no-store" });
      if (!response.ok) {
        return [];
      }
      const payload = await response.json();
      const records = Array.isArray(payload?.data?.records) ? payload.data.records : [];
      return records.map((item: Record<string, unknown>) => ({
        id: String(item.id ?? ""),
        title: String(item.title ?? "未命名报告"),
        recordDate: String(item.recordDate ?? item.record_date ?? ""),
        sourceType: String(item.sourceType ?? item.source_type ?? "UPLOAD"),
      }));
    } catch {
      return [];
    }
  };

  const [profiles, initialRecords] = await Promise.all([loadProfiles(), loadInitialRecords()]);
  const safeInitialRecordId =
    initialRecordId && initialRecords.some((record) => record.id === initialRecordId) ? initialRecordId : "";

  return (
    <AgentWorkbench
      profiles={profiles}
      initialProfileId={initialProfileId || undefined}
      initialRecordId={safeInitialRecordId || undefined}
      initialRecords={initialRecords}
    />
  );
}
