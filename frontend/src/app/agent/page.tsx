"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AgentWorkbench } from "../../components/agent/AgentWorkbench";
import type { AgentProfile, AgentRecord } from "../../components/agent/types";
import { authFetch } from "../../lib/api";

export default function AgentPage() {
  const searchParams = useSearchParams();
  const initialProfileId = searchParams.get("profileId")?.trim() || "";
  const initialRecordId = searchParams.get("recordId")?.trim() || "";

  const [profiles, setProfiles] = useState<AgentProfile[]>([]);
  const [initialRecords, setInitialRecords] = useState<AgentRecord[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function loadProfiles(): Promise<AgentProfile[]> {
      try {
        const response = await authFetch("/disease-profiles/overview");
        if (!response.ok) return [];
        const payload = await response.json();
        const list = Array.isArray(payload?.data?.profiles) ? payload.data.profiles : [];
        return list
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
    }

    async function loadInitialRecords(): Promise<AgentRecord[]> {
      if (!initialProfileId) return [];
      try {
        const response = await authFetch(`/disease-profiles/${encodeURIComponent(initialProfileId)}/records`);
        if (!response.ok) return [];
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
    }

    Promise.all([loadProfiles(), loadInitialRecords()]).then(([p, r]) => {
      if (cancelled) return;
      setProfiles(p);
      setInitialRecords(r);
      setLoading(false);
    });

    return () => { cancelled = true; };
  }, [initialProfileId]);

  if (loading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: "60px 0", color: "var(--muted)" }}>
        加载中...
      </div>
    );
  }

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
