const { chromium } = require('playwright-core');
const OUT = process.argv[2];
const sides = {
  legacy: { base: 'http://localhost:8080/EmployeeManagementApp/', pages: [
    ['01-home', 'index.jsp'], ['02-signup', 'signup.jsp'], ['03-login', 'Login.jsp'], 'LOGIN',
    ['04-welcome', 'Welcome.jsp'], ['05-employee-list', 'ShowEmployeeServlet'],
    ['06-add-employee', 'addEmployee.jsp'], ['07-edit-employee', 'editEmployee.jsp'], ['08-delete-employee', 'deleteEmployee.jsp']]},
  modernized: { base: 'http://localhost:8091/', pages: [
    ['01-home', ''], ['02-signup', 'signup'], ['03-login', 'login'], 'LOGIN',
    ['04-welcome', 'welcome'], ['05-employee-list', 'employees'],
    ['06-add-employee', 'employees/add'], ['07-edit-employee', 'employees/edit'], ['08-delete-employee', 'employees/delete']]},
};
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  for (const [side, cfg] of Object.entries(sides)) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    const page = await ctx.newPage();
    for (const p of cfg.pages) {
      if (p === 'LOGIN') {
        await page.goto(cfg.base + (side === 'legacy' ? 'Login.jsp' : 'login'));
        await page.fill('input[name="id"], input#id', '1');
        await page.fill('input[name="name"], input#name', 'Arun');
        await Promise.all([page.waitForURL(/welcome/i), page.click('input[type=submit], button[type=submit]')]);
        continue;
      }
      await page.goto(cfg.base + p[1], { waitUntil: 'networkidle' });
      await page.waitForTimeout(300);
      await page.screenshot({ path: `${OUT}/${side}/${p[0]}.png`, fullPage: true });
      console.log(side, p[0], page.url());
    }
    await ctx.close();
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
