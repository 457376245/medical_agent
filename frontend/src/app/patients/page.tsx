"use client";

import { useState, type FormEvent } from "react";
import { usePatient, type Patient } from "../../components/auth/PatientProvider";
import { AppSelect, type AppSelectOption } from "../../components/common/AppSelect";
import { apiFetch } from "../../lib/api";
import { ConfirmDialog } from "../../components/common/ConfirmDialog";

type EditingPatient = {
  id: string;
  name: string;
  relationship: string;
  gender: string;
  birthDate: string;
  notes: string;
};

const RELATIONSHIP_OPTIONS: AppSelectOption[] = [
  { value: "本人", label: "本人" },
  { value: "家人", label: "家人" },
  { value: "父亲", label: "父亲" },
  { value: "母亲", label: "母亲" },
  { value: "配偶", label: "配偶" },
  { value: "子女", label: "子女" },
  { value: "其他", label: "其他" },
];

const GENDER_OPTIONS: AppSelectOption[] = [
  { value: "", label: "未填写" },
  { value: "男", label: "男" },
  { value: "女", label: "女" },
];

export default function PatientsPage() {
  const { patients, currentPatient, switchPatient, refreshPatients } = usePatient();
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<EditingPatient | null>(null);
  const [deletingPatient, setDeletingPatient] = useState<Patient | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");

  // Create form state
  const [name, setName] = useState("");
  const [relationship, setRelationship] = useState("家人");
  const [gender, setGender] = useState("");
  const [birthDate, setBirthDate] = useState("");
  const [notes, setNotes] = useState("");

  function resetForm() {
    setName("");
    setRelationship("家人");
    setGender("");
    setBirthDate("");
    setNotes("");
    setError("");
  }

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError("");
    setIsSubmitting(true);
    try {
      await apiFetch("/patients", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, relationship, gender: gender || null, birthDate: birthDate || null, notes: notes || null }),
      });
      await refreshPatients();
      resetForm();
      setShowCreate(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "创建失败");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleUpdate(e: FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setError("");
    setIsSubmitting(true);
    try {
      await apiFetch(`/patients/${editing.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: editing.name,
          relationship: editing.relationship,
          gender: editing.gender || null,
          birthDate: editing.birthDate || null,
          notes: editing.notes || null,
        }),
      });
      await refreshPatients();
      setEditing(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "更新失败");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!deletingPatient) return;
    setIsSubmitting(true);
    try {
      await apiFetch(`/patients/${deletingPatient.id}`, { method: "DELETE" });
      await refreshPatients();
      setDeletingPatient(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "删除失败");
      setDeletingPatient(null);
    } finally {
      setIsSubmitting(false);
    }
  }

  function startEdit(p: Patient) {
    setEditing({
      id: p.id,
      name: p.name,
      relationship: p.relationship,
      gender: p.gender ?? "",
      birthDate: p.birthDate ?? "",
      notes: p.notes ?? "",
    });
    setShowCreate(false);
    setError("");
  }

  return (
    <div className="patients-page">
      <div className="patients-header">
        <h1>病人管理</h1>
        <button
          className="btn btn-primary"
          type="button"
          onClick={() => {
            setShowCreate(true);
            setEditing(null);
            resetForm();
          }}
        >
          添加病人
        </button>
      </div>

      {error && <p className="patients-error">{error}</p>}

      {showCreate && (
        <form className="patient-form" onSubmit={handleCreate}>
          <h3>添加新病人</h3>
          <div className="patient-form-grid">
            <label className="patient-form-field">
              <span>姓名</span>
              <input type="text" value={name} onChange={(e) => setName(e.target.value)} required />
            </label>
            <div className="patient-form-field">
              <span>关系</span>
              <AppSelect
                ariaLabel="关系"
                value={relationship}
                options={RELATIONSHIP_OPTIONS}
                rootClassName="patient-form-select"
                triggerClassName="patient-form-select-trigger"
                menuClassName="patient-form-select-menu"
                onChange={setRelationship}
              />
            </div>
            <div className="patient-form-field">
              <span>性别</span>
              <AppSelect
                ariaLabel="性别"
                value={gender}
                options={GENDER_OPTIONS}
                rootClassName="patient-form-select"
                triggerClassName="patient-form-select-trigger"
                menuClassName="patient-form-select-menu"
                onChange={setGender}
              />
            </div>
            <label className="patient-form-field">
              <span>出生日期</span>
              <input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} />
            </label>
            <label className="patient-form-field patient-form-field-full">
              <span>备注</span>
              <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} />
            </label>
          </div>
          <div className="patient-form-actions">
            <button className="btn btn-ghost" type="button" onClick={() => setShowCreate(false)}>
              取消
            </button>
            <button className="btn btn-primary" type="submit" disabled={isSubmitting}>
              {isSubmitting ? "保存中..." : "保存"}
            </button>
          </div>
        </form>
      )}

      {editing && (
        <form className="patient-form" onSubmit={handleUpdate}>
          <h3>编辑病人</h3>
          <div className="patient-form-grid">
            <label className="patient-form-field">
              <span>姓名</span>
              <input
                type="text"
                value={editing.name}
                onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                required
              />
            </label>
            <div className="patient-form-field">
              <span>关系</span>
              <AppSelect
                ariaLabel="关系"
                value={editing.relationship}
                options={RELATIONSHIP_OPTIONS}
                rootClassName="patient-form-select"
                triggerClassName="patient-form-select-trigger"
                menuClassName="patient-form-select-menu"
                onChange={(nextValue) => setEditing({ ...editing, relationship: nextValue })}
              />
            </div>
            <div className="patient-form-field">
              <span>性别</span>
              <AppSelect
                ariaLabel="性别"
                value={editing.gender}
                options={GENDER_OPTIONS}
                rootClassName="patient-form-select"
                triggerClassName="patient-form-select-trigger"
                menuClassName="patient-form-select-menu"
                onChange={(nextValue) => setEditing({ ...editing, gender: nextValue })}
              />
            </div>
            <label className="patient-form-field">
              <span>出生日期</span>
              <input type="date" value={editing.birthDate} onChange={(e) => setEditing({ ...editing, birthDate: e.target.value })} />
            </label>
            <label className="patient-form-field patient-form-field-full">
              <span>备注</span>
              <textarea value={editing.notes} onChange={(e) => setEditing({ ...editing, notes: e.target.value })} rows={2} />
            </label>
          </div>
          <div className="patient-form-actions">
            <button className="btn btn-ghost" type="button" onClick={() => setEditing(null)}>
              取消
            </button>
            <button className="btn btn-primary" type="submit" disabled={isSubmitting}>
              {isSubmitting ? "保存中..." : "保存修改"}
            </button>
          </div>
        </form>
      )}

      <div className="patients-list">
        {patients.map((p) => (
          <div key={p.id} className={`patient-card ${currentPatient?.id === p.id ? "patient-card-active" : ""}`}>
            <div className="patient-card-info">
              <strong>{p.name}</strong>
              <span className="patient-card-rel">{p.relationship}</span>
              {p.isDefault && <span className="patient-card-badge">默认</span>}
              {p.gender && <span className="patient-card-meta">{p.gender}</span>}
              {p.birthDate && <span className="patient-card-meta">{p.birthDate}</span>}
            </div>
            <div className="patient-card-actions">
              {currentPatient?.id !== p.id && (
                <button className="btn btn-ghost btn-small" type="button" onClick={() => switchPatient(p.id)}>
                  切换
                </button>
              )}
              <button className="btn btn-ghost btn-small" type="button" onClick={() => startEdit(p)}>
                编辑
              </button>
              {!p.isDefault && (
                <button
                  className="btn btn-ghost btn-small"
                  type="button"
                  style={{ color: "var(--danger)" }}
                  onClick={() => setDeletingPatient(p)}
                >
                  删除
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      <ConfirmDialog
        open={Boolean(deletingPatient)}
        title="确认删除病人"
        description={`确认删除"${deletingPatient?.name ?? ""}"吗？该病人下的所有疾病档案和记录将无法访问。`}
        confirmText="确认删除"
        tone="danger"
        loading={isSubmitting}
        onCancel={() => setDeletingPatient(null)}
        onConfirm={handleDelete}
      />
    </div>
  );
}
