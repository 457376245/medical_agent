"use client";

import { useSearchParams } from "next/navigation";
import { AgentPageFrame } from "../../../components/agent/AgentPageFrame";
import { AgentWorkbench } from "../../../components/agent/AgentWorkbench";
import { useAgentDashboard } from "../../../components/agent/useAgentDashboard";
import { usePatient } from "../../../components/auth/PatientProvider";

export default function AgentChatPage() {
  const searchParams = useSearchParams();
  const { currentPatient } = usePatient();
  const profileId = searchParams.get("profileId")?.trim() || undefined;
  const recordId = searchParams.get("recordId")?.trim() || undefined;
  const { data, loading, error } = useAgentDashboard(profileId, currentPatient?.id);
  const selectedProfileId = data?.selectedProfile?.profileId;
  const safeRecordId = recordId && data?.records.some((record) => record.id === recordId) ? recordId : undefined;

  return (
    <AgentPageFrame profiles={data?.profiles ?? []} selectedProfile={data?.selectedProfile}>
      {loading ? (
        <div className="agent-dashboard-loading" role="status">
          <div className="agent-skeleton-line" style={{ width: "36%" }} />
          <div className="agent-skeleton-line" style={{ width: "58%" }} />
        </div>
      ) : error ? (
        <section className="agent-empty-state">
          <h2>暂时无法加载咨询上下文</h2>
          <p>{error}</p>
        </section>
      ) : (
        <AgentWorkbench
          key={`${currentPatient?.id ?? "patient"}-${selectedProfileId ?? "none"}`}
          profiles={data?.profiles ?? []}
          initialProfileId={selectedProfileId}
          initialRecordId={safeRecordId}
          initialRecords={data?.records ?? []}
          patientId={currentPatient?.id}
        />
      )}
    </AgentPageFrame>
  );
}
