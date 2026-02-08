export function MedicationPlanConfirmDialog({
  open,
  onConfirm,
}: {
  open: boolean;
  onConfirm: () => void;
}) {
  if (!open) return null;
  return (
    <dialog
      open
      style={{
        border: "1px solid #cfe0ea",
        borderRadius: 14,
        padding: 16,
        boxShadow: "0 20px 40px rgba(18,39,53,.15)",
      }}
    >
      <p>AI 生成内容仅供参考，请在保存前再次人工确认。</p>
      <button className="btn btn-primary" type="button" onClick={onConfirm}>
        确认并保存
      </button>
    </dialog>
  );
}
