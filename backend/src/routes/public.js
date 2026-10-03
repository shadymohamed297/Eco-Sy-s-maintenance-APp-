const express = require('express');
const rateLimit = require('express-rate-limit');
const { pool } = require('../db');

const router = express.Router();

const DEVICE_TYPES = ['مكيف', 'غسالة', 'ثلاجة', 'سخان', 'بوتاجاز', 'أخرى'];
const BRANCHES = ['مدينة نصر', 'المعادي', 'الرحاب'];

const STATUS_LABELS = {
  new: 'تم استلام الطلب',
  assigned: 'تم تعيين فني',
  in_progress: 'جارِ العمل على الطلب',
  paused: 'الزيارة متوقفة مؤقتاً',
  awaiting_approval: 'بانتظار موافقتكم على عرض السعر',
  rejected: 'تم إلغاء الطلب',
  completed: 'تم الانتهاء من الإصلاح',
  closed: 'تم تسليم الجهاز',
};

// Public intake has no login, so it's the one place in this API reachable by anyone on
// the internet — keep it tightly rate-limited per IP to deter spam/abuse.
const submitLimiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 12, standardHeaders: true, legacyHeaders: false });
const trackLimiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 30, standardHeaders: true, legacyHeaders: false });

function cleanText(value, maxLen) {
  if (typeof value !== 'string') return '';
  return value.trim().slice(0, maxLen);
}

router.post('/requests', submitLimiter, async (req, res) => {
  const name = cleanText(req.body?.name, 120);
  const phone = cleanText(req.body?.phone, 30);
  const address = cleanText(req.body?.address, 300);
  const branch = cleanText(req.body?.branch, 50);
  const deviceType = cleanText(req.body?.deviceType, 20);
  const brand = cleanText(req.body?.brand, 60);
  const model = cleanText(req.body?.model, 60);
  const issueDescription = cleanText(req.body?.issueDescription, 500);
  // Honeypot: a real browser never fills this hidden field; a bot filling every field usually does.
  const honeypot = cleanText(req.body?.website, 100);

  if (honeypot) {
    return res.status(201).json({ code: 'WO-0000' });
  }

  if (!name || !phone || !deviceType || !issueDescription) {
    return res.status(400).json({ error: 'الاسم ورقم الهاتف ونوع الجهاز ووصف العطل مطلوبة' });
  }
  if (!/^[0-9+\s-]{8,20}$/.test(phone)) {
    return res.status(400).json({ error: 'رقم الهاتف غير صحيح' });
  }
  if (!DEVICE_TYPES.includes(deviceType)) {
    return res.status(400).json({ error: 'نوع الجهاز غير صحيح' });
  }
  // A new customer has no branch on file yet, and work_orders.branch is required, so the
  // request itself must always supply one (the website form already requires this field).
  const existingCustomer = (await pool.query('SELECT branch FROM customers WHERE phone = $1', [phone])).rows[0];
  const resolvedBranch = branch || existingCustomer?.branch || null;
  if (!resolvedBranch || !BRANCHES.includes(resolvedBranch)) {
    return res.status(400).json({ error: 'الفرع مطلوب' });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    let customerRow = (await client.query('SELECT * FROM customers WHERE phone = $1', [phone])).rows[0];
    if (!customerRow) {
      customerRow = (
        await client.query(
          'INSERT INTO customers (name, phone, address, branch) VALUES ($1,$2,$3,$4) RETURNING *',
          [name, phone, address || null, resolvedBranch]
        )
      ).rows[0];
    }

    const deviceRow = (
      await client.query(
        `INSERT INTO devices (customer_id, device_type, brand, model) VALUES ($1,$2,$3,$4) RETURNING *`,
        [customerRow.id, deviceType, brand || null, model || null]
      )
    ).rows[0];

    const codeRow = await client.query("SELECT nextval(pg_get_serial_sequence('work_orders','id')) AS n");
    const code = `WO-${2400 + Number(codeRow.rows[0].n)}`;
    await client.query(
      `INSERT INTO work_orders (code, customer_id, device_id, branch, issue_description, status, priority, source)
       VALUES ($1,$2,$3,$4,$5,'new','normal','online')`,
      [code, customerRow.id, deviceRow.id, resolvedBranch, issueDescription]
    );

    await client.query('COMMIT');
    return res.status(201).json({ code });
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
});

router.get('/requests/:code', trackLimiter, async (req, res) => {
  const phone = cleanText(req.query.phone, 30);
  const code = cleanText(req.params.code, 20);
  if (!phone || !code) {
    return res.status(400).json({ error: 'رقم الهاتف وكود الطلب مطلوبان' });
  }

  const { rows } = await pool.query(
    `SELECT wo.code, wo.status, wo.created_at, wo.closed_at, d.device_type, t.name AS technician_name
     FROM work_orders wo
     JOIN customers c ON c.id = wo.customer_id
     JOIN devices d ON d.id = wo.device_id
     LEFT JOIN users t ON t.id = wo.technician_id
     WHERE wo.code = $1 AND c.phone = $2`,
    [code, phone]
  );
  const wo = rows[0];
  if (!wo) {
    return res.status(404).json({ error: 'لا يوجد طلب بهذا الكود ورقم الهاتف' });
  }

  return res.json({
    code: wo.code,
    status: wo.status,
    statusLabel: STATUS_LABELS[wo.status] || wo.status,
    deviceType: wo.device_type,
    technicianAssigned: !!wo.technician_name,
    createdAt: wo.created_at,
    closedAt: wo.closed_at,
  });
});

module.exports = router;
