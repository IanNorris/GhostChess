import { test, expect, Page } from '@playwright/test';

const SCREENSHOT_DIR = '/home/ian/chess/screenshots';

async function startHvH(page: Page) {
  await page.goto('/');
  await page.getByTestId('mode-vs-human').click({ timeout: 30000 });
  await page.getByTestId('start-game-btn').click();
  await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });
}

async function startVsEngine(page: Page) {
  await page.goto('/');
  await page.getByTestId('start-game-btn').click({ timeout: 30000 });
  await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });
}

async function makeMove(page: Page, from: string, to: string) {
  await page.getByTestId(`square-${from}`).click();
  await page.getByTestId(`square-${to}`).click();
}

test.describe('Screenshots for README', () => {

  test('01 - main menu', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('app-title')).toBeVisible({ timeout: 30000 });
    await page.screenshot({ path: `${SCREENSHOT_DIR}/01-main-menu.png`, fullPage: true });
  });

  test('02 - initial board', async ({ page }) => {
    await startHvH(page);
    await page.screenshot({ path: `${SCREENSHOT_DIR}/02-board.png`, fullPage: true });
  });

  test('03 - piece selected with legal moves', async ({ page }) => {
    await startHvH(page);
    await page.getByTestId('square-e2').click();
    await page.waitForTimeout(300);
    await page.screenshot({ path: `${SCREENSHOT_DIR}/03-selected.png`, fullPage: true });
  });

  test('04 - ghost preview after move', async ({ page }) => {
    await startHvH(page);
    await makeMove(page, 'e2', 'e4');
    await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });
    await page.getByTestId('ghost-play-pause-btn').click();
    await page.waitForTimeout(200);
    await page.getByTestId('ghost-step-forward-btn').click();
    await page.getByTestId('ghost-step-forward-btn').click();
    await page.waitForTimeout(300);
    await page.screenshot({ path: `${SCREENSHOT_DIR}/04-ghost-preview.png`, fullPage: true });
  });

  test('05 - vs engine with engine thinking', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('thinking-toggle').click({ timeout: 30000 });
    await page.getByTestId('start-game-btn').click();
    await expect(page.getByTestId('game-screen')).toBeVisible({ timeout: 15000 });
    await makeMove(page, 'e2', 'e4');
    await page.waitForSelector('#ghost-controls.active', { timeout: 10000 });
    await page.waitForTimeout(500);
    await page.screenshot({ path: `${SCREENSHOT_DIR}/05-engine-thinking.png`, fullPage: true });
  });

  test('06 - pause menu', async ({ page }) => {
    await startHvH(page);
    await page.getByTestId('pause-btn').click();
    await expect(page.getByTestId('pause-modal')).toBeVisible({ timeout: 5000 });
    await page.waitForTimeout(300);
    await page.screenshot({ path: `${SCREENSHOT_DIR}/06-pause-menu.png`, fullPage: true });
  });
});
