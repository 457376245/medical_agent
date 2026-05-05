"use client";

import Link from "next/link";
import type { Route } from "next";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Activity, BarChart3, CheckSquare, ChevronDown, MessageSquare, ShieldCheck } from "lucide-react";
import { AppSelect, type AppSelectOption } from "../common/AppSelect";
import type { AgentProfile } from "./types";

const NAV_ITEMS = [
  { href: "/agent", label: "总览", icon: Activity },
  { href: "/agent/chat", label: "咨询", icon: MessageSquare },
  { href: "/agent/trends", label: "趋势", icon: BarChart3 },
  { href: "/agent/tasks", label: "随访", icon: CheckSquare },
];

export function AgentPageFrame({
  profiles,
  selectedProfile,
  children,
}: {
  profiles: AgentProfile[];
  selectedProfile?: AgentProfile;
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const selectedProfileId = selectedProfile?.profileId ?? searchParams.get("profileId") ?? "";

  const options: AppSelectOption[] = profiles.length > 0
    ? profiles.map((profile) => ({
        value: profile.profileId,
        label: `${profile.diseaseName} · ${profile.recordCount} 份报告`,
      }))
    : [{ value: "", label: "暂无疾病档案", disabled: true }];

  const withProfile = (href: string, profileId = selectedProfileId) => {
    if (!profileId) return href;
    return `${href}?profileId=${encodeURIComponent(profileId)}`;
  };

  const handleProfileChange = (nextProfileId: string) => {
    if (!nextProfileId) return;
    router.push(withProfile(pathname, nextProfileId) as Route);
  };

  return (
    <main className="agent-page-shell">
      {/* Compact top bar: disease selector + nav tabs combined */}
      <header className="agent-topbar">
        <div className="agent-topbar-left">
          <div className="agent-topbar-brand">
            <Activity className="w-5 h-5" aria-hidden="true" />
            <span className="agent-topbar-label">慢病追踪</span>
          </div>
          <div className="agent-topbar-divider" />
          <div className="agent-topbar-selector">
            <AppSelect
              ariaLabel="选择疾病档案"
              value={selectedProfileId}
              options={options}
              disabled={profiles.length === 0}
              rootClassName="agent-compact-select"
              triggerClassName="agent-compact-select-trigger"
              menuClassName="agent-select-menu"
              onChange={handleProfileChange}
            />
          </div>
          <div className="agent-topbar-safety">
            <ShieldCheck className="w-3.5 h-3.5" aria-hidden="true" />
            <span>仅辅助理解，诊疗请咨询医师</span>
          </div>
        </div>
        <nav className="agent-topbar-nav" aria-label="Agent 功能导航">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            const active = pathname === item.href;
            return (
              <Link key={item.href} href={withProfile(item.href) as Route} className={`agent-topbar-nav-link ${active ? "active" : ""}`}>
                <Icon className="w-4 h-4" aria-hidden="true" />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </header>

      {/* Main content area — gets maximum space */}
      <div className="agent-page-content">
        {children}
      </div>
    </main>
  );
}
