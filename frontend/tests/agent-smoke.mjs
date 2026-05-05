import { chromium } from "playwright";

const baseUrl = process.env.AGENT_PAGE_URL || "http://localhost:3000/agent";

async function runViewport(name, viewport) {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport });

  try {
    await page.goto(baseUrl, { waitUntil: "networkidle" });
    await page.waitForSelector("text=患者慢病追踪");

    if (viewport.width <= 680) {
      await page.waitForSelector(".agent-patient-nav");
      await page.waitForSelector(".agent-patient-nav-link");
    } else {
      await page.waitForSelector(".agent-patient-hero");
      await page.waitForSelector(".agent-dashboard-grid, .agent-empty-state");
    }

    console.log(`[ok] ${name}`);
  } finally {
    await browser.close();
  }
}

await runViewport("desktop", { width: 1440, height: 960 });
await runViewport("mobile", { width: 390, height: 844 });
