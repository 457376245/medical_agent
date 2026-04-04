"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";

/**
 * Slim top progress bar that animates on route changes (App Router compatible).
 * Detects pathname changes via usePathname() and plays a CSS animation.
 */
export function RouteProgress() {
  const pathname = usePathname();
  const [state, setState] = useState<"idle" | "loading" | "finishing">("idle");
  const prevPath = useRef(pathname);

  useEffect(() => {
    if (pathname !== prevPath.current) {
      prevPath.current = pathname;
      setState("loading");
      const timer = setTimeout(() => setState("finishing"), 80);
      return () => clearTimeout(timer);
    }
  }, [pathname]);

  useEffect(() => {
    if (state === "finishing") {
      const timer = setTimeout(() => setState("idle"), 350);
      return () => clearTimeout(timer);
    }
  }, [state]);

  if (state === "idle") return null;

  return (
    <div
      className={`route-progress ${state === "finishing" ? "route-progress-done" : ""}`}
      role="progressbar"
      aria-label="页面加载中"
    />
  );
}
