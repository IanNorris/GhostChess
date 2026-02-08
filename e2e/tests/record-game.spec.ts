import { test, expect, type Page } from '@playwright/test';

// Record a full Scholar's Mate game with ghost preview demo
// Run: npx playwright test record-game --project chromium

test.use({
  video: { mode: 'on', size: { width: 1100, height: 620 } },
  viewport: { width: 1100, height: 620 },
});

async function makeMove(page: Page, from: string, to: string) {
  await page.getByTestId(`square-${from}`).click();
  await page.waitForTimeout(200);
  await page.getByTestId(`square-${to}`).click();
}

// Wait for ghost preview to appear and auto-play through all steps
async function waitForGhostPreview(page: Page) {
  await expect(page.getByTestId('ghost-controls')).toBeVisible({ timeout: 15000 });
  // Let the ghost auto-play — each step takes ~1500ms
  // Wait for "Preview complete" which means all steps played
  await expect(page.getByTestId('ghost-status-text')).toContainText('Preview complete', { timeout: 30000 });
  // Linger so viewer can see the full ghost line
  await page.waitForTimeout(2000);
}

// Accept ghost and linger to show board state
async function acceptGhost(page: Page) {
  await page.getByTestId('ghost-accept-btn').click({ timeout: 10000 });
  await page.waitForTimeout(1200);
}

// Quick accept — show ghost briefly then move on
async function quickAcceptGhost(page: Page) {
  await expect(page.getByTestId('ghost-controls')).toBeVisible({ timeout: 15000 });
  // Wait a moment for ghost to start, then accept
  await page.waitForTimeout(2500);
  await page.getByTestId('ghost-accept-btn').click({ timeout: 10000 });
  await page.waitForTimeout(800);
}

test("record Scholar's Mate game", async ({ page }) => {
  test.setTimeout(120000);
  await page.goto('/');

  // Show the menu screen
  await page.waitForTimeout(2500);

  // Start Human vs Human game
  await page.getByTestId('mode-vs-human').click({ timeout: 15000 });
  await page.waitForTimeout(800);
  await page.getByTestId('start-game-btn').click();
  await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });

  // Linger on initial board so viewer can take it in
  await page.waitForTimeout(3000);

  // === Move 1: e4 — show full ghost preview on this move ===
  await makeMove(page, 'e2', 'e4');
  await waitForGhostPreview(page);
  await acceptGhost(page);

  // === Move 1... e5 — show ghost briefly ===
  await makeMove(page, 'e7', 'e5');
  await quickAcceptGhost(page);

  // === Move 2: Bc4 — show ghost briefly ===
  await makeMove(page, 'f1', 'c4');
  await quickAcceptGhost(page);

  // === Move 2... Nc6 — show ghost briefly ===
  await makeMove(page, 'b8', 'c6');
  await quickAcceptGhost(page);

  // === Move 3: Qh5 — show full ghost preview (the attack builds) ===
  await makeMove(page, 'd1', 'h5');
  await waitForGhostPreview(page);
  await acceptGhost(page);

  // === Move 3... Nf6 (black tries to defend) — quick accept ===
  await makeMove(page, 'g8', 'f6');
  await quickAcceptGhost(page);

  // === Move 4: Qxf7# — Scholar's Mate! ===
  await makeMove(page, 'h5', 'f7');
  await page.waitForTimeout(1500);

  // Show the checkmate result
  await expect(page.getByTestId('game-status')).toContainText('White wins', { timeout: 5000 });

  // Long linger on checkmate
  await page.waitForTimeout(4000);

  const video = page.video();
  if (video) {
    const videoPath = await video.path();
    console.log(`Video saved at: ${videoPath}`);
  }
});
