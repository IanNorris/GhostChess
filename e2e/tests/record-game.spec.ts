import { test, expect, type Page } from '@playwright/test';
import * as path from 'path';

// Record a full Scholar's Mate game (4 moves to checkmate)
// Run: npx playwright test record-game --project chromium

test.use({
  video: { mode: 'on', size: { width: 480, height: 720 } },
  viewport: { width: 480, height: 720 },
});

async function makeMove(page: Page, from: string, to: string) {
  await page.getByTestId(`square-${from}`).click();
  await page.getByTestId(`square-${to}`).click();
}

async function waitForAnimation(page: Page, ms = 900) {
  await page.waitForTimeout(ms);
}

test("record Scholar's Mate game", async ({ page }, testInfo) => {
  await page.goto('/');

  // Start Human vs Human game
  await page.getByTestId('mode-vs-human').click({ timeout: 15000 });
  await page.getByTestId('start-game-btn').click();
  await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });

  // Pause for viewer to see initial board
  await page.waitForTimeout(1500);

  // 1. e4
  await makeMove(page, 'e2', 'e4');
  await waitForAnimation(page);
  await page.getByTestId('ghost-accept-btn').click({ timeout: 10000 });
  await waitForAnimation(page);

  // 1... e5
  await makeMove(page, 'e7', 'e5');
  await waitForAnimation(page);
  await page.getByTestId('ghost-accept-btn').click({ timeout: 10000 });
  await waitForAnimation(page);

  // 2. Bc4
  await makeMove(page, 'f1', 'c4');
  await waitForAnimation(page);
  await page.getByTestId('ghost-accept-btn').click({ timeout: 10000 });
  await waitForAnimation(page);

  // 2... Nc6
  await makeMove(page, 'b8', 'c6');
  await waitForAnimation(page);
  await page.getByTestId('ghost-accept-btn').click({ timeout: 10000 });
  await waitForAnimation(page);

  // 3. Qh5
  await makeMove(page, 'd1', 'h5');
  await waitForAnimation(page);
  await page.getByTestId('ghost-accept-btn').click({ timeout: 10000 });
  await waitForAnimation(page);

  // 3... Nf6 (black tries to defend)
  await makeMove(page, 'g8', 'f6');
  await waitForAnimation(page);
  await page.getByTestId('ghost-accept-btn').click({ timeout: 10000 });
  await waitForAnimation(page);

  // 4. Qxf7# — Scholar's Mate!
  await makeMove(page, 'h5', 'f7');
  await waitForAnimation(page);

  // Wait to show the checkmate result
  await expect(page.getByTestId('game-status')).toContainText('White wins', { timeout: 5000 });
  await page.waitForTimeout(3000);

  // The video is automatically saved by Playwright
  const video = page.video();
  if (video) {
    const videoPath = await video.path();
    console.log(`Video saved at: ${videoPath}`);
  }
});
