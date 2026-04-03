import { Noto_Sans_SC, Noto_Serif_SC } from "next/font/google";
import type { ReactNode } from "react";
import "./globals.css";
import { Providers } from "./providers";
import { UserTopBar } from "../components/layout/UserTopBar";

const bodyFont = Noto_Sans_SC({
  subsets: ["latin"],
  variable: "--font-body",
  weight: ["400", "500", "700"],
});

const headingFont = Noto_Serif_SC({
  subsets: ["latin"],
  variable: "--font-heading",
  weight: ["400", "600", "700"],
});

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN">
      <body className={`${bodyFont.variable} ${headingFont.variable}`}>
        <Providers>
          <div className="app-shell">
            <UserTopBar />
            {children}
          </div>
        </Providers>
      </body>
    </html>
  );
}
