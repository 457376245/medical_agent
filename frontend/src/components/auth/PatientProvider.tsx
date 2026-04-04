"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/api";
import {
  getCurrentPatientId,
  setCurrentPatientId,
} from "../../lib/auth";
import { useAuth } from "./AuthProvider";

export interface Patient {
  id: string;
  name: string;
  relationship: string;
  gender?: string;
  birthDate?: string;
  notes?: string;
  isDefault: boolean;
}

interface PatientContextType {
  patients: Patient[];
  currentPatient: Patient | null;
  switchPatient: (patientId: string) => void;
  refreshPatients: () => Promise<void>;
  isLoading: boolean;
}

const PatientContext = createContext<PatientContextType | null>(null);

export function PatientProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const queryClient = useQueryClient();
  const [patients, setPatients] = useState<Patient[]>([]);
  const [currentPatient, setCurrentPatient] = useState<Patient | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const fetchPatients = useCallback(async () => {
    try {
      const res = await apiFetch<{ patients: Patient[] }>("/patients");
      const list = res.data.patients || [];
      setPatients(list);

      const savedId = getCurrentPatientId();
      const saved = list.find((p) => p.id === savedId);
      const defaultP = list.find((p) => p.isDefault);
      const active = saved || defaultP || list[0] || null;

      setCurrentPatient(active);
      if (active) {
        setCurrentPatientId(active.id);
      }
    } catch {
      // If fetch fails (e.g. not authenticated), just leave empty
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isAuthenticated) {
      fetchPatients();
    } else {
      setIsLoading(false);
    }
  }, [isAuthenticated, fetchPatients]);

  const switchPatient = useCallback(
    (patientId: string) => {
      const target = patients.find((p) => p.id === patientId);
      if (target) {
        setCurrentPatient(target);
        setCurrentPatientId(target.id);
        queryClient.clear();
      }
    },
    [patients, queryClient],
  );

  return (
    <PatientContext.Provider
      value={{
        patients,
        currentPatient,
        switchPatient,
        refreshPatients: fetchPatients,
        isLoading,
      }}
    >
      {children}
    </PatientContext.Provider>
  );
}

export function usePatient() {
  const ctx = useContext(PatientContext);
  if (!ctx) throw new Error("usePatient must be used within PatientProvider");
  return ctx;
}
