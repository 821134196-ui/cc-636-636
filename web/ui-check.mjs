import { chromium } from 'playwright';

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1400, height: 1000 } });
const errors = [];
page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message));
page.on('console', (m) => { if (m.type() === 'error') errors.push('CONSOLE: ' + m.text()); });

await page.goto('http://localhost:8080/', { waitUntil: 'networkidle' });
await page.waitForTimeout(800);

// 首屏：转发授权
const title = await page.locator('header h1').innerText();
const tabs = await page.locator('.tabs button').allInnerTexts();
console.log('TITLE:', title);
console.log('TABS:', tabs.join(' | '));

// 切到自动回复页，检查列表
await page.getByRole('button', { name: '自动回复' }).click();
await page.waitForTimeout(400);
const replyStatus = await page.locator('.badge.ACTIVE').first().innerText().catch(() => '(none)');
console.log('REPLY BADGE:', replyStatus);

// 切到模拟邮箱页
await page.getByRole('button', { name: '模拟邮箱' }).click();
await page.waitForTimeout(400);
const inboxRows = await page.locator('.panel').allInnerTexts();
console.log('MAILBOX panels contain 发件箱:', inboxRows.some((t) => t.includes('模拟邮箱发件箱')));

// 完整业务流：转发申请并审批，然后检查状态徽标
await page.getByRole('button', { name: '转发授权' }).click();
await page.waitForTimeout(300);
await page.getByRole('button', { name: '主管发起转发申请' }).click();
await page.selectOption('select >> nth=0', { index: 1 });
await page.selectOption('select >> nth=1', { index: 1 });
await page.getByRole('button', { name: '提交申请（待审批）' }).click();
await page.waitForTimeout(600);
await page.getByRole('button', { name: '审批通过' }).first().click();
await page.waitForTimeout(600);
const activeRules = await page.locator('.badge.ACTIVE').allInnerTexts();
console.log('ACTIVE badges after approve:', activeRules.join(','));
await page.screenshot({ path: '/tmp/ui-forwarding.png', fullPage: true });

// 历史版本弹窗
await page.getByRole('button', { name: '历史版本' }).first().click();
await page.waitForTimeout(400);
const modalText = await page.locator('.modal').first().innerText();
console.log('VERSION MODAL HAS APPROVED:', modalText.includes('审批通过'));
await page.keyboard.press('Escape').catch(() => {});
await page.locator('.modal-mask').first().click({ position: { x: 5, y: 5 } }).catch(() => {});

// 审计页
await page.getByRole('button', { name: '审计记录' }).click();
await page.waitForTimeout(500);
const auditActions = await page.locator('.action').allInnerTexts();
console.log('AUDIT has FORWARD_APPROVED:', auditActions.includes('FORWARD_APPROVED'),
  'rows:', auditActions.length);

// 截图
await page.screenshot({ path: '/tmp/ui-audit.png', fullPage: false });

console.log('JS ERRORS:', errors.length ? errors.join(' || ') : 'none');
await browser.close();
process.exit(errors.length ? 1 : 0);
