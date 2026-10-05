require('dotenv').config();
const bcrypt = require('bcryptjs');
const { pool } = require('../src/db');
const { BRANCH, transaction } = require('../src/domain');

// Provision one real manager on a fresh database. Never reset existing records.
async function bootstrapManager() {
  const name = (process.env.MANAGER_NAME || '').trim();
  const email = (process.env.MANAGER_EMAIL || '').trim().toLowerCase();
  const phone = (process.env.MANAGER_PHONE || '').trim();
  const password = process.env.MANAGER_PASSWORD || '';
  if (!name || (!email && !phone) || password.length < 12 || Buffer.byteLength(password) > 72) {
    throw new Error('Set MANAGER_NAME, MANAGER_EMAIL or MANAGER_PHONE, and MANAGER_PASSWORD (at least 12 characters, at most 72 UTF-8 bytes).');
  }
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new Error('Invalid MANAGER_EMAIL.');
  if (phone && !/^[+0-9\s-]{8,20}$/.test(phone)) throw new Error('Invalid MANAGER_PHONE.');
  const passwordHash = await bcrypt.hash(password, 12);
  return transaction(async (client) => {
    await client.query('LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE');
    const existing = await client.query('SELECT id FROM users LIMIT 1');
    if (existing.rowCount) throw new Error('Accounts already exist. Bootstrap did not change any data; manage accounts from the app.');
    const { rows } = await client.query(
      `INSERT INTO users (name,email,phone,password_hash,role,branch,title)
       VALUES ($1,$2,$3,$4,'manager',$5,'مدير المركز') RETURNING id`,
      [name,email || null,phone || null,passwordHash,BRANCH]
    );
    return rows[0].id;
  });
}
module.exports = { bootstrapManager };
if (require.main === module) {
  bootstrapManager()
    .then(() => console.log('Manager account created. No customer, order, or inventory data was added.'))
    .catch((error) => { console.error(error.message); process.exitCode = 1; })
    .finally(() => pool.end());
}
