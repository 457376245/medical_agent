"use client";

import { startTransition, useEffect, useRef, useState } from "react";
import { normalizeStructuredFields } from "./agent-utils";
import type {
  AgentRecord,
  AgentRecordDetail,
  AgentTrendData,
} from "./types";
import { authFetch } from "../../lib/api";

export type UseRecordContextResult = {
  records: AgentRecord[];
  recordDetail: AgentRecordDetail | null;
  recordAnalysis: string | null;
  trendData: AgentTrendData | null;
  loadingRecords: boolean;
  contextLoading: boolean;
  contextError: string;
  recordDetails: Record<string, AgentRecordDetail | null | undefined>;
  recordAnalyses: Record<string, string | null | undefined>;
  trendDataMap: Record<string, AgentTrendData | null | undefined>;
  setRecords: React.Dispatch<React.SetStateAction<AgentRecord[]>>;
};

export function useRecordContext(
  profileId: string,
  recordId: string,
  initialProfileId?: string,
  initialRecords?: AgentRecord[],
): UseRecordContextResult {
  const [records, setRecords] = useState<AgentRecord[]>(initialRecords ?? []);
  const [loadingRecords, setLoadingRecords] = useState(false);
  const [contextError, setContextError] = useState("");
  const [recordDetails, setRecordDetails] = useState<Record<string, AgentRecordDetail | null | undefined>>({});
  const [recordAnalyses, setRecordAnalyses] = useState<Record<string, string | null | undefined>>({});
  const [trendDataMap, setTrendDataMap] = useState<Record<string, AgentTrendData | null | undefined>>({});

  const hydratedInitialRecordsRef = useRef(Boolean(initialProfileId));

  const recordDetail = recordId ? recordDetails[recordId] ?? null : null;
  const recordAnalysis = recordId ? recordAnalyses[recordId] ?? null : null;
  const trendData = recordId ? trendDataMap[recordId] ?? null : null;

  const contextLoading = (() => {
    if (!recordId) return false;
    const detailPending = recordDetails[recordId] === undefined;
    const trendPending = trendDataMap[recordId] === undefined;
    const analysisPending =
      recordDetails[recordId] &&
      (recordDetails[recordId]?.fields.length ?? 0) > 0 &&
      recordAnalyses[recordId] === undefined;
    return Boolean(detailPending || trendPending || analysisPending);
  })();

  // Load records when profile changes
  useEffect(() => {
    let cancelled = false;
    if (!profileId) {
      setRecords([]);
      return;
    }

    if (hydratedInitialRecordsRef.current && profileId === initialProfileId) {
      hydratedInitialRecordsRef.current = false;
      setRecords(initialRecords ?? []);
      return;
    }

    const loadRecords = async () => {
      setLoadingRecords(true);
      try {
        const response = await authFetch(`/disease-profiles/${encodeURIComponent(profileId)}/records`);
        if (!response.ok) {
          throw new Error("加载疾病报告失败，请稍后重试。");
        }
        const payload = await response.json();
        const nextRecords: AgentRecord[] = Array.isArray(payload?.data?.records)
          ? payload.data.records.map((item: Record<string, unknown>) => ({
              id: String(item.id ?? ""),
              title: String(item.title ?? "未命名报告"),
              recordDate: String(item.recordDate ?? item.record_date ?? ""),
              sourceType: String(item.sourceType ?? item.source_type ?? "UPLOAD"),
            }))
          : [];
        if (!cancelled) {
          startTransition(() => {
            setRecords(nextRecords);
          });
        }
      } catch (error) {
        if (!cancelled) {
          setContextError(error instanceof Error ? error.message : "加载疾病报告失败，请稍后重试。");
        }
      } finally {
        if (!cancelled) {
          setLoadingRecords(false);
        }
      }
    };

    void loadRecords();
    return () => {
      cancelled = true;
    };
  }, [profileId, initialProfileId, initialRecords]);

  // Load record detail
  useEffect(() => {
    let cancelled = false;
    if (!recordId || recordDetails[recordId] !== undefined) return;
    const loadRecordDetail = async () => {
      setContextError("");
      try {
        const response = await authFetch(`/records/${encodeURIComponent(recordId)}`);
        if (!response.ok) throw new Error("加载报告详情失败，请稍后重试。");
        const payload = await response.json();
        const detail = payload?.data;
        const normalized: AgentRecordDetail = {
          recordId: String(detail?.recordId ?? recordId),
          summary: String(detail?.summary ?? ""),
          parseStatus: String(detail?.parseStatus ?? "NOT_PARSED"),
          fields: normalizeStructuredFields(detail?.structuredResult?.payload),
        };
        if (!cancelled) {
          setRecordDetails((prev) => ({ ...prev, [recordId]: normalized }));
        }
      } catch (error) {
        if (!cancelled) {
          setRecordDetails((prev) => ({ ...prev, [recordId]: null }));
          setContextError(error instanceof Error ? error.message : "加载报告详情失败，请稍后重试。");
        }
      }
    };
    void loadRecordDetail();
    return () => { cancelled = true; };
  }, [recordDetails, recordId]);

  // Load trend data
  useEffect(() => {
    let cancelled = false;
    if (!recordId || trendDataMap[recordId] !== undefined) return;
    const loadTrend = async () => {
      try {
        const response = await authFetch(`/records/${encodeURIComponent(recordId)}/trend?limit=3`);
        if (!response.ok) throw new Error("加载趋势摘要失败，请稍后重试。");
        const payload = await response.json();
        const data = payload?.data;
        const normalized: AgentTrendData = {
          recordId: String(data?.recordId ?? recordId),
          sourceType: String(data?.sourceType ?? ""),
          diseaseProfileId: String(data?.diseaseProfileId ?? ""),
          limit: Number(data?.limit ?? 3),
          snapshots: Array.isArray(data?.snapshots)
            ? data.snapshots.map((snapshot: Record<string, unknown>) => ({
                recordId: String(snapshot.recordId ?? ""),
                recordDate: String(snapshot.recordDate ?? ""),
                title: String(snapshot.title ?? "未命名报告"),
                sourceType: String(snapshot.sourceType ?? ""),
                fields: Array.isArray(snapshot.fields)
                  ? snapshot.fields
                      .map((field: Record<string, unknown>) => {
                        const name = String(field.name ?? "").trim();
                        const value = String(field.value ?? "").trim();
                        if (!name || !value) return null;
                        return {
                          name,
                          value,
                          unit: String(field.unit ?? "").trim() || undefined,
                          referenceRange: String(field.referenceRange ?? field.reference_range ?? "").trim() || undefined,
                        };
                      })
                      .filter((field): field is NonNullable<typeof field> => Boolean(field))
                  : [],
              }))
            : [],
        };
        if (!cancelled) {
          setTrendDataMap((prev) => ({ ...prev, [recordId]: normalized }));
        }
      } catch {
        if (!cancelled) {
          setTrendDataMap((prev) => ({ ...prev, [recordId]: null }));
        }
      }
    };
    void loadTrend();
    return () => { cancelled = true; };
  }, [recordId, trendDataMap]);

  // Load AI analysis
  useEffect(() => {
    let cancelled = false;
    const detail = recordId ? recordDetails[recordId] : null;
    if (!recordId || detail === undefined || recordAnalyses[recordId] !== undefined) return;
    if (!detail || detail.fields.length === 0 || detail.parseStatus.toUpperCase() !== "SUCCESS") {
      setRecordAnalyses((prev) => ({ ...prev, [recordId]: null }));
      return;
    }

    const loadAnalysis = async () => {
      try {
        const response = await authFetch(`/records/${encodeURIComponent(recordId)}/analysis`);
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
          setRecordAnalyses((prev) => ({ ...prev, [recordId]: null }));
          if (response.status !== 409 && !cancelled) {
            setContextError(String(payload?.message ?? "加载 AI 分析失败，请稍后重试。"));
          }
          return;
        }
        const content = String(payload?.data?.content ?? "").trim();
        if (!cancelled) {
          setRecordAnalyses((prev) => ({ ...prev, [recordId]: content || null }));
        }
      } catch {
        if (!cancelled) {
          setRecordAnalyses((prev) => ({ ...prev, [recordId]: null }));
        }
      }
    };
    void loadAnalysis();
    return () => { cancelled = true; };
  }, [recordAnalyses, recordDetails, recordId]);

  return {
    records,
    recordDetail,
    recordAnalysis,
    trendData,
    loadingRecords,
    contextLoading,
    contextError,
    recordDetails,
    recordAnalyses,
    trendDataMap,
    setRecords,
  };
}
