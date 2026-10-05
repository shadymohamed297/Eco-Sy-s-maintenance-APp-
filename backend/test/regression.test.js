const { test } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const { Pool } = require('pg');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.TEST_BASE_URL || 'http://127.0.0.1:3000';
const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';
if (process.env.NODE_ENV !== 'test' || !/_test(?:[?]|$)/.test(process.env.DATABASE_URL || '') || !['localhost', '127.0.0.1'].includes(new URL(base).hostname)) {
  throw new Error('Tests require NODE_ENV=test, a local API and a database name ending in _test.');
}
async function api(method, route, token, body, status = 200) {
  const response = await fetch(base + route, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  const data = await response.json();
  assert.equal(response.status, status, method + ' ' + route + ': ' + JSON.stringify(data));
  return data;
}
async function login(identifier, password = 'Passw0rd!') { return api('POST', '/api/auth/login', null, { identifier, password }); }
const uid = Date.now().toString(36);
test('Repair lifecycle, invoice accuracy and permissions', async (t) => {
  const manager = (await login('khaled@markazsayana.eg')).token;
  const tech = (await login('mahmoud@markazsayana.eg')).token;
  const other = (await login('amr@markazsayana.eg')).token;
  const techId = (await api('GET', '/api/auth/me', tech)).user.id;
  const fixtureDb = new Pool({ connectionString: process.env.DATABASE_URL });
  try { await fixtureDb.query('UPDATE invoices SET tax_rate = 0.07 WHERE work_order_id = 4'); } finally { await fixtureDb.end(); }
  const before = await api('GET', '/api/reports?period=month', manager);
  const customer = (await api('POST', '/api/work-orders/customers', manager, { name: 'عميل اختبار معزول', phone: '010' + String(Date.now()).slice(-8), address: 'عنوان اختبار', branch: 'المعادي' }, 201)).customer;
  const device = (await api('POST', '/api/work-orders/devices', manager, { customerId: customer.id, deviceType: 'غسالة' }, 201)).device;
  let order = (await api('POST', '/api/work-orders', manager, { customerId: customer.id, deviceId: device.id, branch: 'الرحاب', issueDescription: 'طلب اختبار معزول' }, 201)).workOrder;
  const route = '/api/work-orders/' + order.id;
  const close = { laborFee: 450, warrantyDiscount: 100, paymentMethod: 'cash', signatureName: 'عميل اختبار', signatureImage: png, customerRating: 5 };
  let stock, invoice;
  await t.test('Intake uses one branch, real checklist and activity', async () => {
    assert.equal(customer.branch, 'الحدايق'); assert.equal(order.branch, 'الحدايق');
    assert.equal(order.checklist.length, 3); assert.ok(order.timeline.some(e => e.action.includes('إنشاء')));
    await api('POST', '/api/work-orders', manager, { customerId: customer.id + 100000, deviceId: device.id, issueDescription: 'invalid' }, 400);
    await api('GET', '/api/work-orders?offset=-1', manager, undefined, 400);
  });
  await t.test('Visits start explicitly; only assigned technician can edit', async () => {
    order = (await api('PATCH', route + '/assign', manager, { technicianId: techId, scheduledAt: new Date().toISOString() })).workOrder;
    assert.equal(order.status, 'assigned'); assert.equal(order.startedAt, null);
    await api('PATCH', route + '/assign', manager, { technicianId: techId, scheduledAt: '2026-02-30T10:00:00Z' }, 400);
    await api('PATCH', route + '/assign', manager, { technicianId: techId, scheduledAt: '2026-10-10T24:00:00Z' }, 400);
    await api('POST', route + '/start', other, {}, 403);
    order = (await api('POST', route + '/start', tech, {})).workOrder;
    await api('PATCH', '/api/work-orders/checklist-items/' + order.checklist[0].id, other, { status: 'done' }, 403);
    await api('GET', route, other, undefined, 403);
    const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Cairo' }).format(new Date());
    const schedule = await api('GET', '/api/technicians/' + techId + '/itinerary?date=' + date, manager);
    assert.ok(schedule.items.some(item => item.id === order.id && item.customer.address === 'عنوان اختبار'));
    await api('GET', '/api/technicians/' + techId + '/itinerary?date=2026-02-30', manager, undefined, 400);
  });
  await t.test('Checklist, money and image validation prevent bad invoices', async () => {
    await api('POST', route + '/close', tech, close, 409);
    for (const item of order.checklist) await api('PATCH', '/api/work-orders/checklist-items/' + item.id, tech, { status: 'done' });
    for (const changes of [{ laborFee: -1 }, { laborFee: 'NaN' }, { warrantyDiscount: 500 }, { signatureImage: 'invalid' }]) await api('POST', route + '/close', tech, { ...close, ...changes }, 400);
    await api('POST', route + '/parts', tech, { name: 'قطعة بلا سعر', status: 'used' }, 400);
  });
  await t.test('Stock consumption uses actual price and cannot become negative', async () => {
    stock = (await api('POST', '/api/inventory', manager, { name: 'قطعة اختبار', sku: 'TEST-' + uid, price: 150.25, quantity: 1 }, 201)).item;
    await api('POST', route + '/parts', tech, { name: 'ignored', inventoryItemId: stock.id, status: 'used' }, 201);
    await api('POST', route + '/parts', tech, { name: 'ignored', inventoryItemId: stock.id, status: 'used' }, 409);
    const inventory = await api('GET', '/api/inventory', manager);
    assert.equal(inventory.items.find(item => item.id === stock.id).quantity, 0);
    order = (await api('GET', route, manager)).workOrder;
    assert.equal(order.parts.length, 1); assert.equal(order.parts[0].price, 150.25);
    const pending = (await api('POST', route + '/parts', tech, { name: 'قطعة مطلوبة', status: 'requested' }, 201)).workOrder.parts.find(part => part.status === 'requested');
    await api('POST', route + '/close', tech, close, 409);
    await api('DELETE', route + '/parts/' + pending.id, tech);
  });
  await t.test('Invoice and reports subtract discount and preserve cents', async () => {
    const preview = await api('GET', route + '/invoice-preview?laborFee=450&warrantyDiscount=100', manager);
    assert.equal(preview.partsTotal, 150.25); assert.equal(preview.total, 570.29);
    order = (await api('POST', route + '/close', tech, close)).workOrder;
    invoice = order.invoice;
    assert.equal(invoice.total, 570.29); assert.equal(invoice.signatureImage, png);
    const after = await api('GET', '/api/reports?period=month', manager);
    assert.equal(Math.round((after.totals.totalRevenue - before.totals.totalRevenue) * 100), 57029);
    const dashboard = await api('GET', '/api/dashboard/manager', manager);
    assert.equal(Math.round(dashboard.weeklyRevenue.reduce((sum, point) => sum + point.revenue, 0) * 100), Math.round(after.totals.totalRevenue * 100));
    const history = await api('GET', '/api/technicians/' + techId + '/history', manager);
    assert.ok(history.items.some(item => item.id === order.id));
  });
  await t.test('Retries keep original invoice; closed jobs stay immutable', async () => {
    const retry = (await api('POST', route + '/close', tech, { ...close, laborFee: 999 })).workOrder;
    assert.equal(retry.invoice.total, invoice.total);
    assert.equal((await api('GET', route + '/invoice-preview?laborFee=999&warrantyDiscount=0', manager)).total, invoice.total);
    await api('PATCH', '/api/customers/' + customer.id, manager, { address: 'عنوان العميل الجديد بعد الزيارة' });
    assert.equal((await api('GET', route, manager)).workOrder.customer.address, 'عنوان اختبار');
    await api('PATCH', '/api/work-orders/checklist-items/' + order.checklist[0].id, tech, { status: 'issue' }, 409);
    await api('POST', route + '/parts', tech, { name: 'قطعة', inventoryItemId: stock.id, status: 'requested' }, 409);
    assert.ok(retry.timeline.some(event => event.action.includes('الفاتورة')));
  });
  await t.test('Purchase receipt is idempotent', async () => {
    const po = (await api('POST', '/api/inventory/' + stock.id + '/purchase-order', manager, { quantity: 2 }, 201)).purchaseOrder;
    await api('POST', '/api/inventory/purchase-orders/' + po.id + '/receive', manager, {});
    await api('POST', '/api/inventory/purchase-orders/' + po.id + '/receive', manager, {});
    assert.equal((await api('GET', '/api/inventory', manager)).items.find(item => item.id === stock.id).quantity, 2);
  });
  await t.test('Role changes/reset invalidate previous permissions', async () => {
    const created = await api('POST', '/api/employees', manager, { name: 'اختبار صلاحيات', role: 'manager', email: 'role-' + uid + '@example.test' }, 201);
    const casingDb = new Pool({ connectionString: process.env.DATABASE_URL });
    try { await casingDb.query('UPDATE users SET email = upper(email) WHERE id = $1', [created.employee.id]); } finally { await casingDb.end(); }
    await api('POST', '/api/employees', manager, { name: 'نفس البريد', role: 'reception', email: created.employee.email }, 409);
    const session = (await login(created.employee.email, created.temporaryPassword)).token;
    await api('PATCH', '/api/employees/' + created.employee.id, manager, { role: 'reception' });
    await api('GET', '/api/employees', session, undefined, 403); await api('GET', '/api/customers', session);
    await api('PATCH', '/api/employees/' + created.employee.id, manager, { resetPassword: true });
    await api('GET', '/api/customers', session, undefined, 401);
    const id = (await api('GET', '/api/auth/me', manager)).user.id;
    await api('PATCH', '/api/employees/' + id, manager, { role: 'technician' }, 400);
  });
  await t.test('Password change returns a rotated session', async () => {
    const created = await api('POST', '/api/employees', manager, { name: 'اختبار كلمة المرور', role: 'reception', email: 'password-' + uid + '@example.test' }, 201);
    const session = (await login(created.employee.email, created.temporaryPassword)).token;
    await api('POST', '/api/auth/change-password', session, { currentPassword: 'wrong', newPassword: 'NewPassword123!' }, 400);
    await api('GET', '/api/auth/me', session);
    const changed = await api('POST', '/api/auth/change-password', session, { currentPassword: created.temporaryPassword, newPassword: 'NewPassword123!' });
    await api('GET', '/api/auth/me', session, undefined, 401); await api('GET', '/api/auth/me', changed.token);
  });
  await t.test('Public intake requires no branch and records checklist/events', async () => {
    const result = await api('POST', '/api/public/requests', null, { name: 'اختبار الموقع', phone: '01012345678', address: 'عنوان يدخله المستخدم', deviceType: 'ثلاجة', issueDescription: 'وصف العطل' }, 201);
    const page = await api('GET', '/api/work-orders?search=' + result.code, manager);
    const received = (await api('GET', '/api/work-orders/' + page.items[0].id, manager)).workOrder;
    assert.equal(received.branch, 'الحدايق'); assert.equal(received.checklist.length, 3); assert.equal(received.source, 'online'); assert.equal(received.timeline.length, 1);
    await api('GET', '/api/public/requests/' + result.code + '?phone=01000000001', null, undefined, 404);
    const second = await api('POST', '/api/public/requests', null, { name: 'اختبار الموقع', phone: '01012345678', address: 'عنوان زيارة جديد', deviceType: 'ثلاجة', issueDescription: 'صيانة جديدة' }, 201);
    const secondPage = await api('GET', '/api/work-orders?search=' + second.code, manager);
    const updatedVisit = (await api('GET', '/api/work-orders/' + secondPage.items[0].id, manager)).workOrder;
    assert.equal(updatedVisit.customer.address, 'عنوان زيارة جديد');

  });
  await t.test('Migration and bootstrap preserve existing data', async () => {
    const db = new Pool({ connectionString: process.env.DATABASE_URL });
    try {
      const count = (await db.query('SELECT count(*) AS n FROM work_orders')).rows[0].n;
      await db.query(fs.readFileSync(path.join(__dirname, '../db/schema.sql'), 'utf8'));
      assert.equal((await db.query('SELECT count(*) AS n FROM work_orders')).rows[0].n, count);
      assert.throws(() => execFileSync(process.execPath, [path.join(__dirname, '../db/seed.js')], { env: { ...process.env, MANAGER_NAME: 'مدير اختبار', MANAGER_EMAIL: 'bootstrap@example.test', MANAGER_PASSWORD: 'BootstrapPassword123!' }, stdio: 'pipe' }));
      assert.equal((await db.query('SELECT count(*) AS n FROM work_orders')).rows[0].n, count);
    } finally { await db.end(); }
  });
});
