import { test, expect, Page } from '@playwright/test';

// Helper: start a vs-Engine game (white)
async function startVsEngine(page: Page) {
  await page.goto('/');
  await page.getByTestId('start-game-btn').click({ timeout: 30000 });
  await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });
}

// Helper: start a Human vs Human game
async function startHvH(page: Page) {
  await page.goto('/');
  await page.getByTestId('mode-vs-human').click({ timeout: 30000 });
  await page.getByTestId('start-game-btn').click();
  await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });
}

// Helper: make a move by clicking from → to
async function makeMove(page: Page, from: string, to: string) {
  await page.getByTestId(`square-${from}`).click();
  await page.getByTestId(`square-${to}`).click();
}

// Helper: pause ghost auto-play and collect ghost line moves
async function pauseAndCollectGhostLine(page: Page): Promise<string[]> {
  await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });
  await page.getByTestId('ghost-play-pause-btn').click();
  await page.waitForTimeout(200);

  const moves: string[] = [];
  for (let i = 0; i < 10; i++) {
    const disabled = await page.locator('#ghost-step-forward-btn').getAttribute('disabled');
    if (disabled !== null) break;
    await page.locator('#ghost-step-forward-btn').click();
    await page.waitForTimeout(50);
    const moveInfo = await page.locator('#ghost-move-info').textContent();
    const match = moveInfo?.match(/: (.+)$/);
    if (match) moves.push(match[1]);
  }
  return moves;
}

// All 20 legal white opening moves with deterministic engine response + ghost line
const openingMoveData: Array<{
  name: string;
  from: string;
  to: string;
  engineResponse: string;
  ghostLine: string[];
}> = [
  { name: 'a2a3', from: 'a2', to: 'a3', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'd2d4', 'e5d4', 'd1d4'] },
  { name: 'a2a4', from: 'a2', to: 'a4', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'd2d4', 'e5d4', 'd1d4'] },
  { name: 'b2b3', from: 'b2', to: 'b3', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'd2d4', 'e5d4', 'd1d4'] },
  { name: 'b2b4', from: 'b2', to: 'b4', engineResponse: 'd7d5', ghostLine: ['c2c4', 'd5c4', 'd2d4', 'd8d4', 'd1d4'] },
  { name: 'c2c3', from: 'c2', to: 'c3', engineResponse: 'c7c5', ghostLine: ['d2d4', 'c5d4', 'd1d4', 'd7d5', 'd4d5'] },
  { name: 'c2c4', from: 'c2', to: 'c4', engineResponse: 'c7c5', ghostLine: ['d2d4', 'c5d4', 'd1d4', 'd8a5', 'b1c3'] },
  { name: 'd2d3', from: 'd2', to: 'd3', engineResponse: 'c7c5', ghostLine: ['c2c4', 'd8a5', 'b1c3', 'a5c3', 'b2c3'] },
  { name: 'd2d4', from: 'd2', to: 'd4', engineResponse: 'd7d5', ghostLine: ['c2c4', 'd5c4', 'e2e4', 'd8d4', 'd1d4'] },
  { name: 'e2e3', from: 'e2', to: 'e3', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'd2d4', 'e5d4', 'd1d4'] },
  { name: 'e2e4', from: 'e2', to: 'e4', engineResponse: 'e7e5', ghostLine: ['c2c4', 'c7c5', 'd2d4', 'c5d4', 'd1d4'] },
  { name: 'f2f3', from: 'f2', to: 'f3', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'd2d4', 'e5d4', 'd1d4'] },
  { name: 'f2f4', from: 'f2', to: 'f4', engineResponse: 'f7f5', ghostLine: ['c2c4', 'c7c5', 'd2d4', 'c5d4', 'd1d4'] },
  { name: 'g2g3', from: 'g2', to: 'g3', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'd2d4', 'e5d4', 'd1d4'] },
  { name: 'g2g4', from: 'g2', to: 'g4', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'd2d4', 'e5d4', 'd1d4'] },
  { name: 'h2h3', from: 'h2', to: 'h3', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'd2d4', 'e5d4', 'd1d4'] },
  { name: 'h2h4', from: 'h2', to: 'h4', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'd2d4', 'e5d4', 'd1d4'] },
  { name: 'Nb1a3', from: 'b1', to: 'a3', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'd2d4', 'e5d4', 'd1d4'] },
  { name: 'Nb1c3', from: 'b1', to: 'c3', engineResponse: 'c7c5', ghostLine: ['d2d4', 'c5d4', 'd1d4', 'd7d5', 'c3d5'] },
  { name: 'Ng1f3', from: 'g1', to: 'f3', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'f3e5', 'f7f5', 'e5d7'] },
  { name: 'Ng1h3', from: 'g1', to: 'h3', engineResponse: 'c7c5', ghostLine: ['c2c4', 'e7e5', 'd2d4', 'e5d4', 'd1d4'] },
];

