"use client";

import { useEffect, useState } from "react";
import { HomeOverview } from "../components/home/HomeOverview";
import { usePatient } from "../components/auth/PatientProvider";
import { apiFetch } from "../lib/api";

type HomeProfile = {
  profileId: string;
  diseaseName: string;
  recordCount: number;
  latestRecordAt?: string;
  latestRecordId?: string;
  latestRecordTitle?: string;
  latestParseStatus?: string;
};

export default function HomePage() {
  const [profiles, setProfiles] = useState<HomeProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const { currentPatient } = usePatient();

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const payload = await apiFetch<{
          profiles?: Array<Record<string, unknown>>;
        }>("/disease-profiles/overview");
        if (cancelled) return;
        const list = (payload.data?.profiles ?? [])
          .map((item) => ({
            profileId: String(item.profileId ?? item.profile_id ?? item.id ?? "unknown-profile"),
            diseaseName: String(item.diseaseName ?? item.disease_name ?? item.name ?? "未分类疾病"),
            recordCount: Number(item.recordCount ?? item.record_count ?? 0),
            latestRecordAt: String(item.latestRecordAt ?? item.latest_record_at ?? item.updatedAt ?? ""),
            latestRecordId: item.latestRecordId ?? item.latest_record_id ? String(item.latestRecordId ?? item.latest_record_id) : undefined,
            latestRecordTitle: item.latestRecordTitle ?? item.latest_record_title ? String(item.latestRecordTitle ?? item.latest_record_title) : undefined,
            latestParseStatus: item.latestParseStatus ?? item.latest_parse_status ? String(item.latestParseStatus ?? item.latest_parse_status) : undefined,
          }))
          .filter((item) => item.profileId !== "unknown" && item.diseaseName !== "Unassigned");
        setProfiles(list);
      } catch {
        setProfiles([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [currentPatient?.id]);

  if (loading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: "60px 0", color: "var(--muted)" }}>
        加载中...
      </div>
    );
  }

  return <HomeOverview profiles={profiles} />;
}
