import { test, expect } from '@playwright/test';

test.describe('Ghost Chess', () => {

  test.describe('Menu Screen', () => {
    test('displays app title', async ({ page }) => {
      await page.goto('/');
      await expect(page.getByTestId('app-title')).toBeVisible({ timeout: 30000 });
      await expect(page.getByTestId('app-title')).toContainText('Ghost Chess');
    });

    test('displays game mode options', async ({ page }) => {
      await page.goto('/');
      await expect(page.getByTestId('mode-vs-engine')).toBeVisible({ timeout: 30000 });
      await expect(page.getByTestId('mode-vs-human')).toBeVisible();
    });

    test('displays start game button', async ({ page }) => {
      await page.goto('/');
      await expect(page.getByTestId('start-game-btn')).toBeVisible({ timeout: 30000 });
    });

    test('has thinking toggle', async ({ page }) => {
      await page.goto('/');
      await expect(page.getByTestId('thinking-toggle')).toBeVisible({ timeout: 30000 });
    });

    test('has depth slider', async ({ page }) => {
      await page.goto('/');
      await expect(page.getByTestId('depth-slider')).toBeVisible({ timeout: 30000 });
    });
  });

  test.describe('Game Screen', () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/');
      await page.getByTestId('start-game-btn').click({ timeout: 30000 });
      await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });
    });

    test('displays chess board', async ({ page }) => {
      await expect(page.getByTestId('chess-board')).toBeVisible();
    });

    test('displays game status', async ({ page }) => {
      await expect(page.getByTestId('game-status')).toBeVisible();
      await expect(page.getByTestId('game-status')).toContainText('White to move');
    });

    test('has pause button', async ({ page }) => {
      await expect(page.getByTestId('pause-btn')).toBeVisible();
    });

    test('has undo button', async ({ page }) => {
      await expect(page.getByTestId('undo-btn')).toBeVisible();
    });

    test('board has all squares', async ({ page }) => {
      await expect(page.getByTestId('square-a1')).toBeVisible();
      await expect(page.getByTestId('square-h8')).toBeVisible();
      await expect(page.getByTestId('square-a8')).toBeVisible();
      await expect(page.getByTestId('square-h1')).toBeVisible();
    });

    test('pieces are displayed on initial board', async ({ page }) => {
      await expect(page.getByTestId('piece-e1')).toBeVisible(); // King
      await expect(page.getByTestId('piece-d1')).toBeVisible(); // Queen
      await expect(page.getByTestId('piece-e2')).toBeVisible(); // Pawn
      await expect(page.getByTestId('piece-e8')).toBeVisible(); // King
      await expect(page.getByTestId('piece-d8')).toBeVisible(); // Queen
      await expect(page.getByTestId('piece-e7')).toBeVisible(); // Pawn
    });

    test('clicking a piece shows legal moves', async ({ page }) => {
      await page.getByTestId('square-e2').click();
      await expect(page.getByTestId('legal-move-e3')).toBeVisible({ timeout: 5000 });
      await expect(page.getByTestId('legal-move-e4')).toBeVisible();
    });

    test('can make a move by clicking', async ({ page }) => {
      await page.getByTestId('square-e2').click();
      await page.getByTestId('square-e4').click();
      await expect(page.getByTestId('piece-e4')).toBeVisible({ timeout: 5000 });
    });

    test('pause then quit returns to menu', async ({ page }) => {
      await page.getByTestId('pause-btn').click();
      await expect(page.getByTestId('pause-modal')).toBeVisible({ timeout: 5000 });
      await page.getByTestId('quit-btn').click();
      await expect(page.getByTestId('menu-screen')).toBeVisible({ timeout: 5000 });
    });
  });

  test.describe('Ghost Preview', () => {
    test('ghost controls appear after making a move', async ({ page }) => {
      await page.goto('/');
      await page.getByTestId('mode-vs-human').click({ timeout: 30000 });
      await page.getByTestId('start-game-btn').click();
      await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });

      await page.getByTestId('square-e2').click();
      await page.getByTestId('square-e4').click();

      await expect(page.getByTestId('ghost-controls')).toBeVisible({ timeout: 10000 });
    });

    test('ghost preview shows eval', async ({ page }) => {
      await page.goto('/');
      await page.getByTestId('mode-vs-human').click({ timeout: 30000 });
      await page.getByTestId('start-game-btn').click();
      await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });

      await page.getByTestId('square-e2').click();
      await page.getByTestId('square-e4').click();

      await expect(page.getByTestId('ghost-eval')).toBeVisible({ timeout: 10000 });
    });

    test('ghost dismiss button works', async ({ page }) => {
      await page.goto('/');
      await page.getByTestId('mode-vs-human').click({ timeout: 30000 });
      await page.getByTestId('start-game-btn').click();
      await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });

      await page.getByTestId('square-e2').click();
      await page.getByTestId('square-e4').click();

      await expect(page.getByTestId('ghost-controls')).toBeVisible({ timeout: 10000 });
      await page.getByTestId('ghost-dismiss-btn').click();
      await expect(page.getByTestId('ghost-controls')).not.toBeVisible({ timeout: 5000 });
    });
  });

  test.describe('Engine Thinking', () => {
    test('thinking panel appears when enabled', async ({ page }) => {
      await page.goto('/');
      await page.getByTestId('thinking-toggle').click({ timeout: 30000 });
      await page.getByTestId('mode-vs-human').click();
      await page.getByTestId('start-game-btn').click();
      await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });

      await page.getByTestId('square-e2').click();
      await page.getByTestId('square-e4').click();

      await expect(page.getByTestId('engine-thinking-panel')).toBeVisible({ timeout: 10000 });
      await expect(page.getByTestId('thinking-description')).toBeVisible();
    });
  });
});