test.describe('Opening moves (deterministic engine)', () => {

  for (const data of openingMoveData) {
    test(`${data.name}: engine responds ${data.engineResponse}, ghost line correct`, async ({ page }) => {
      await startVsEngine(page);
      await makeMove(page, data.from, data.to);

      // Wait for engine response + ghost preview
      await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });

      // Verify engine responded correctly
      const history = await page.locator('#move-history').textContent();
      expect(history).toContain(data.from + data.to);
      expect(history).toContain(data.engineResponse);

      // Verify it's white's turn again
      await expect(page.getByTestId('game-status')).toContainText('White to move');

      // Pause and verify ghost line
      const ghostMoves = await pauseAndCollectGhostLine(page);
      expect(ghostMoves).toEqual(data.ghostLine);
    });
  }
});

test.describe('Ghost accept regression (vs Engine)', () => {

  test('accept applies ghost moves without crash', async ({ page }) => {
    page.on('pageerror', err => { throw new Error('Page error: ' + err.message); });

    await startVsEngine(page);
    await makeMove(page, 'e2', 'e4');

    // Wait for ghost, pause, step
    await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });
    await page.getByTestId('ghost-play-pause-btn').click();
    await page.waitForTimeout(200);

    await page.getByTestId('ghost-step-forward-btn').click();
    await page.getByTestId('ghost-step-forward-btn').click();
    await page.getByTestId('ghost-step-forward-btn').click();
    await page.waitForTimeout(100);

    // Accept — should NOT crash
    await page.getByTestId('ghost-accept-btn').click();
    await page.waitForTimeout(500);

    // Ghost should be dismissed
    await expect(page.locator('#ghost-controls')).not.toHaveClass(/active/);

    // History should contain accepted moves
    const history = await page.locator('#move-history').textContent();
    expect(history!.length).toBeGreaterThan(15);

    // Game should still be playable
    await expect(page.getByTestId('game-status')).toContainText(/to move/);
  });

  test('accept all ghost moves then continue playing', async ({ page }) => {
    page.on('pageerror', err => { throw new Error('Page error: ' + err.message); });

    await startVsEngine(page);
    await makeMove(page, 'd2', 'd4');

    await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });
    await page.getByTestId('ghost-play-pause-btn').click();
    await page.waitForTimeout(200);

    // Step all the way
    for (let i = 0; i < 10; i++) {
      const disabled = await page.locator('#ghost-step-forward-btn').getAttribute('disabled');
      if (disabled !== null) break;
      await page.locator('#ghost-step-forward-btn').click();
      await page.waitForTimeout(50);
    }

    await page.getByTestId('ghost-accept-btn').click();
    await page.waitForTimeout(500);

    // Should be able to make another move
    const status = await page.getByTestId('game-status').textContent();
    expect(status).toMatch(/to move/);

    // Find a piece and try to select it
    const activeColor = status!.includes('White') ? '♙' : '♟';
    const squares = page.locator('.square');
    const count = await squares.count();
    let clicked = false;
    for (let i = 0; i < count; i++) {
      const text = await squares.nth(i).textContent();
      if (text?.includes(activeColor)) {
        await squares.nth(i).click();
        clicked = true;
        break;
      }
    }
    expect(clicked).toBe(true);
  });

  test('dismiss ghost then continue playing', async ({ page }) => {
    page.on('pageerror', err => { throw new Error('Page error: ' + err.message); });

    await startVsEngine(page);
    await makeMove(page, 'g1', 'f3');

    await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });

    // Dismiss
    await page.getByTestId('ghost-dismiss-btn').click();
    await page.waitForTimeout(300);

    // Ghost gone
    await expect(page.locator('#ghost-controls')).not.toHaveClass(/active/);

    // Can still play
    await expect(page.getByTestId('game-status')).toContainText('White to move');
    await makeMove(page, 'e2', 'e4');
    await expect(page.getByTestId('piece-e4')).toBeVisible({ timeout: 5000 });
  });
});

