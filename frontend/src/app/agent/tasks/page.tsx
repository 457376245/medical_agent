"use client";

import { useSearchParams } from "next/navigation";
import { AgentPageFrame } from "../../../components/agent/AgentPageFrame";
import { CareProfilePanel } from "../../../components/agent/CareProfilePanel";
import { FollowUpTasksPanel } from "../../../components/agent/FollowUpTasksPanel";
import { SymptomLogPanel } from "../../../components/agent/SymptomLogPanel";
import { useAgentDashboard } from "../../../components/agent/useAgentDashboard";
import { useCareSupport } from "../../../components/agent/useCareSupport";
import { usePatient } from "../../../components/auth/PatientProvider";

export default function AgentTasksPage() {
  const searchParams = useSearchParams();
  const { currentPatient } = usePatient();
  const profileId = searchParams.get("profileId")?.trim() || undefined;
  const { data, loading, error, reload } = useAgentDashboard(profileId, currentPatient?.id);
  const selectedProfileId = data?.selectedProfile?.profileId;
  const care = useCareSupport(currentPatient?.id, selectedProfileId);

  const handleCreateTask = async (input: Parameters<typeof care.createFollowUpTask>[0]) => {
    await care.createFollowUpTask({ ...input, diseaseProfileId: selectedProfileId });
    await reload();
  };

  const handleCreateSymptom = async (input: Parameters<typeof care.createSymptomLog>[0]) => {
    await care.createSymptomLog({ ...input, diseaseProfileId: selectedProfileId });
    await reload();
  };

  return (
    <AgentPageFrame profiles={data?.profiles ?? []} selectedProfile={data?.selectedProfile}>
      {loading ? (
        <div className="agent-dashboard-loading" role="status">
          <div className="agent-skeleton-line" style={{ width: "40%" }} />
          <div className="agent-skeleton-line" style={{ width: "64%" }} />
        </div>
      ) : error ? (
        <section className="agent-empty-state">
          <h2>暂时无法加载随访页</h2>
          <p>{error}</p>
        </section>
      ) : (
        <section className="agent-tasks-layout">
          <div className="agent-section-head">
            <div>
              <p className="hero-kicker">行动闭环</p>
              <h2>随访、症状和用药</h2>
            </div>
            {care.careError ? <p className="status-text error">{care.careError}</p> : null}
          </div>

          <div className="agent-dashboard-two-col">
            <FollowUpTasksPanel
              tasks={care.followUpTasks}
              profileId={selectedProfileId}
              onCreateTask={handleCreateTask}
              onToggleTask={async (task) => {
                await care.updateFollowUpTask(task.id, {
                  status: (task.status ?? "OPEN") === "DONE" ? "OPEN" : "DONE",
                });
                await reload();
              }}
            />
            <SymptomLogPanel
              symptoms={care.symptoms}
              profileId={selectedProfileId}
              onCreateSymptom={handleCreateSymptom}
            />
          </div>

          <CareProfilePanel careProfile={care.careProfile} onSave={care.saveCareProfile} />
        </section>
      )}
    </AgentPageFrame>
  );
}
