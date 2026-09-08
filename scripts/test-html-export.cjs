// Run after HtmlExporterTest with -Dopenspine.test.htmlOutput=<directory>.
// Requires Playwright, pngjs, and an installed headless Chromium.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require('playwright');
const { PNG } = require('pngjs');

async function main() {
  const directory = path.resolve(process.argv[2] || 'artifacts/html-export-check/fixtures');
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.OPENSPINE_TEST_CHROMIUM || undefined,
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
  });
  try {
    for (const viewport of [{ width: 960, height: 700 }, { width: 390, height: 844 }]) {
      const context = await browser.newContext({ viewport, offline: true });
      try {
        await context.addInitScript(() => {
          let runtime;
          Object.defineProperty(window, 'spine', {
            configurable: true,
            get: () => runtime,
            set(value) {
              runtime = { ...value, SpinePlayer: class extends value.SpinePlayer {
                constructor(parent, config) { super(parent, config); window.__testPlayer = this; }
              } };
            },
          });
        });
        for (const file of ['external-atlas.html', 'loose-images.html', 'skin-combination.html', 'setup-only.html']) {
          const page = await context.newPage();
          try {
            const errors = [];
            const assetRequests = [];
            const networkRequests = [];
            page.on('pageerror', error => errors.push(error.message));
            page.on('request', request => {
              if (request.url().includes('/openspine-export/')) assetRequests.push(request.url());
              if (/^https?:/.test(request.url())) networkRequests.push(request.url());
            });
            await page.goto(pathToFileURL(path.join(directory, file)).href);
            await page.waitForFunction(() => document.getElementById('status').dataset.state !== 'loading');
            const state = await page.locator('#status').getAttribute('data-state');
            assert.equal(state, 'ready', (await page.locator('#status').textContent()) + '\n' + errors.join('\n'));
            if (file === 'skin-combination.html') {
              const composition = await page.evaluate(() => {
                const player = window.__testPlayer;
                const config = JSON.parse(document.getElementById('openspine-data').textContent).config;
                const attachment = player.skeleton.findSlot('body').getAppliedPose().getAttachment();
                return { skins: config.skin, red: attachment.color.r, green: attachment.color.g };
              });
              assert.deepEqual(composition.skins, ['default', '皮肤(夜)']);
              assert(Math.abs(composition.red - 0x55 / 255) < 0.001, 'last skin supplies the rendered attachment');
              assert(Math.abs(composition.green - 0xdd / 255) < 0.001, 'combined attachment tint is preserved');
            }
            if (file !== 'setup-only.html') {
              await page.evaluate(() => {
                window.__testPlayer.setAnimation('移动[测试]', true);
                window.__testPlayer.play();
              });
            }
            await page.waitForTimeout(120);
            const first = await page.locator('canvas').screenshot();
            await page.waitForTimeout(270);
            const second = await page.locator('canvas').screenshot();
            if (file !== 'setup-only.html') assert(!first.equals(second), `${file}: the rendered animation must move`);
            const decoded = PNG.sync.read(second);
            let coloredPixels = 0;
            for (let index = 0; index < decoded.data.length; index += 4) {
              const [r, g, b, a] = decoded.data.subarray(index, index + 4);
              if (a > 128 && Math.max(r, g, b) - Math.min(r, g, b) > 40) coloredPixels++;
            }
            assert(coloredPixels > 1000, `${file}: expected visible textured geometry, got ${coloredPixels} pixels`);
            await page.evaluate(() => window.__testPlayer.pause());
            assert.equal(await page.evaluate(() => window.__testPlayer.paused), true, 'player controls can pause');
            assert.deepEqual(assetRequests, [], 'all skeleton, atlas, and texture requests resolve from embedded data');
            assert.deepEqual(networkRequests, [], 'offline exports request no remote scripts, styles, fonts, or textures');
            assert.deepEqual(errors, [], 'the public 4.3 player reports no JavaScript errors');
            assert((await page.locator('#spine-player-license').evaluate(element => element.content.textContent))
              .includes('Spine Runtimes License Agreement'), 'the full runtime license travels with the HTML');
            const screenshot = path.join(directory, `${path.parse(file).name}-${viewport.width}.png`);
            await page.screenshot({ path: screenshot });
            console.log(JSON.stringify({ file, viewport, state, coloredPixels, screenshot }));
          } finally { await page.close(); }
        }
        const missingPage = await context.newPage();
        try {
          await missingPage.goto(pathToFileURL(path.join(directory, 'missing-resources.html')).href);
          assert.equal(await missingPage.locator('#status').getAttribute('data-state'), 'error');
          assert(await missingPage.locator('#status').isVisible(), 'missing resources must show a visible error');
        } finally { await missingPage.close(); }
        const invalidPage = await context.newPage();
        try {
          const html = await fs.readFile(path.join(directory, 'external-atlas.html'), 'utf8');
          const invalidHtml = html.replace(/(<script id="openspine-data" type="application\/json">)([\s\S]*?)(<\/script>)/,
            (_match, open, data, close) => {
              const payload = JSON.parse(data);
              payload.config.skin = 'missing-test-skin';
              return open + JSON.stringify(payload).replaceAll('<', '\\u003c') + close;
            });
          await invalidPage.setContent(invalidHtml);
          await invalidPage.waitForFunction(() => document.getElementById('status').dataset.state === 'error');
          const message = await invalidPage.locator('#status').textContent();
          assert(message.includes('missing-test-skin'), `asynchronous errors retain the actual cause: ${message}`);
          assert(await invalidPage.locator('#status').isVisible(), 'asynchronous player errors remain visible');
        } finally { await invalidPage.close(); }
      } finally { await context.close(); }
    }
    const corrupt = await browser.newContext({ offline: true });
    try {
      const page = await corrupt.newPage();
      const html = await fs.readFile(path.join(directory, 'external-atlas.html'), 'utf8');
      await page.setContent(html.replace(/(<script id="spine-player-runtime" src=")[^"]+"/,
        '$1data:text/javascript;base64,"'));
      assert.equal(await page.locator('#status').getAttribute('data-state'), 'error');
      assert((await page.locator('#status').textContent()).includes('重新导出'), 'a damaged embedded runtime gives an actionable error');
    } finally { await corrupt.close(); }
    console.log('HTML PLAYWRIGHT TEST PASSED');
  } finally { await browser.close(); }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
