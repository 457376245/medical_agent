"use client";

import { useEffect, useState } from "react";
import type { CareMedication, CareProfile, PatientMemoryEntry } from "./types";

function joinLines(values: string[]): string {
  return values.join("\n");
}

function splitLines(value: string): string[] {
  return value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function medicationsToText(medications: CareMedication[]): string {
  return medications
    .map((item) => [item.name, item.dosage, item.frequency, item.purpose].filter(Boolean).join(" | "))
    .join("\n");
}

function parseMedications(value: string): CareMedication[] {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [name, dosage, frequency, purpose] = line.split("|").map((part) => part.trim());
      return {
        name,
        dosage: dosage || undefined,
        frequency: frequency || undefined,
        purpose: purpose || undefined,
      };
    })
    .filter((item) => item.name);
}

function memoryFieldLabel(fieldPath: string): string {
  switch (fieldPath) {
    case "patientBaseline.diagnosedConditions":
      return "已知诊断";
    case "patientBaseline.allergies":
      return "过敏 / 禁忌";
    case "patientBaseline.abnormalBaseline":
      return "既往异常基线";
    case "patientBaseline.doctorInstructions":
      return "医生交代事项";
    case "patientBaseline.recentSymptoms":
      return "近期症状 / 体征";
    case "currentMedications":
      return "当前用药";
    case "careGoals":
      return "健康目标";
    case "redFlagNotes":
      return "红旗提醒";
    case "personalContext":
      return "个人背景 / 偏好";
    case "followUpTasks":
      return "随访任务";
    default:
      return fieldPath;
  }
}

