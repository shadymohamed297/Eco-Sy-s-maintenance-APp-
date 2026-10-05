const { test } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const { Pool } = require('pg');
const fs = require('node:fs');
const path = require('node:path');
if (process.env.NODE_ENV !== 'test' || !/_test(?:[?]|$)/.test(process.env.DATABASE_URL || '')) {
  throw new Error('Bootstrap tests require an isolated test database.');
}
test('Fresh bootstrap creates one real manager and no fake business records', async () => {
  const connection = new URL(process.env.DATABASE_URL);
  assert.ok(['localhost', '127.0.0.1'].includes(connection.hostname), 'Test database must be local');
  const database = 'bootstrap_' + process.pid + '_' + Date.now() + '_test';
  const admin = new Pool({ connectionString: connection.toString() });
  let db;
  let created = false;
  try {
    await admin.query('CREATE DATABASE "' + database + '"'); created = true;
    connection.pathname = '/' + database;
    db = new Pool({ connectionString: connection.toString() });
    await db.query(fs.readFileSync(path.join(__dirname, '../db/schema.sql'), 'utf8'));
    const env = { ...process.env, DATABASE_URL: connection.toString(), MANAGER_NAME: 'مدير المركز', MANAGER_EMAIL: 'manager@example.test', MANAGER_PASSWORD: 'BootstrapPassword123!' };
    execFileSync(process.execPath, [path.join(__dirname, '../db/seed.js')], { env, stdio: 'pipe' });
    const users = await db.query('SELECT role, branch, name FROM users');
    assert.equal(users.rowCount, 1); assert.equal(users.rows[0].role, 'manager'); assert.equal(users.rows[0].branch, 'الحدايق');
    for (const table of ['customers', 'devices', 'work_orders', 'invoices', 'inventory_items']) {
      assert.equal((await db.query('SELECT count(*)::int AS count FROM ' + table)).rows[0].count, 0, table);
    }
    assert.throws(() => execFileSync(process.execPath, [path.join(__dirname, '../db/seed.js')], { env, stdio: 'pipe' }));
    assert.equal((await db.query('SELECT count(*)::int AS count FROM users')).rows[0].count, 1);
  } finally {
    if (db) await db.end();
    if (created) await admin.query('DROP DATABASE "' + database + '"');
    await admin.end();
  }
});
