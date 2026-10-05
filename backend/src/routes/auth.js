const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const rateLimit = require('express-rate-limit');
const { pool } = require('../db');
const { requireAuth } = require('../middleware/auth');
const { BRANCH, text, fail, transaction } = require('../domain');
const router = express.Router();
function initials(name) { return name.trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join(' '); }
function toPublicUser(user) {
  return { id: user.id, name: user.name, email: user.email, phone: user.phone, role: user.role,
    branch: BRANCH, title: user.title, specialty: user.specialty, initials: initials(user.name) };
}
function tokenFor(user) {
  return jwt.sign({ id: user.id, role: user.role, name: user.name, tokenVersion: user.token_version || 0 }, process.env.JWT_SECRET, { expiresIn: '30d' });
}
function validPassword(value) { return typeof value === 'string' && value.length >= 8 && Buffer.byteLength(value) <= 72; }
const loginLimiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 50, standardHeaders: true, legacyHeaders: false,
  message: { error: 'محاولات دخول كثيرة. انتظر قليلًا وحاول تاني.' } });
router.post('/login', loginLimiter, async (req, res) => {
  const identifier = text(req.body?.identifier, 'رقم الهاتف أو البريد الإلكتروني', 200, true);
  const password = req.body?.password;
  if (typeof password !== 'string' || Buffer.byteLength(password) > 72) fail(400, 'اكتب كلمة المرور بصورة صحيحة');
  const { rows } = await pool.query('SELECT * FROM users WHERE lower(email) = lower($1) OR phone = $1 LIMIT 1', [identifier]);
  const user = rows[0];
  if (!user || !await bcrypt.compare(password, user.password_hash)) return res.status(401).json({ error: 'رقم الهاتف أو البريد الإلكتروني أو كلمة المرور غير صحيحة' });
  if (!user.active) return res.status(403).json({ error: 'الحساب متوقف. تواصل مع مدير المركز.' });
  return res.json({ token: tokenFor(user), user: toPublicUser(user) });
});
router.get('/me', requireAuth, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM users WHERE id = $1', [req.user.id]);
  return res.json({ user: toPublicUser(rows[0]) });
});
router.post('/change-password', requireAuth, async (req, res) => {
  const { currentPassword, newPassword } = req.body || {};
  if (!validPassword(newPassword) || typeof currentPassword !== 'string' || Buffer.byteLength(currentPassword) > 72) fail(400, 'اكتب كلمة مرور من ٨ أحرف على الأقل، وتجنب كلمة مرور طويلة جدًا');
  const user = await transaction(async (client) => {
    const { rows } = await client.query('SELECT * FROM users WHERE id = $1 FOR UPDATE', [req.user.id]);
    if (!await bcrypt.compare(currentPassword, rows[0].password_hash)) fail(400, 'كلمة المرور الحالية غير صحيحة');
    const newHash = await bcrypt.hash(newPassword, 12);
    return (await client.query('UPDATE users SET password_hash = $1, token_version = token_version + 1 WHERE id = $2 RETURNING *', [newHash, req.user.id])).rows[0];
  });
  return res.json({ ok: true, token: tokenFor(user), user: toPublicUser(user) });
});
module.exports = { router, toPublicUser, initials };
