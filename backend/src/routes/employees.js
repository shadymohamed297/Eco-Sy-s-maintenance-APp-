const express = require('express');
const bcrypt = require('bcryptjs');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const { initials } = require('./auth');

const router = express.Router();

function toEmployeeDto(row) {
  return {
    id: row.id,
    name: row.name,
    initials: initials(row.name),
    email: row.email,
    phone: row.phone,
    role: row.role,
    branch: row.branch,
    title: row.title,
    specialty: row.specialty,
    active: row.active,
    createdAt: row.created_at,
  };
}

function generatePassword() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';
  let out = '';
  for (let i = 0; i < 10; i += 1) {
    out += chars[Math.floor(Math.random() * chars.length)];
  }
  return out;
}

router.use(requireAuth, requireRole('manager'));

router.get('/', async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM users ORDER BY active DESC, role ASC, name ASC');
  return res.json({ employees: rows.map(toEmployeeDto) });
});

router.post('/', async (req, res) => {
  const { name, role, email, phone, branch, title, specialty } = req.body || {};
  if (!name || !role) return res.status(400).json({ error: 'name and role are required' });
  if (!['reception', 'technician', 'manager'].includes(role)) {
    return res.status(400).json({ error: 'invalid role' });
  }
  if (!email && !phone) return res.status(400).json({ error: 'email or phone is required' });

  const password = generatePassword();
  const passwordHash = await bcrypt.hash(password, 10);

  try {
    const { rows } = await pool.query(
      `INSERT INTO users (name, email, phone, password_hash, role, branch, title, specialty)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
      [name, email || null, phone || null, passwordHash, role, branch || null, title || null, specialty || null]
    );
    return res.status(201).json({ employee: toEmployeeDto(rows[0]), temporaryPassword: password });
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ error: 'البريد الإلكتروني أو رقم الهاتف مستخدم بالفعل' });
    }
    throw err;
  }
});

router.patch('/:id', async (req, res) => {
  const { name, role, email, phone, branch, title, specialty, active, resetPassword } = req.body || {};

  if (Number(req.params.id) === req.user.id && active === false) {
    return res.status(400).json({ error: 'لا يمكنك إيقاف حسابك الخاص' });
  }
  if (role && !['reception', 'technician', 'manager'].includes(role)) {
    return res.status(400).json({ error: 'invalid role' });
  }

  const existing = await pool.query('SELECT * FROM users WHERE id = $1', [req.params.id]);
  if (!existing.rows[0]) return res.status(404).json({ error: 'Employee not found' });

  try {
    const { rows } = await pool.query(
      `UPDATE users SET
         name = COALESCE($1, name),
         role = COALESCE($2, role),
         email = COALESCE($3, email),
         phone = COALESCE($4, phone),
         branch = COALESCE($5, branch),
         title = COALESCE($6, title),
         specialty = COALESCE($7, specialty),
         active = COALESCE($8, active)
       WHERE id = $9 RETURNING *`,
      [name, role, email, phone, branch, title, specialty, active, req.params.id]
    );

    let temporaryPassword;
    if (resetPassword) {
      temporaryPassword = generatePassword();
      const passwordHash = await bcrypt.hash(temporaryPassword, 10);
      await pool.query('UPDATE users SET password_hash = $1 WHERE id = $2', [passwordHash, req.params.id]);
    }

    return res.json({ employee: toEmployeeDto(rows[0]), ...(temporaryPassword ? { temporaryPassword } : {}) });
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ error: 'البريد الإلكتروني أو رقم الهاتف مستخدم بالفعل' });
    }
    throw err;
  }
});

module.exports = router;
