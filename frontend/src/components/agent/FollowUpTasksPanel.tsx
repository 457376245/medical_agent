"use client";

import { useState } from "react";
import type { FollowUpTask } from "./types";

export function FollowUpTasksPanel({
  tasks,
  onCreateTask,
  onToggleTask,
  profileId,
  recordId,
}: {
  tasks: FollowUpTask[];
  onCreateTask: (input: {
    title: string;
    dueDate?: string;
    priority?: string;
    notes?: string;
    diseaseProfileId?: string;
    recordId?: string;
  }) => Promise<void>;
  onToggleTask: (task: FollowUpTask) => Promise<void>;
  profileId?: string;
  recordId?: string;
}) {
  const [title, setTitle] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [priority, setPriority] = useState("MEDIUM");
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [creating, setCreating] = useState(false);

  const handleCreate = async () => {
    if (!title.trim()) return;
    setSaving(true);
    setError("");
    try {
      await onCreateTask({
        title: title.trim(),
        dueDate: dueDate || undefined,
        priority,
        notes: notes.trim() || undefined,
        diseaseProfileId: profileId,
        recordId,
      });
      setTitle("");
      setDueDate("");
      setPriority("MEDIUM");
      setNotes("");
      setCreating(false);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "创建随访任务失败。");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="agent-care-card">
      <div className="agent-care-card-head">
        <div>
          <p className="hero-kicker">行动闭环</p>
          <h4>随访任务</h4>
        </div>
        <div className="agent-care-head-actions">
          <span className="badge">{tasks.length} 项待办</span>
          <button className="btn btn-ghost btn-small" type="button" onClick={() => setCreating((current) => !current)}>
            {creating ? "收起" : "新增任务"}
          </button>
        </div>
      </div>

      {tasks.length > 0 ? (
        <div className="agent-care-stack">
          {tasks.map((task) => (
            <article key={task.id} className={`agent-task-item status-${(task.status ?? "OPEN").toLowerCase()}`}>
              <div>
                <strong>{task.title}</strong>
                <p>{[task.dueDate, task.priority, task.notes].filter(Boolean).join(" / ") || "待补充说明"}</p>
              </div>
              <button className="btn btn-ghost btn-small" type="button" onClick={() => void onToggleTask(task)}>
                {(task.status ?? "OPEN") === "DONE" ? "恢复" : "完成"}
              </button>
            </article>
          ))}
        </div>
      ) : (
        <p className="agent-care-empty">还没有随访任务，可以先添加复查或复诊事项。</p>
      )}

      {creating ? (
        <>
          <div className="agent-care-divider" />
          <div className="agent-inline-grid">
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="任务标题，例如：两周后复查肝功能" />
            <input value={dueDate} onChange={(e) => setDueDate(e.target.value)} type="date" />
          </div>
          <div className="agent-inline-grid">
            <select value={priority} onChange={(e) => setPriority(e.target.value)}>
              <option value="LOW">低优先级</option>
              <option value="MEDIUM">中优先级</option>
              <option value="HIGH">高优先级</option>
            </select>
            <input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="备注，例如：携带最近3次检验单" />
          </div>
        </>
      ) : null}

      {error ? <p className="status-text error">{error}</p> : null}
      {creating ? (
        <div className="agent-care-actions">
          <button className="btn btn-primary btn-small" type="button" onClick={handleCreate} disabled={saving || !title.trim()}>
            {saving ? "添加中..." : "添加任务"}
          </button>
        </div>
      ) : null}
    </section>
  );
}
