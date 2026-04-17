"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { usePatient } from "../../../components/auth/PatientProvider";
import { DiseaseTimelineView } from "../../../components/profiles/DiseaseTimelineView";
import { authFetch } from "../../../lib/api";

export default function ProfilePage() {
  const params = useParams();
  const profileId = typeof params.profileId === "string" ? params.profileId : "";
  const { currentPatient } = usePatient();
  const [diseaseName, setDiseaseName] = useState("未分类疾病");
  const [records, setRecords] = useState<Array<{ id: string; title: string; recordDate: string; sourceType: string }>>([]);
  const [parsingCount, setParsingCount] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!profileId) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    async function load() {
      try {
        const res = await authFetch(`/disease-profiles/${profileId}/records`);
        if (!res.ok) throw new Error();
        const payload = await res.json();
        if (cancelled) return;
        const fromApi = String(payload.data?.diseaseName ?? "").trim();
        if (fromApi && fromApi !== "Unassigned" && fromApi !== "未分类疾病") {
          setDiseaseName(fromApi);
        }
        setRecords(
          (payload.data?.records ?? []).map((item: Record<string, unknown>) => ({
            id: String(item.id ?? ""),
            title: String(item.title ?? "未命名报告"),
            recordDate: String(item.recordDate ?? item.record_date ?? "暂无"),
            sourceType: String(item.sourceType ?? item.source_type ?? "UPLOAD"),
          })),
        );
        setParsingCount(Number(payload.data?.parsingCount ?? 0));
      } catch {
        if (!cancelled) setRecords([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [profileId, currentPatient?.id]);

  if (loading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: "60px 0", color: "var(--muted)" }}>
        加载中...
      </div>
    );
  }

  return <DiseaseTimelineView profileId={profileId || undefined} diseaseName={diseaseName} records={records} parsingCount={parsingCount} patientId={currentPatient?.id} />;
}
