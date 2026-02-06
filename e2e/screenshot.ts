import { chromium } from '@playwright/test';
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 500, height: 700 } });
  await page.goto('http://localhost:8080');
  await page.waitForSelector('[data-testid="menu-screen"]', { timeout: 10000 });
  // Start HvH
  await page.click('[data-testid="mode-vs-human"]');
  await page.click('[data-testid="start-game-btn"]');
  await page.waitForSelector('[data-testid="chess-board"]', { state: 'visible', timeout: 5000 });
  await page.waitForTimeout(500);
  await page.screenshot({ path: '/home/ian/chess/screenshot-board.png', fullPage: true });
  console.log('1. Board captured');
  // Select e2
  await page.click('[data-testid="square-e2"]');
  await page.waitForTimeout(300);
  await page.screenshot({ path: '/home/ian/chess/screenshot-selected.png', fullPage: true });
  console.log('2. Selected captured');
  // Make move e2-e4
  await page.click('[data-testid="square-e4"]');
  await page.waitForTimeout(500);
  // Pause auto-play, step forward
  await page.click('[data-testid="ghost-play-pause-btn"]');
  await page.click('[data-testid="ghost-step-forward-btn"]');
  await page.click('[data-testid="ghost-step-forward-btn"]');
  await page.waitForTimeout(300);
  await page.screenshot({ path: '/home/ian/chess/screenshot-after-move.png', fullPage: true });
  console.log('3. After move + ghost captured');
  await browser.close();
})();
