"use client";

import { useState } from "react";
import type { CareSymptomItem } from "./types";

export function SymptomLogPanel({
  symptoms,
  onCreateSymptom,
  profileId,
}: {
  symptoms: CareSymptomItem[];
  onCreateSymptom: (input: {
    label: string;
    value?: string;
    unit?: string;
    alertLevel?: string;
    notes?: string;
    recordedAt?: string;
    diseaseProfileId?: string;
  }) => Promise<void>;
  profileId?: string;
}) {
  const [label, setLabel] = useState("");
  const [value, setValue] = useState("");
  const [unit, setUnit] = useState("");
  const [alertLevel, setAlertLevel] = useState("NORMAL");
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const handleCreate = async () => {
    if (!label.trim()) return;
    setSaving(true);
    setError("");
    try {
      await onCreateSymptom({
        label: label.trim(),
        value: value.trim() || undefined,
        unit: unit.trim() || undefined,
        alertLevel,
        notes: notes.trim() || undefined,
        recordedAt: new Date().toISOString(),
        diseaseProfileId: profileId,
      });
      setLabel("");
      setValue("");
      setUnit("");
      setAlertLevel("NORMAL");
      setNotes("");
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "新增症状记录失败。");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="agent-care-card">
      <div className="agent-care-card-head">
        <div>
          <p className="hero-kicker">自我监测</p>
          <h4>症状 / 体征记录</h4>
        </div>
      </div>

      {symptoms.length > 0 ? (
        <div className="agent-care-stack compact">
          {symptoms.map((item) => (
            <article key={item.id} className={`agent-symptom-item level-${(item.alertLevel ?? "normal").toLowerCase()}`}>
              <strong>{item.label}</strong>
              <p>
                {[item.value ? `${item.value}${item.unit ?? ""}` : "", item.notes, item.recordedAt ? new Date(item.recordedAt).toLocaleString("zh-CN") : ""]
                  .filter(Boolean)
                  .join(" / ")}
              </p>
            </article>
          ))}
        </div>
      ) : (
        <p className="agent-care-empty">暂时还没有家庭测量或症状记录。</p>
      )}

      <div className="agent-care-divider" />

      <div className="agent-inline-grid">
        <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="例如：空腹血糖 / 血压 / 胸闷" />
        <input value={value} onChange={(e) => setValue(e.target.value)} placeholder="数值或严重程度" />
      </div>
      <div className="agent-inline-grid">
        <input value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="单位，可选" />
        <select value={alertLevel} onChange={(e) => setAlertLevel(e.target.value)}>
          <option value="NORMAL">常规</option>
          <option value="WATCH">观察</option>
          <option value="WARNING">警示</option>
          <option value="ALERT">高风险</option>
        </select>
      </div>
      <input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="备注，例如：饭后2小时测量" />

      {error ? <p className="status-text error">{error}</p> : null}
      <div className="agent-care-actions">
        <button className="btn btn-primary btn-small" type="button" onClick={handleCreate} disabled={saving || !label.trim()}>
          {saving ? "记录中..." : "新增记录"}
        </button>
      </div>
    </section>
  );
}
