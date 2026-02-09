"use client";

import { ConfirmDialog } from "../common/ConfirmDialog";

export function MedicationPlanConfirmDialog({
  open,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  onConfirm: () => void;
  onCancel?: () => void;
}) {
  const handleCancel = onCancel ?? (() => {});
  return open ? (
    <ConfirmDialog
      open={open}
      title="确认保存用药方案"
      description="AI 生成内容仅供参考，请在保存前再次人工确认。"
      confirmText="确认并保存"
      onCancel={handleCancel}
      onConfirm={onConfirm}
    />
  ) : null;
}