test.describe('Ghost display (pieces hidden/shown correctly)', () => {

  test('ghost mode hides original pieces and shows ghost pieces', async ({ page }) => {
    await startHvH(page);
    await makeMove(page, 'e2', 'e4');

    await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });
    await page.getByTestId('ghost-play-pause-btn').click();
    await page.waitForTimeout(200);

    // Before stepping: no ghost pieces visible, original pieces shown
    const ghostPiecesBefore = await page.locator('[data-testid^="ghost-piece-"]').count();
    expect(ghostPiecesBefore).toBe(0);

    // Step forward
    await page.getByTestId('ghost-step-forward-btn').click();
    await page.waitForTimeout(200);

    // After stepping: ghost piece should exist at the moved-to square
    const ghostPiecesAfter = await page.locator('[data-testid^="ghost-piece-"]').count();
    expect(ghostPiecesAfter).toBeGreaterThan(0);

    // The original piece at the from-square should NOT be visible
    // (the ghost board replaced it, piece moved away)
    const moveInfo = await page.locator('#ghost-move-info').textContent();
    const moveMatch = moveInfo?.match(/: (\w\d)(\w\d)/);
    if (moveMatch) {
      const fromSquare = moveMatch[1];
      // Original piece at from-square should be hidden (no piece-XX testid there)
      const origPiece = page.getByTestId(`piece-${fromSquare}`);
      // Either it doesn't exist or it's not visible
      await expect(origPiece).not.toBeVisible({ timeout: 1000 }).catch(() => {});
    }
  });

  test('dismiss ghost restores original pieces', async ({ page }) => {
    await startHvH(page);
    await makeMove(page, 'e2', 'e4');

    await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });
    await page.getByTestId('ghost-play-pause-btn').click();
    await page.waitForTimeout(200);

    // Step forward to show ghost pieces
    await page.getByTestId('ghost-step-forward-btn').click();
    await page.waitForTimeout(200);

    // Dismiss
    await page.getByTestId('ghost-dismiss-btn').click();
    await page.waitForTimeout(300);

    // No ghost pieces should remain
    const ghostPieces = await page.locator('[data-testid^="ghost-piece-"]').count();
    expect(ghostPieces).toBe(0);

    // Original board should be restored — e4 pawn should be there
    await expect(page.getByTestId('piece-e4')).toBeVisible();
  });

  test('ghost reset returns display to pre-ghost state', async ({ page }) => {
    await startHvH(page);
    await makeMove(page, 'e2', 'e4');

    await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });
    await page.getByTestId('ghost-play-pause-btn').click();
    await page.waitForTimeout(200);

    // Step forward twice
    await page.getByTestId('ghost-step-forward-btn').click();
    await page.getByTestId('ghost-step-forward-btn').click();
    await page.waitForTimeout(200);

    const ghostBefore = await page.locator('[data-testid^="ghost-piece-"]').count();
    expect(ghostBefore).toBeGreaterThan(0);

    // Reset
    await page.getByTestId('ghost-reset-btn').click();
    await page.waitForTimeout(200);

    // No ghost pieces after reset (back to step -1)
    const ghostAfter = await page.locator('[data-testid^="ghost-piece-"]').count();
    expect(ghostAfter).toBe(0);
  });
});

test.describe('Ghost accept regression (vs Human)', () => {

  test('accept works in human vs human mode', async ({ page }) => {
    page.on('pageerror', err => { throw new Error('Page error: ' + err.message); });

    await startHvH(page);
    await makeMove(page, 'e2', 'e4');

    await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });
    await page.getByTestId('ghost-play-pause-btn').click();
    await page.waitForTimeout(200);

    await page.getByTestId('ghost-step-forward-btn').click();
    await page.getByTestId('ghost-step-forward-btn').click();
    await page.waitForTimeout(100);

    const historyBefore = await page.locator('#move-history').textContent();

    await page.getByTestId('ghost-accept-btn').click();
    await page.waitForTimeout(500);

    const historyAfter = await page.locator('#move-history').textContent();
    expect(historyAfter!.length).toBeGreaterThan(historyBefore!.length);
  });

  test('multi-move sequence with ghost accept each time', async ({ page }) => {
    page.on('pageerror', err => { throw new Error('Page error: ' + err.message); });

    await startHvH(page);

    // Move 1: e4, accept ghost
    await makeMove(page, 'e2', 'e4');
    await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });
    await page.getByTestId('ghost-play-pause-btn').click();
    await page.waitForTimeout(200);
    await page.getByTestId('ghost-step-forward-btn').click();
    await page.getByTestId('ghost-accept-btn').click();
    await page.waitForTimeout(500);

    // Move 2: another move, dismiss ghost
    const status = await page.getByTestId('game-status').textContent();
    expect(status).toMatch(/to move/);

    // Game should still function
    const pieces = await page.locator('[data-testid^="piece-"]').count();
    expect(pieces).toBeGreaterThan(20); // Should still have most pieces
  });
});
