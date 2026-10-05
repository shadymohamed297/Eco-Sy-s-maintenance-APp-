const express = require('express');
const bcrypt = require('bcryptjs');
const { randomBytes } = require('crypto');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const { initials } = require('./auth');
const { BRANCH, text, positiveId, transaction, fail } = require('../domain');
const router = express.Router();
function toEmployeeDto(row) {
  return { id: row.id, name: row.name, initials: initials(row.name), email: row.email, phone: row.phone,
    role: row.role, branch: BRANCH, title: row.title, specialty: row.specialty, active: row.active, createdAt: row.created_at };
}
function validate(body, existing = {}) {
  const value = { ...existing, ...body };
  value.name = text(value.name, 'اسم الموظف', 120, true);
  if (!['reception', 'technician', 'manager'].includes(value.role)) fail(400, 'اختر وظيفة الموظف');
  value.email = text(value.email, 'البريد الإلكتروني', 200)?.toLowerCase() || null;
  value.phone = text(value.phone, 'رقم الهاتف', 30) || null;
  if (!value.email && !value.phone) fail(400, 'أضف رقم هاتف أو بريد إلكتروني لتسجيل الدخول');
  if (value.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.email)) fail(400, 'البريد الإلكتروني غير صحيح');
  if (value.phone && !/^[+0-9\s-]{8,20}$/.test(value.phone)) fail(400, 'رقم الهاتف غير صحيح');
  if (value.active !== undefined && typeof value.active !== 'boolean') fail(400, 'حالة الحساب غير صحيحة');
  value.title = text(value.title, 'المسمى الوظيفي', 120);
  value.specialty = text(value.specialty, 'التخصص', 120);
  return value;
}
function duplicate(error) {
  if (error.code === '23505') fail(409, 'رقم الهاتف أو البريد الإلكتروني مستخدم بالفعل');
  throw error;
}
router.use(requireAuth, requireRole('manager'));
router.get('/', async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM users ORDER BY active DESC, role ASC, name ASC');
  return res.json({ employees: rows.map(toEmployeeDto) });
});
router.post('/', async (req, res) => {
  const value = validate(req.body || {});
  if (value.email && (await pool.query('SELECT id FROM users WHERE lower(email) = $1 LIMIT 1', [value.email])).rowCount) fail(409, 'البريد الإلكتروني مستخدم بالفعل');
  const password = randomBytes(12).toString('base64url');
  const hash = await bcrypt.hash(password, 12);
  try {
    const { rows } = await pool.query(`INSERT INTO users (name,email,phone,password_hash,role,branch,title,specialty)
      VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
      [value.name,value.email,value.phone,hash,value.role,BRANCH,value.title,value.specialty]);
    return res.status(201).json({ employee: toEmployeeDto(rows[0]), temporaryPassword: password });
  } catch (error) { duplicate(error); }
});
router.patch('/:id', async (req, res) => {
  const id = positiveId(req.params.id);
  const body = req.body || {};
  if (body.resetPassword !== undefined && typeof body.resetPassword !== 'boolean') fail(400, 'طلب تغيير كلمة المرور غير صحيح');
  try {
    const result = await transaction(async (client) => {
      // Serialize account changes so concurrent updates cannot remove the last manager.
      await client.query('LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE');
      const old = (await client.query('SELECT * FROM users WHERE id = $1', [id])).rows[0];
      if (!old) fail(404, 'الموظف غير موجود');
      const value = validate(body, old);
      if (value.email && (await client.query('SELECT id FROM users WHERE lower(email) = $1 AND id != $2 LIMIT 1', [value.email,id])).rowCount) fail(409, 'البريد الإلكتروني مستخدم بالفعل');
      if (id === req.user.id && (value.active === false || value.role !== 'manager')) fail(400, 'لا يمكنك إيقاف حسابك أو تغيير صلاحياته');
      if (old.role === 'manager' && old.active && (value.role !== 'manager' || value.active === false)) {
        const managers = await client.query("SELECT count(*)::int AS count FROM users WHERE role = 'manager' AND active = true");
        if (managers.rows[0].count <= 1) fail(400, 'لازم يفضل مدير واحد على الأقل للمركز');
      }
      const password = body.resetPassword ? randomBytes(12).toString('base64url') : null;
      const passwordHash = password ? await bcrypt.hash(password, 12) : old.password_hash;
      const { rows } = await client.query(`UPDATE users SET name=$1,email=$2,phone=$3,role=$4,branch=$5,title=$6,
        specialty=$7,active=$8,password_hash=$9,token_version=token_version + $10 WHERE id=$11 RETURNING *`,
        [value.name,value.email,value.phone,value.role,BRANCH,value.title,value.specialty,value.active,
          passwordHash,(password || value.active !== old.active) ? 1 : 0,id]);
      return { employee: toEmployeeDto(rows[0]), ...(password ? { temporaryPassword: password } : {}) };
    });
    return res.json(result);
  } catch (error) { duplicate(error); }
});
module.exports = router;
