"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ConfirmDialog } from "../common/ConfirmDialog";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

type DeleteRecordButtonProps = {
  recordId: string;
  profileId: string;
  isSelected: boolean;
};

export function DeleteRecordButton({ recordId, profileId, isSelected }: DeleteRecordButtonProps) {
  const router = useRouter();
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);

  const onDelete = async () => {
    setIsDeleting(true);
    setError("");
    try {
      const response = await fetch(`${API_BASE}/records/${recordId}`, {
        method: "DELETE",
      });
      if (!response.ok) {
        throw new Error("delete failed");
      }
      setConfirmOpen(false);
      if (isSelected) {
        router.push(`/profiles/${profileId}`);
      }
      router.refresh();
    } catch {
      setError("删除失败，请稍后重试。");
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="timeline-item-delete">
      <button className="btn btn-danger btn-small" type="button" onClick={() => setConfirmOpen(true)} disabled={isDeleting}>
        {isDeleting ? "删除中..." : "删除报告"}
      </button>
      {error ? <p className="status-text error">{error}</p> : null}
      <ConfirmDialog
        open={confirmOpen}
        title="确认删除报告"
        description="删除后将不可恢复，且会移除该报告关联的解析数据与文件。"
        confirmText="确认删除"
        tone="danger"
        loading={isDeleting}
        onCancel={() => !isDeleting && setConfirmOpen(false)}
        onConfirm={onDelete}
      />
    </div>
  );
}

