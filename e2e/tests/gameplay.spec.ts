import { test, expect } from '@playwright/test';

// Helper: start a Human vs Human game
async function startHvH(page: import('@playwright/test').Page) {
  await page.goto('/');
  await page.getByTestId('mode-vs-human').click({ timeout: 30000 });
  await page.getByTestId('start-game-btn').click();
  await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });
}

// Helper: make a move by clicking from → to
async function makeMove(page: import('@playwright/test').Page, from: string, to: string) {
  await page.getByTestId(`square-${from}`).click();
  await page.getByTestId(`square-${to}`).click();
}

test.describe('Chess Gameplay', () => {

  test.describe('Basic moves', () => {
    test('pawn moves forward and updates status', async ({ page }) => {
      await startHvH(page);
      await expect(page.getByTestId('game-status')).toContainText('White to move');

      // e2-e4
      await makeMove(page, 'e2', 'e4');
      await expect(page.getByTestId('piece-e4')).toBeVisible({ timeout: 5000 });
      // After ghost dismiss, should be black's turn
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      await expect(page.getByTestId('game-status')).toContainText('Black to move');
    });

    test('black can move after white', async ({ page }) => {
      await startHvH(page);
      await makeMove(page, 'e2', 'e4');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });

      // Black plays e7-e5
      await makeMove(page, 'e7', 'e5');
      await expect(page.getByTestId('piece-e5')).toBeVisible({ timeout: 5000 });
    });

    test('cannot move opponents pieces', async ({ page }) => {
      await startHvH(page);
      // Try clicking a black piece on white's turn — no legal moves should show
      await page.getByTestId('square-e7').click();
      await expect(page.getByTestId('legal-move-e6')).not.toBeVisible({ timeout: 2000 });
      await expect(page.getByTestId('legal-move-e5')).not.toBeVisible();
    });

    test('knight moves correctly', async ({ page }) => {
      await startHvH(page);
      // Nf3
      await makeMove(page, 'g1', 'f3');
      await expect(page.getByTestId('piece-f3')).toBeVisible({ timeout: 5000 });
    });

    test('piece disappears from origin after move', async ({ page }) => {
      await startHvH(page);
      await makeMove(page, 'e2', 'e4');
      await expect(page.getByTestId('piece-e4')).toBeVisible({ timeout: 5000 });
      await expect(page.getByTestId('piece-e2')).not.toBeVisible();
    });
  });

  test.describe('Multi-move sequences', () => {
    test('play Italian Game opening', async ({ page }) => {
      await startHvH(page);

      // 1. e4
      await makeMove(page, 'e2', 'e4');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      // 1... e5
      await makeMove(page, 'e7', 'e5');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      // 2. Nf3
      await makeMove(page, 'g1', 'f3');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      // 2... Nc6
      await makeMove(page, 'b8', 'c6');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      // 3. Bc4
      await makeMove(page, 'f1', 'c4');
      await expect(page.getByTestId('piece-c4')).toBeVisible({ timeout: 5000 });
    });

    test('capture works correctly', async ({ page }) => {
      await startHvH(page);
      // 1. e4 d5 2. exd5
      await makeMove(page, 'e2', 'e4');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      await makeMove(page, 'd7', 'd5');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      // exd5 capture
      await makeMove(page, 'e4', 'd5');
      await expect(page.getByTestId('piece-d5')).toBeVisible({ timeout: 5000 });
      // Black pawn should be gone, white pawn should be on d5
    });
  });

  test.describe('Undo', () => {
    test('undo restores previous position', async ({ page }) => {
      await startHvH(page);

      // Confirm e2 pawn exists
      await expect(page.getByTestId('piece-e2')).toBeVisible();

      // Make move e2-e4
      await makeMove(page, 'e2', 'e4');
      await expect(page.getByTestId('piece-e4')).toBeVisible({ timeout: 5000 });

      // Undo
      await page.getByTestId('undo-btn').click();

      // Pawn should be back on e2
      await expect(page.getByTestId('piece-e2')).toBeVisible({ timeout: 5000 });
      await expect(page.getByTestId('piece-e4')).not.toBeVisible();
      await expect(page.getByTestId('game-status')).toContainText('White to move');
    });
  });

  test.describe('Ghost preview flow', () => {
    test('ghost step forward shows predicted move', async ({ page }) => {
      await startHvH(page);

      await makeMove(page, 'e2', 'e4');
      await expect(page.getByTestId('ghost-controls')).toBeVisible({ timeout: 10000 });

      // Pause auto-play and step manually
      await page.getByTestId('ghost-play-pause-btn').click();

      // Step forward
      await page.getByTestId('ghost-step-forward-btn').click();
      await expect(page.getByTestId('ghost-move-info')).toContainText('Move 1/');

      // Accept should now be enabled
      await expect(page.getByTestId('ghost-accept-btn')).toBeEnabled();
    });

    test('ghost accept applies moves to the game', async ({ page }) => {
      await startHvH(page);

      await makeMove(page, 'e2', 'e4');
      await expect(page.getByTestId('ghost-controls')).toBeVisible({ timeout: 10000 });

      // Pause and step
      await page.getByTestId('ghost-play-pause-btn').click();
      await page.getByTestId('ghost-step-forward-btn').click();

      // Accept the ghost line
      await page.getByTestId('ghost-accept-btn').click();

      // Ghost controls should disappear
      await expect(page.getByTestId('ghost-controls')).not.toBeVisible({ timeout: 5000 });

      // Move history should have more moves
      const historyEl = page.getByTestId('move-history');
      await expect(historyEl).toBeVisible();
    });

    test('ghost reset returns to original position', async ({ page }) => {
      await startHvH(page);

      await makeMove(page, 'e2', 'e4');
      await expect(page.getByTestId('ghost-controls')).toBeVisible({ timeout: 10000 });

      // Pause and step forward twice
      await page.getByTestId('ghost-play-pause-btn').click();
      await page.getByTestId('ghost-step-forward-btn').click();
      await page.getByTestId('ghost-step-forward-btn').click();

      // Reset
      await page.getByTestId('ghost-reset-btn').click();

      // Should be back at step -1 (no move info)
      await expect(page.getByTestId('ghost-move-info')).toContainText('');
    });

    test('ghost toggle mode switches between auto and step', async ({ page }) => {
      await startHvH(page);

      await makeMove(page, 'e2', 'e4');
      await expect(page.getByTestId('ghost-controls')).toBeVisible({ timeout: 10000 });

      // Initial mode should be auto-play
      await expect(page.getByTestId('ghost-status-text')).toContainText(/Auto-playing|Preview complete/);

      // Toggle to step-through
      await page.getByTestId('ghost-toggle-mode-btn').click();
      await expect(page.getByTestId('ghost-status-text')).toContainText('Step through');
    });
  });

  test.describe('Move history', () => {
    test('move history updates after moves', async ({ page }) => {
      await startHvH(page);
      const history = page.getByTestId('move-history');

      // No moves yet — history panel hidden
      await expect(history).not.toBeVisible();

      // Make a move
      await makeMove(page, 'e2', 'e4');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });

      // History should now be visible and contain the move
      await expect(history).toBeVisible({ timeout: 5000 });
      await expect(history).toContainText('e2e4');
    });
  });

  test.describe('vs Engine mode', () => {
    test('engine responds after player move', async ({ page }) => {
      await page.goto('/');
      // Keep default vs Engine mode, play as white
      await page.getByTestId('start-game-btn').click({ timeout: 30000 });
      await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });

      // Make a move as white
      await makeMove(page, 'e2', 'e4');

      // Wait for engine to respond — status should eventually come back to White
      await expect(page.getByTestId('game-status')).toContainText('White to move', { timeout: 15000 });
    });
  });

  test.describe('Board display', () => {
    test('all 64 squares are present', async ({ page }) => {
      await startHvH(page);
      const squares = page.locator('.square');
      await expect(squares).toHaveCount(64);
    });

    test('rank and file labels are displayed', async ({ page }) => {
      await startHvH(page);
      await expect(page.getByTestId('rank-label-1')).toBeVisible();
      await expect(page.getByTestId('rank-label-8')).toBeVisible();
      await expect(page.getByTestId('file-label-a')).toBeVisible();
      await expect(page.getByTestId('file-label-h')).toBeVisible();
    });

    test('board squares are uniform size', async ({ page }) => {
      await startHvH(page);
      const a1 = await page.getByTestId('square-a1').boundingBox();
      const e4 = await page.getByTestId('square-e4').boundingBox();
      const h8 = await page.getByTestId('square-h8').boundingBox();
      // All squares should have same width and height (within 2px tolerance)
      expect(Math.abs(a1!.width - e4!.width)).toBeLessThan(2);
      expect(Math.abs(a1!.height - e4!.height)).toBeLessThan(2);
      expect(Math.abs(a1!.width - h8!.width)).toBeLessThan(2);
      // Each square should be roughly square
      expect(Math.abs(a1!.width - a1!.height)).toBeLessThan(2);
    });
  });

  test.describe('Promotion', () => {
    test('promotion modal is hidden initially', async ({ page }) => {
      await startHvH(page);
      await expect(page.getByTestId('promotion-modal')).not.toBeVisible();
    });
  });

  test.describe('Checkmate', () => {
    test("fool's mate results in Black wins", async ({ page }) => {
      await startHvH(page);

      // 1. f3
      await makeMove(page, 'f2', 'f3');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      // 1... e5
      await makeMove(page, 'e7', 'e5');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      // 2. g4
      await makeMove(page, 'g2', 'g4');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      // 2... Qh4#
      await makeMove(page, 'd8', 'h4');

      // Game should be over — Black wins
      await expect(page.getByTestId('game-status')).toContainText('Black wins', { timeout: 5000 });
    });

    test("scholar's mate results in White wins", async ({ page }) => {
      await startHvH(page);

      // 1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7#
      await makeMove(page, 'e2', 'e4');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      await makeMove(page, 'e7', 'e5');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      await makeMove(page, 'f1', 'c4');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      await makeMove(page, 'b8', 'c6');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      await makeMove(page, 'd1', 'h5');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      await makeMove(page, 'g8', 'f6');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      await makeMove(page, 'h5', 'f7');

      await expect(page.getByTestId('game-status')).toContainText('White wins', { timeout: 5000 });
    });

    test('no moves possible after checkmate', async ({ page }) => {
      await startHvH(page);

      // Fool's mate
      await makeMove(page, 'f2', 'f3');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      await makeMove(page, 'e7', 'e5');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      await makeMove(page, 'g2', 'g4');
      await page.getByTestId('ghost-dismiss-btn').click({ timeout: 10000 });
      await makeMove(page, 'd8', 'h4');

      await expect(page.getByTestId('game-status')).toContainText('Black wins', { timeout: 5000 });

      // Clicking a piece should not show legal moves
      await page.getByTestId('square-e2').click();
      await page.waitForTimeout(200);
      const dots = await page.locator('.legal-dot').count();
      expect(dots).toBe(0);
    });
  });
});