export function CareProfilePanel({
  careProfile,
  pendingMemories = [],
  onSave,
  onConfirmMemory,
  onRejectMemory,
}: {
  careProfile: CareProfile;
  pendingMemories?: PatientMemoryEntry[];
  onSave: (input: {
    diagnosedConditions: string[];
    currentMedications: CareMedication[];
    allergies: string[];
    abnormalBaseline: string[];
    doctorInstructions?: string;
    careGoals: string[];
    redFlagNotes: string[];
    personalContext: string[];
  }) => Promise<void>;
  onConfirmMemory?: (memoryId: string) => Promise<void>;
  onRejectMemory?: (memoryId: string) => Promise<void>;
}) {
  const [diagnosedConditions, setDiagnosedConditions] = useState("");
  const [allergies, setAllergies] = useState("");
  const [abnormalBaseline, setAbnormalBaseline] = useState("");
  const [doctorInstructions, setDoctorInstructions] = useState("");
  const [careGoals, setCareGoals] = useState("");
  const [redFlagNotes, setRedFlagNotes] = useState("");
  const [personalContext, setPersonalContext] = useState("");
  const [medications, setMedications] = useState("");
  const [saving, setSaving] = useState(false);
  const [memoryBusyId, setMemoryBusyId] = useState("");
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(false);

  useEffect(() => {
    setDiagnosedConditions(joinLines(careProfile.patientBaseline.diagnosedConditions));
    setAllergies(joinLines(careProfile.patientBaseline.allergies));
    setAbnormalBaseline(joinLines(careProfile.patientBaseline.abnormalBaseline));
    setDoctorInstructions(careProfile.patientBaseline.doctorInstructions ?? "");
    setCareGoals(joinLines(careProfile.careGoals));
    setRedFlagNotes(joinLines(careProfile.redFlagNotes));
    setPersonalContext(joinLines(careProfile.personalContext));
    setMedications(medicationsToText(careProfile.currentMedications));
  }, [careProfile]);

  const handleSave = async () => {
    setSaving(true);
    setError("");
    try {
      await onSave({
        diagnosedConditions: splitLines(diagnosedConditions),
        currentMedications: parseMedications(medications),
        allergies: splitLines(allergies),
        abnormalBaseline: splitLines(abnormalBaseline),
        doctorInstructions: doctorInstructions.trim() || undefined,
        careGoals: splitLines(careGoals),
        redFlagNotes: splitLines(redFlagNotes),
        personalContext: splitLines(personalContext),
      });
      setEditing(false);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "保存慢病画像失败。");
    } finally {
      setSaving(false);
    }
  };

  const handleMemoryAction = async (memoryId: string, action: "confirm" | "reject") => {
    const handler = action === "confirm" ? onConfirmMemory : onRejectMemory;
    if (!handler) return;
    setMemoryBusyId(memoryId);
    setError("");
    try {
      await handler(memoryId);
    } catch (memoryError) {
      setError(memoryError instanceof Error ? memoryError.message : "处理画像更新失败。");
    } finally {
      setMemoryBusyId("");
    }
  };

  const hasProfileSummary =
    careProfile.patientBaseline.diagnosedConditions.length > 0 ||
    careProfile.patientBaseline.allergies.length > 0 ||
    careProfile.currentMedications.length > 0 ||
    careProfile.careGoals.length > 0 ||
    careProfile.redFlagNotes.length > 0 ||
    careProfile.personalContext.length > 0 ||
    Boolean(careProfile.patientBaseline.doctorInstructions);

  return (
    <section className="agent-care-card">
      <div className="agent-care-card-head">
        <div>
          <p className="hero-kicker">长期画像</p>
          <h4>你的慢病画像</h4>
        </div>
        <button className="btn btn-ghost btn-small" type="button" onClick={() => setEditing((current) => !current)}>
          {editing ? "收起编辑" : "编辑画像"}
        </button>
      </div>

      <div className="agent-care-summary-grid">
        {careProfile.updatedAt ? <span className="badge">更新于 {new Date(careProfile.updatedAt).toLocaleDateString("zh-CN")}</span> : null}
        {hasProfileSummary ? (
          <>
            {careProfile.patientBaseline.diagnosedConditions.length > 0 ? (
              <div className="agent-care-summary-block">
                <strong>已知诊断</strong>
                <div className="agent-chip-list">
                  {careProfile.patientBaseline.diagnosedConditions.map((item) => <span className="agent-chip" key={item}>{item}</span>)}
                </div>
              </div>
            ) : null}
            {careProfile.currentMedications.length > 0 ? (
              <div className="agent-care-summary-block">
                <strong>当前用药</strong>
                <div className="agent-care-mini-list">
                  {careProfile.currentMedications.slice(0, 4).map((item) => (
                    <span key={`${item.name}-${item.dosage ?? ""}`}>{[item.name, item.dosage, item.frequency].filter(Boolean).join(" / ")}</span>
                  ))}
                </div>
              </div>
            ) : null}
            {careProfile.patientBaseline.allergies.length > 0 ? (
              <div className="agent-care-summary-block">
                <strong>过敏 / 禁忌</strong>
                <div className="agent-chip-list">
                  {careProfile.patientBaseline.allergies.map((item) => <span className="agent-chip chip-warning" key={item}>{item}</span>)}
                </div>
              </div>
            ) : null}
            {careProfile.careGoals.length > 0 ? (
              <div className="agent-care-summary-block">
                <strong>健康目标</strong>
                <div className="agent-care-mini-list">
                  {careProfile.careGoals.slice(0, 3).map((item) => <span key={item}>{item}</span>)}
                </div>
              </div>
            ) : null}
            {careProfile.redFlagNotes.length > 0 ? (
              <div className="agent-care-summary-block">
                <strong>红旗提醒</strong>
                <div className="agent-care-mini-list warning">
                  {careProfile.redFlagNotes.slice(0, 3).map((item) => <span key={item}>{item}</span>)}
                </div>
              </div>
            ) : null}
            {careProfile.personalContext.length > 0 ? (
              <div className="agent-care-summary-block">
                <strong>个人背景 / 偏好</strong>
                <div className="agent-care-mini-list">
                  {careProfile.personalContext.slice(0, 3).map((item) => <span key={item}>{item}</span>)}
                </div>
              </div>
            ) : null}
            {careProfile.patientBaseline.doctorInstructions ? (
              <p className="agent-care-summary">{careProfile.patientBaseline.doctorInstructions}</p>
            ) : null}
          </>
        ) : (
          <p className="agent-care-empty">还没有补充长期画像。补充后，Agent 可以更好地结合慢病、用药和禁忌回答。</p>
        )}
      </div>

      {pendingMemories.length > 0 ? (
        <div className="agent-care-memory-review">
          <div className="agent-care-inline-list">
            <strong>待确认画像更新</strong>
            <div className="agent-care-mini-list">
              {pendingMemories.slice(0, 5).map((memory) => (
                <div className="agent-memory-review-item" key={memory.id}>
                  <div>
                    <span className="badge">{memoryFieldLabel(memory.fieldPath)}</span>
                    {memory.riskLevel ? <span className={`badge badge-risk-${memory.riskLevel.toLowerCase()}`}>{memory.riskLevel}</span> : null}
                    <p>{memory.valueText || memory.valueJson || "待确认信息"}</p>
                    {memory.evidenceText ? <small>{memory.evidenceText}</small> : null}
                  </div>
                  <div className="agent-memory-review-actions">
                    <button
                      className="btn btn-primary btn-small"
                      type="button"
                      disabled={memoryBusyId === memory.id}
                      onClick={() => void handleMemoryAction(memory.id, "confirm")}
                    >
                      确认
                    </button>
                    <button
                      className="btn btn-ghost btn-small"
                      type="button"
                      disabled={memoryBusyId === memory.id}
                      onClick={() => void handleMemoryAction(memory.id, "reject")}
                    >
                      忽略
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      ) : null}

      {editing ? (
        <>
          <div className="agent-care-divider" />
          <div className="agent-care-form">
            <label>
              <span>已知慢病 / 诊断</span>
              <textarea value={diagnosedConditions} onChange={(e) => setDiagnosedConditions(e.target.value)} placeholder="每行一项，例如：2型糖尿病" />
            </label>
            <label>
              <span>过敏 / 禁忌</span>
              <textarea value={allergies} onChange={(e) => setAllergies(e.target.value)} placeholder="每行一项，例如：青霉素过敏" />
            </label>
            <label>
              <span>当前用药</span>
              <textarea value={medications} onChange={(e) => setMedications(e.target.value)} placeholder="每行：药名 | 剂量 | 频次 | 用途" />
            </label>
            <label>
              <span>既往异常基线</span>
              <textarea value={abnormalBaseline} onChange={(e) => setAbnormalBaseline(e.target.value)} placeholder="每行一项，例如：ALT长期轻度偏高" />
            </label>
            <label>
              <span>医生交代事项</span>
              <textarea value={doctorInstructions} onChange={(e) => setDoctorInstructions(e.target.value)} placeholder="例如：每3个月复查肝肾功能" />
            </label>
            <label>
              <span>当前健康目标</span>
              <textarea value={careGoals} onChange={(e) => setCareGoals(e.target.value)} placeholder="每行一项，例如：3个月内把空腹血糖控制到 6.1 以下" />
            </label>
            <label>
              <span>长期红旗提醒</span>
              <textarea value={redFlagNotes} onChange={(e) => setRedFlagNotes(e.target.value)} placeholder="每行一项，例如：若胸痛持续超过 15 分钟需立即就医" />
            </label>
            <label>
              <span>个人背景 / 偏好</span>
              <textarea value={personalContext} onChange={(e) => setPersonalContext(e.target.value)} placeholder="每行一项，例如：家属协助记录血压；偏好用清单式回答" />
            </label>
          </div>
        </>
      ) : null}

      {careProfile.patientBaseline.recentSymptoms.length > 0 ? (
        <div className="agent-care-inline-list">
          <strong>近期症状 / 体征</strong>
          <div className="agent-chip-list">
            {careProfile.patientBaseline.recentSymptoms.map((item) => (
              <span key={item.id} className={`agent-chip chip-${(item.alertLevel ?? "normal").toLowerCase()}`}>
                {item.label}
                {item.value ? ` ${item.value}${item.unit ?? ""}` : ""}
              </span>
            ))}
          </div>
        </div>
      ) : null}

      {error ? <p className="status-text error">{error}</p> : null}
      {editing ? (
        <div className="agent-care-actions">
          <button className="btn btn-primary btn-small" type="button" onClick={handleSave} disabled={saving}>
            {saving ? "保存中..." : "保存慢病画像"}
          </button>
        </div>
      ) : null}
    </section>
  );
}
