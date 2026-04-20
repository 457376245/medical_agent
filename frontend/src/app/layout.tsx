"use client";

import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import "./globals.css";
import { Providers } from "./providers";
import { UserTopBar } from "../components/layout/UserTopBar";
import { RouteProgress } from "../components/shared/RouteProgress";
import { AuthGuard } from "../components/auth/AuthGuard";

const PUBLIC_PATHS = ["/login", "/register"];

function AppContent({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const isPublic = PUBLIC_PATHS.includes(pathname);

  if (isPublic) {
    return <>{children}</>;
  }

  return (
    <AuthGuard>
      <div className="app-shell">
        <UserTopBar />
        {children}
      </div>
    </AuthGuard>
  );
}

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>
        <Providers>
          <RouteProgress />
          <AppContent>{children}</AppContent>
        </Providers>
      </body>
    </html>
  );
}
