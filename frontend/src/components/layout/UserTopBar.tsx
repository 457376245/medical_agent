"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "../auth/AuthProvider";
import { usePatient } from "../auth/PatientProvider";
import { BatchUploadDialog } from "./BatchUploadDialog";

type OpenUploadDialogDetail = {
  diseaseProfileId?: string;
  diseaseName?: string;
};

export function UserTopBar() {
  const router = useRouter();
  const { user, logout } = useAuth();
  const { patients, currentPatient, switchPatient } = usePatient();
  const [patientMenuOpen, setPatientMenuOpen] = useState(false);
  const patientMenuRef = useRef<HTMLDivElement | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogPrefill, setDialogPrefill] = useState<OpenUploadDialogDetail>({});

  const openDialog = useCallback((detail?: OpenUploadDialogDetail) => {
    setDialogPrefill(detail ?? {});
    setDialogOpen(true);
  }, []);

  const closeDialog = () => setDialogOpen(false);

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<OpenUploadDialogDetail>).detail;
      openDialog(detail);
    };
    window.addEventListener("open-upload-dialog", handler);
    return () => window.removeEventListener("open-upload-dialog", handler);
  }, [openDialog]);

  useEffect(() => {
    const onPointerDown = (event: MouseEvent) => {
      const target = event.target as Node | null;
      if (!target) return;
      if (patientMenuRef.current && !patientMenuRef.current.contains(target)) {
        setPatientMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, []);

  return (
    <>
      <header className="top-header top-header-minimal">
        <div className="header-search">
          <svg className="search-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M11 19C15.4183 19 19 15.4183 19 11C19 6.58172 15.4183 3 11 3C6.58172 3 3 6.58172 3 11C3 15.4183 6.58172 19 11 19Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M20.9999 21L16.6499 16.65" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <input type="text" placeholder="搜索报告、疾病..." className="search-input" />
        </div>

        <div className="header-actions">
          <div className="patient-selector" ref={patientMenuRef}>
            <button
              className="patient-selector-trigger"
              type="button"
              onClick={() => setPatientMenuOpen((prev) => !prev)}
            >
              {currentPatient?.name ?? "选择病人"}
              <span className="dialog-select-caret" aria-hidden="true" />
            </button>
            {patientMenuOpen && (
              <ul className="patient-selector-menu">
                {patients.map((p) => (
                  <li key={p.id}>
                    <button
                      className={`patient-selector-item ${currentPatient?.id === p.id ? "active" : ""}`}
                      type="button"
                      onClick={() => {
                        switchPatient(p.id);
                        setPatientMenuOpen(false);
                      }}
                    >
                      <span>{p.name}</span>
                      {p.isDefault && <span className="patient-selector-default">本人</span>}
                    </button>
                  </li>
                ))}
                <li style={{ borderTop: "1px solid var(--line)", margin: "4px 0" }} />
                <li>
                  <Link
                    href="/patients"
                    className="patient-selector-item"
                    onClick={() => setPatientMenuOpen(false)}
                    style={{ textDecoration: "none" }}
                  >
                    管理病人
                  </Link>
                </li>
              </ul>
            )}
          </div>

          <button className="action-btn action-btn-upload minimal-upload" type="button" onClick={() => openDialog()}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 4V16M12 4L8 8M12 4L16 8M4 20H20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            上传
          </button>
          <Link className="action-btn action-btn-agent minimal-agent" href="/agent">
            AI 智能分析
          </Link>

          <div className="user-menu">
            <span className="user-name">{user?.displayName ?? ""}</span>
            <button className="logout-btn" type="button" onClick={logout}>
              退出
            </button>
          </div>
        </div>
      </header>

      {dialogOpen && (
        <BatchUploadDialog
          open={dialogOpen}
          prefilledDiseaseId={dialogPrefill.diseaseProfileId}
          prefilledDiseaseName={dialogPrefill.diseaseName}
          onClose={closeDialog}
          onUploadComplete={() => router.refresh()}
        />
      )}
    </>
  );
}
