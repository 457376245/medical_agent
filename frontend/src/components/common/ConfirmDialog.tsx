"use client";

type ConfirmDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmText: string;
  cancelText?: string;
  tone?: "primary" | "danger";
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function ConfirmDialog({
  open,
  title,
  description,
  confirmText,
  cancelText = "取消",
  tone = "primary",
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  if (!open) {
    return null;
  }

  const confirmClass = tone === "danger" ? "btn btn-danger" : "btn btn-primary";

  return (
    <div className="confirm-dialog-overlay" onClick={onCancel} role="presentation">
      <section
        className="confirm-dialog-panel"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        role="dialog"
        onClick={(event) => event.stopPropagation()}
      >
        <h3 className="panel-title" id="confirm-dialog-title">
          {title}
        </h3>
        <p className="muted panel-text">{description}</p>

        <div className="dialog-actions">
          <button className="btn btn-ghost" type="button" onClick={onCancel} disabled={loading}>
            {cancelText}
          </button>
          <button className={confirmClass} type="button" onClick={onConfirm} disabled={loading}>
            {loading ? "处理中..." : confirmText}
          </button>
        </div>
      </section>
    </div>
  );
}
