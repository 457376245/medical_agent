import { DiseaseTimelineView } from "../../components/timeline/DiseaseTimelineView";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

type TimelineProfileDetailResponse = {
  data?: {
    profileId?: string;
    diseaseName?: string;
    records?: Array<{
      id?: string;
      title?: string;
      record_date?: string;
      recordDate?: string;
      source_type?: string;
      sourceType?: string;
    }>;
  };
};

export default async function TimelinePage({
  searchParams,
}: {
  searchParams?: { profileId?: string; diseaseName?: string };
}) {
  const profileId = searchParams?.profileId?.trim() ?? "";
  const diseaseNameFromQuery = searchParams?.diseaseName?.trim() ?? "";
  let diseaseName = diseaseNameFromQuery || "未分类疾病";
  let records: Array<{ id: string; title: string; recordDate: string; sourceType: string }> = [];

  if (profileId) {
    try {
      const res = await fetch(`${API_BASE}/timeline/${profileId}`, { cache: "no-store" });
      if (res.ok) {
        const payload = (await res.json()) as TimelineProfileDetailResponse;
        const fromApi = String(payload.data?.diseaseName ?? "").trim();
        // Keep disease name from query when backend falls back to unassigned/default labels.
        if (fromApi && fromApi !== "Unassigned" && fromApi !== "未分类疾病") {
          diseaseName = fromApi;
        }
        records = (payload.data?.records ?? []).map((item) => ({
          id: String(item.id ?? ""),
          title: String(item.title ?? "未命名报告"),
          recordDate: String(item.recordDate ?? item.record_date ?? "暂无"),
          sourceType: String(item.sourceType ?? item.source_type ?? "UPLOAD"),
        }));
      }
    } catch {
      records = [];
    }
  }

  return <DiseaseTimelineView profileId={profileId || undefined} diseaseName={diseaseName} records={records} />;
}

