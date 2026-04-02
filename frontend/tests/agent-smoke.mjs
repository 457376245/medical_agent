import { chromium } from "playwright";

const baseUrl = process.env.AGENT_PAGE_URL || "http://localhost:3000/agent";

async function runViewport(name, viewport) {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport });

  try {
    await page.goto(baseUrl, { waitUntil: "networkidle" });
    await page.waitForSelector("text=疾病档案对话工作台");

    if (viewport.width <= 680) {
      await page.getByRole("button", { name: "会话栏" }).click();
      await page.waitForSelector(".agent-sidebar-left.agent-drawer-open");
      await page.getByRole("button", { name: "关闭抽屉" }).click();
      await page.getByRole("button", { name: "病例上下文" }).click();
      await page.waitForSelector(".agent-sidebar-right.agent-drawer-open");
    } else {
      await page.waitForSelector(".agent-chat-panel");
      await page.waitForSelector(".agent-sidebar-right");
    }

    console.log(`[ok] ${name}`);
  } finally {
    await browser.close();
  }
}

await runViewport("desktop", { width: 1440, height: 960 });
await runViewport("mobile", { width: 390, height: 844 });
