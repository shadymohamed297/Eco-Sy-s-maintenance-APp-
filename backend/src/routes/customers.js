const express = require('express');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');

const { BRANCH, positiveId, pagination, text, fail, handleError } = require('../domain');
const router = express.Router();
router.param('id', (req, res, next, value) => { positiveId(value); next(); });

router.get('/', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { search } = req.query;
  const { limit, offset } = pagination(req.query);

  const where = [];
  const params = [];
  if (search) {
    params.push(`%${text(search, 'البحث', 200)}%`);
    where.push(`(c.name ILIKE $${params.length} OR c.phone ILIKE $${params.length})`);
  }
  const whereSql = where.length ? `WHERE ${where.join(' AND ')}` : '';

  const totalRow = await pool.query(`SELECT count(*) FROM customers c ${whereSql}`, params);

  params.push(limit);
  params.push(offset);
  const { rows } = await pool.query(
    `SELECT c.*,
            (SELECT count(*) FROM devices d WHERE d.customer_id = c.id) AS device_count,
            (SELECT count(*) FROM work_orders wo WHERE wo.customer_id = c.id) AS work_order_count
     FROM customers c
     ${whereSql}
     ORDER BY c.name ASC
     LIMIT $${params.length - 1} OFFSET $${params.length}`,
    params
  );

  return res.json({
    total: Number(totalRow.rows[0].count),
    limit,
    offset,
    items: rows.map((r) => ({
      id: r.id,
      name: r.name,
      phone: r.phone,
      address: r.address,
      branch: r.branch,
      deviceCount: Number(r.device_count),
      workOrderCount: Number(r.work_order_count),
    })),
  });
});

router.get('/:id', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const customerRow = await pool.query('SELECT * FROM customers WHERE id = $1', [req.params.id]);
  const customer = customerRow.rows[0];
  if (!customer) return res.status(404).json({ error: 'Customer not found' });

  const devices = await pool.query('SELECT * FROM devices WHERE customer_id = $1 ORDER BY created_at DESC', [customer.id]);

  const workOrders = await pool.query(
    `SELECT wo.id, wo.code, wo.status, wo.priority, wo.issue_description, wo.created_at, wo.scheduled_at, wo.started_at, wo.closed_at,
            d.device_type, t.name AS technician_name
     FROM work_orders wo
     JOIN devices d ON d.id = wo.device_id
     LEFT JOIN users t ON t.id = wo.technician_id
     WHERE wo.customer_id = $1
     ORDER BY wo.created_at DESC`,
    [customer.id]
  );

  return res.json({
    customer: {
      id: customer.id,
      name: customer.name,
      phone: customer.phone,
      address: customer.address,
      branch: customer.branch,
    },
    devices: devices.rows.map((d) => ({
      id: d.id,
      deviceType: d.device_type,
      brand: d.brand,
      model: d.model,
      serialNumber: d.serial_number,
      underWarranty: d.under_warranty,
      warrantyEnd: d.warranty_end,
    })),
    workOrders: workOrders.rows.map((wo) => ({
      id: wo.id,
      code: wo.code,
      status: wo.status,
      priority: wo.priority,
      issueDescription: wo.issue_description,
      deviceType: wo.device_type,
      technicianName: wo.technician_name,
      createdAt: wo.created_at,
      scheduledAt: wo.scheduled_at,
      startedAt: wo.started_at,
      closedAt: wo.closed_at,
    })),
  });
});

router.patch('/:id', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const body = req.body || {};
  const name = text(body.name, 'الاسم', 120, body.name !== undefined);
  const phone = text(body.phone, 'رقم الهاتف', 30, body.phone !== undefined);
  const address = text(body.address, 'العنوان', 300);
  if (phone && !/^[0-9+\s-]{8,20}$/.test(phone)) fail(400, 'رقم الهاتف غير صحيح');
  if (phone && (await pool.query('SELECT id FROM customers WHERE phone = $1 AND id != $2 LIMIT 1', [phone, req.params.id])).rowCount) fail(409, 'رقم الهاتف مسجل لعميل آخر');
  const { rows } = await pool.query(
    `UPDATE customers SET
       name = COALESCE($1, name),
       phone = COALESCE($2, phone),
       address = COALESCE($3, address),
       branch = COALESCE($4, branch)
     WHERE id = $5 RETURNING *`,
    [name, phone, address, body.branch === undefined ? null : BRANCH, req.params.id]
  );
  if (!rows[0]) return res.status(404).json({ error: 'Customer not found' });
  return res.json({
    customer: {
      id: rows[0].id,
      name: rows[0].name,
      phone: rows[0].phone,
      address: rows[0].address,
      branch: rows[0].branch,
    },
  });
});

router.use(handleError);
module.exports = router;
