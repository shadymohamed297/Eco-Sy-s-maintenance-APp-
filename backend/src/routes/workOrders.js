const express = require('express');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const { initials } = require('./auth');

const router = express.Router();

const STATUS_LABELS = {
  new: 'طلب جديد',
  assigned: 'مُعيَّن',
  in_progress: 'قيد التنفيذ',
  paused: 'متوقفة مؤقتاً',
  awaiting_approval: 'بانتظار موافقة العميل',
  rejected: 'رفض العميل العرض',
  completed: 'مكتملة',
  closed: 'مغلقة',
};

async function loadWorkOrderDetail(id) {
  const { rows } = await pool.query(
    `SELECT wo.*, c.name AS customer_name, c.phone AS customer_phone, c.address AS customer_address,
            d.device_type, d.brand, d.model, d.serial_number, d.under_warranty, d.warranty_end,
            t.name AS technician_name
     FROM work_orders wo
     JOIN customers c ON c.id = wo.customer_id
     JOIN devices d ON d.id = wo.device_id
     LEFT JOIN users t ON t.id = wo.technician_id
     WHERE wo.id = $1`,
    [id]
  );
  const wo = rows[0];
  if (!wo) return null;

  const checklist = await pool.query(
    'SELECT * FROM checklist_items WHERE work_order_id = $1 ORDER BY order_index ASC',
    [id]
  );
  const parts = await pool.query(
    'SELECT * FROM parts_used WHERE work_order_id = $1 ORDER BY created_at ASC',
    [id]
  );
  const invoice = await pool.query('SELECT * FROM invoices WHERE work_order_id = $1', [id]);
  const photos = await pool.query(
    'SELECT * FROM work_order_photos WHERE work_order_id = $1 ORDER BY created_at ASC',
    [id]
  );

  return {
    id: wo.id,
    code: wo.code,
    status: wo.status,
    statusLabel: STATUS_LABELS[wo.status],
    priority: wo.priority,
    issueDescription: wo.issue_description,
    branch: wo.branch,
    source: wo.source,
    scheduledAt: wo.scheduled_at,
    createdAt: wo.created_at,
    startedAt: wo.started_at,
    completedAt: wo.completed_at,
    closedAt: wo.closed_at,
    estimatedCost: wo.estimated_cost !== null ? Number(wo.estimated_cost) : null,
    quoteNote: wo.quote_note,
    quotedAt: wo.quoted_at,
    approvedAt: wo.approved_at,
    rejectReason: wo.reject_reason,
    customer: {
      id: wo.customer_id,
      name: wo.customer_name,
      phone: wo.customer_phone,
      address: wo.customer_address,
    },
    device: {
      id: wo.device_id,
      type: wo.device_type,
      brand: wo.brand,
      model: wo.model,
      serialNumber: wo.serial_number,
      underWarranty: wo.under_warranty,
      warrantyEnd: wo.warranty_end,
    },
    technician: wo.technician_id
      ? { id: wo.technician_id, name: wo.technician_name, initials: initials(wo.technician_name) }
      : null,
    checklist: checklist.rows.map((c) => ({
      id: c.id,
      label: c.label,
      status: c.status,
      note: c.note,
      orderIndex: c.order_index,
    })),
    parts: parts.rows.map((p) => ({
      id: p.id,
      inventoryItemId: p.inventory_item_id,
      name: p.name,
      price: Number(p.price),
      status: p.status,
    })),
    invoice: invoice.rows[0]
      ? {
          laborFee: Number(invoice.rows[0].labor_fee),
          warrantyDiscount: Number(invoice.rows[0].warranty_discount),
          taxRate: Number(invoice.rows[0].tax_rate),
          paymentMethod: invoice.rows[0].payment_method,
          signatureName: invoice.rows[0].signature_name,
          signatureImage: invoice.rows[0].signature_image,
          signedAt: invoice.rows[0].signed_at,
          customerRating: invoice.rows[0].customer_rating,
        }
      : null,
    photos: photos.rows.map((p) => ({
      id: p.id,
      kind: p.kind,
      imageData: p.image_data,
      createdAt: p.created_at,
    })),
  };
}

// ── Customers & devices (reception intake) ─────────────────────────

function toCustomerDto(row) {
  return { id: row.id, name: row.name, phone: row.phone, address: row.address, branch: row.branch };
}

function toDeviceDto(row) {
  return {
    id: row.id,
    customerId: row.customer_id,
    deviceType: row.device_type,
    brand: row.brand,
    model: row.model,
    serialNumber: row.serial_number,
    underWarranty: row.under_warranty,
    warrantyEnd: row.warranty_end,
  };
}

router.get('/customers', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { phone } = req.query;
  if (!phone) return res.status(400).json({ error: 'phone query param required' });
  const customerRow = await pool.query('SELECT * FROM customers WHERE phone = $1', [phone]);
  if (!customerRow.rows[0]) return res.json({ customer: null });

  const devices = await pool.query('SELECT * FROM devices WHERE customer_id = $1 ORDER BY created_at DESC', [customerRow.rows[0].id]);
  return res.json({
    customer: { ...toCustomerDto(customerRow.rows[0]), devices: devices.rows.map(toDeviceDto) },
  });
});

router.post('/customers', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { name, phone, address, branch } = req.body || {};
  if (!name || !phone) return res.status(400).json({ error: 'name and phone are required' });
  const { rows } = await pool.query(
    'INSERT INTO customers (name, phone, address, branch) VALUES ($1,$2,$3,$4) RETURNING *',
    [name, phone, address || null, branch || null]
  );
  return res.status(201).json({ customer: toCustomerDto(rows[0]) });
});

router.post('/devices', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { customerId, deviceType, brand, model, serialNumber, underWarranty, warrantyEnd } = req.body || {};
  if (!customerId || !deviceType) return res.status(400).json({ error: 'customerId and deviceType are required' });
  const { rows } = await pool.query(
    `INSERT INTO devices (customer_id, device_type, brand, model, serial_number, under_warranty, warranty_end)
     VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING *`,
    [customerId, deviceType, brand || null, model || null, serialNumber || null, !!underWarranty, warrantyEnd || null]
  );
  return res.status(201).json({ device: toDeviceDto(rows[0]) });
});

router.patch('/devices/:deviceId', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { deviceType, brand, model, serialNumber, underWarranty, warrantyEnd } = req.body || {};
  const { rows } = await pool.query(
    `UPDATE devices SET
       device_type = COALESCE($1, device_type),
       brand = COALESCE($2, brand),
       model = COALESCE($3, model),
       serial_number = COALESCE($4, serial_number),
       under_warranty = COALESCE($5, under_warranty),
       warranty_end = COALESCE($6, warranty_end)
     WHERE id = $7 RETURNING *`,
    [deviceType || null, brand, model, serialNumber, underWarranty === undefined ? null : !!underWarranty, warrantyEnd, req.params.deviceId]
  );
  if (!rows[0]) return res.status(404).json({ error: 'Device not found' });
  return res.json({ device: toDeviceDto(rows[0]) });
});

// ── Work orders ─────────────────────────────────────────────────────

router.post('/', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { customerId, deviceId, issueDescription, priority, branch } = req.body || {};
  if (!customerId || !deviceId || !issueDescription) {
    return res.status(400).json({ error: 'customerId, deviceId and issueDescription are required' });
  }
  const codeRow = await pool.query("SELECT nextval(pg_get_serial_sequence('work_orders','id')) AS n");
  const code = `WO-${2400 + Number(codeRow.rows[0].n)}`;
  const { rows } = await pool.query(
    `INSERT INTO work_orders (code, customer_id, device_id, branch, issue_description, priority, created_by)
     VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id`,
    [code, customerId, deviceId, branch || req.user.branch || null, issueDescription, priority || 'normal', req.user.id]
  );
  const workOrder = await loadWorkOrderDetail(rows[0].id);
  return res.status(201).json({ workOrder });
});

router.get('/', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { status, branch, search } = req.query;
  const limit = Math.min(Number(req.query.limit) || 30, 100);
  const offset = Math.max(Number(req.query.offset) || 0, 0);

  const where = [];
  const params = [];

  if (status) {
    const statuses = String(status).split(',').map((s) => s.trim()).filter(Boolean);
    params.push(statuses);
    where.push(`wo.status = ANY($${params.length})`);
  }
  if (branch) {
    params.push(branch);
    where.push(`wo.branch = $${params.length}`);
  }
  if (search) {
    params.push(`%${search}%`);
    const idx = params.length;
    where.push(`(wo.code ILIKE $${idx} OR c.name ILIKE $${idx} OR c.phone ILIKE $${idx} OR wo.issue_description ILIKE $${idx})`);
  }

  const whereSql = where.length ? `WHERE ${where.join(' AND ')}` : '';

  const totalRow = await pool.query(
    `SELECT count(*) FROM work_orders wo JOIN customers c ON c.id = wo.customer_id ${whereSql}`,
    params
  );

  params.push(limit);
  params.push(offset);
  const { rows } = await pool.query(
    `SELECT wo.id, wo.code, wo.status, wo.priority, wo.issue_description, wo.branch, wo.source,
            wo.scheduled_at, wo.created_at, wo.closed_at,
            c.name AS customer_name, d.device_type, t.name AS technician_name
     FROM work_orders wo
     JOIN customers c ON c.id = wo.customer_id
     JOIN devices d ON d.id = wo.device_id
     LEFT JOIN users t ON t.id = wo.technician_id
     ${whereSql}
     ORDER BY wo.created_at DESC
     LIMIT $${params.length - 1} OFFSET $${params.length}`,
    params
  );

  return res.json({
    total: Number(totalRow.rows[0].count),
    limit,
    offset,
    items: rows.map((r) => ({
      id: r.id,
      code: r.code,
      status: r.status,
      statusLabel: STATUS_LABELS[r.status],
      priority: r.priority,
      issueDescription: r.issue_description,
      branch: r.branch,
      source: r.source,
      scheduledAt: r.scheduled_at,
      createdAt: r.created_at,
      closedAt: r.closed_at,
      customerName: r.customer_name,
      deviceType: r.device_type,
      technicianName: r.technician_name,
    })),
  });
});

router.get('/mine', requireAuth, requireRole('technician'), async (req, res) => {
  const { rows } = await pool.query(
    `SELECT wo.id FROM work_orders wo
     WHERE wo.technician_id = $1
       AND (
         wo.scheduled_at::date = current_date
         OR wo.status IN ('in_progress', 'paused')
         OR wo.closed_at::date = current_date
       )
     ORDER BY wo.scheduled_at ASC NULLS LAST`,
    [req.user.id]
  );
  const items = await Promise.all(rows.map((r) => loadWorkOrderDetail(r.id)));

  const completedToday = await pool.query(
    `SELECT count(*) FROM work_orders WHERE technician_id = $1 AND closed_at::date = current_date`,
    [req.user.id]
  );
  const totalToday = await pool.query(
    `SELECT count(*) FROM work_orders WHERE technician_id = $1 AND scheduled_at::date = current_date`,
    [req.user.id]
  );

  return res.json({
    items,
    progress: { completed: Number(completedToday.rows[0].count), total: Number(totalToday.rows[0].count) },
  });
});

router.get('/:id', requireAuth, async (req, res) => {
  const workOrder = await loadWorkOrderDetail(req.params.id);
  if (!workOrder) return res.status(404).json({ error: 'Work order not found' });
  return res.json({ workOrder });
});

router.patch('/:id', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { issueDescription, priority, branch } = req.body || {};
  if (priority && !['normal', 'urgent'].includes(priority)) {
    return res.status(400).json({ error: 'invalid priority' });
  }
  const { rows } = await pool.query(
    `UPDATE work_orders SET
       issue_description = COALESCE($1, issue_description),
       priority = COALESCE($2, priority),
       branch = COALESCE($3, branch)
     WHERE id = $4 RETURNING id`,
    [issueDescription || null, priority || null, branch || null, req.params.id]
  );
  if (!rows[0]) return res.status(404).json({ error: 'Work order not found' });
  return res.json({ workOrder: await loadWorkOrderDetail(req.params.id) });
});

router.post('/:id/approve', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { rows } = await pool.query(
    `UPDATE work_orders SET status = 'in_progress', approved_at = now()
     WHERE id = $1 AND status = 'awaiting_approval' RETURNING id`,
    [req.params.id]
  );
  if (!rows[0]) return res.status(409).json({ error: 'Work order is not awaiting approval' });
  return res.json({ workOrder: await loadWorkOrderDetail(req.params.id) });
});

router.post('/:id/reject', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { reason } = req.body || {};
  const { rows } = await pool.query(
    `UPDATE work_orders SET status = 'rejected', reject_reason = $1, closed_at = now()
     WHERE id = $2 AND status = 'awaiting_approval' RETURNING id`,
    [reason || null, req.params.id]
  );
  if (!rows[0]) return res.status(409).json({ error: 'Work order is not awaiting approval' });
  return res.json({ workOrder: await loadWorkOrderDetail(req.params.id) });
});

router.patch('/:id/assign', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { technicianId, scheduledAt } = req.body || {};
  if (!technicianId || !scheduledAt) return res.status(400).json({ error: 'technicianId and scheduledAt are required' });
  await pool.query(
    `UPDATE work_orders SET technician_id = $1, scheduled_at = $2, status = 'assigned' WHERE id = $3`,
    [technicianId, scheduledAt, req.params.id]
  );
  const workOrder = await loadWorkOrderDetail(req.params.id);
  return res.json({ workOrder });
});

async function requireOwnTechnician(req, res, next) {
  const { rows } = await pool.query('SELECT technician_id FROM work_orders WHERE id = $1', [req.params.id]);
  if (!rows[0]) return res.status(404).json({ error: 'Work order not found' });
  if (req.user.role !== 'manager' && rows[0].technician_id !== req.user.id) {
    return res.status(403).json({ error: 'Not assigned to this work order' });
  }
  return next();
}

router.post('/:id/start', requireAuth, requireRole('technician'), requireOwnTechnician, async (req, res) => {
  await pool.query(
    `UPDATE work_orders SET status = 'in_progress', started_at = COALESCE(started_at, now()) WHERE id = $1`,
    [req.params.id]
  );
  return res.json({ workOrder: await loadWorkOrderDetail(req.params.id) });
});

router.post('/:id/pause', requireAuth, requireRole('technician'), requireOwnTechnician, async (req, res) => {
  await pool.query(`UPDATE work_orders SET status = 'paused' WHERE id = $1`, [req.params.id]);
  return res.json({ workOrder: await loadWorkOrderDetail(req.params.id) });
});

// A technician who finds the repair needs customer sign-off on cost (e.g. an
// out-of-warranty part) sends a quote here; the work order leaves the technician's
// active queue until reception records the customer's phone-call decision via
// /:id/approve or /:id/reject.
router.patch('/:id/quote', requireAuth, requireRole('technician'), requireOwnTechnician, async (req, res) => {
  const { estimatedCost, note } = req.body || {};
  if (estimatedCost === undefined || Number.isNaN(Number(estimatedCost))) {
    return res.status(400).json({ error: 'estimatedCost is required' });
  }
  await pool.query(
    `UPDATE work_orders SET status = 'awaiting_approval', estimated_cost = $1, quote_note = $2, quoted_at = now()
     WHERE id = $3`,
    [estimatedCost, note || null, req.params.id]
  );
  return res.json({ workOrder: await loadWorkOrderDetail(req.params.id) });
});

router.patch('/checklist-items/:itemId', requireAuth, requireRole('technician'), async (req, res) => {
  const { status, note } = req.body || {};
  if (!['pending', 'done', 'issue'].includes(status)) {
    return res.status(400).json({ error: 'status must be pending, done, or issue' });
  }
  const { rows } = await pool.query(
    `UPDATE checklist_items SET status = $1, note = COALESCE($2, note) WHERE id = $3 RETURNING work_order_id`,
    [status, note, req.params.itemId]
  );
  if (!rows[0]) return res.status(404).json({ error: 'Checklist item not found' });
  return res.json({ workOrder: await loadWorkOrderDetail(rows[0].work_order_id) });
});

router.post('/:id/parts', requireAuth, requireRole('technician'), requireOwnTechnician, async (req, res) => {
  const { name, inventoryItemId, status } = req.body || {};
  if (!name) return res.status(400).json({ error: 'name is required' });
  const partStatus = status || 'used';

  let price = 0;
  if (inventoryItemId) {
    const item = await pool.query('SELECT * FROM inventory_items WHERE id = $1', [inventoryItemId]);
    if (item.rows[0]) {
      price = Number(item.rows[0].price);
      if (partStatus === 'used') {
        await pool.query('UPDATE inventory_items SET quantity = GREATEST(quantity - 1, 0) WHERE id = $1', [inventoryItemId]);
      }
    }
  }

  await pool.query(
    `INSERT INTO parts_used (work_order_id, inventory_item_id, name, price, status) VALUES ($1,$2,$3,$4,$5)`,
    [req.params.id, inventoryItemId || null, name, price, partStatus]
  );

  if (partStatus === 'requested' && inventoryItemId) {
    await pool.query(
      `INSERT INTO purchase_orders (inventory_item_id, work_order_id, quantity, status) VALUES ($1,$2,1,'pending')`,
      [inventoryItemId, req.params.id]
    );
  }

  return res.status(201).json({ workOrder: await loadWorkOrderDetail(req.params.id) });
});

router.post('/:id/photos', requireAuth, requireRole('technician'), requireOwnTechnician, async (req, res) => {
  const { kind, imageData } = req.body || {};
  if (!['before', 'after'].includes(kind)) {
    return res.status(400).json({ error: 'kind must be before or after' });
  }
  if (!imageData || typeof imageData !== 'string') {
    return res.status(400).json({ error: 'imageData is required' });
  }

  await pool.query(
    `INSERT INTO work_order_photos (work_order_id, kind, image_data) VALUES ($1,$2,$3)`,
    [req.params.id, kind, imageData]
  );

  return res.status(201).json({ workOrder: await loadWorkOrderDetail(req.params.id) });
});

router.get('/:id/invoice-preview', requireAuth, requireRole('technician'), requireOwnTechnician, async (req, res) => {
  const { laborFee = 450, warrantyDiscount } = req.query;
  const wo = await loadWorkOrderDetail(req.params.id);
  if (!wo) return res.status(404).json({ error: 'Work order not found' });

  const partsTotal = wo.parts.filter((p) => p.status === 'used').reduce((sum, p) => sum + p.price, 0);
  const discount = warrantyDiscount !== undefined ? Number(warrantyDiscount) : (wo.device.underWarranty ? Number(laborFee) : 0);
  const taxRate = 0.14;
  const subtotal = Number(laborFee) + partsTotal - discount;
  const total = Math.round(subtotal * (1 + taxRate) * 100) / 100;

  return res.json({
    laborFee: Number(laborFee),
    partsTotal,
    warrantyDiscount: discount,
    taxRate,
    subtotal,
    total,
    parts: wo.parts.filter((p) => p.status === 'used'),
  });
});

router.post('/:id/close', requireAuth, requireRole('technician'), requireOwnTechnician, async (req, res) => {
  const { laborFee, warrantyDiscount = 0, paymentMethod, signatureName, signatureImage, customerRating } = req.body || {};
  if (laborFee === undefined || !paymentMethod || !signatureName) {
    return res.status(400).json({ error: 'laborFee, paymentMethod and signatureName are required' });
  }
  if (!['card', 'cash', 'account'].includes(paymentMethod)) {
    return res.status(400).json({ error: 'invalid paymentMethod' });
  }

  await pool.query(
    `INSERT INTO invoices (work_order_id, labor_fee, warranty_discount, tax_rate, payment_method, signature_name, signature_image, signed_at, customer_rating)
     VALUES ($1,$2,$3,0.14,$4,$5,$6,now(),$7)
     ON CONFLICT (work_order_id) DO UPDATE SET
       labor_fee = EXCLUDED.labor_fee, warranty_discount = EXCLUDED.warranty_discount,
       payment_method = EXCLUDED.payment_method, signature_name = EXCLUDED.signature_name,
       signature_image = EXCLUDED.signature_image,
       signed_at = now(), customer_rating = EXCLUDED.customer_rating`,
    [req.params.id, laborFee, warrantyDiscount, paymentMethod, signatureName, signatureImage || null, customerRating || null]
  );

  await pool.query(
    `UPDATE work_orders SET status = 'closed', completed_at = COALESCE(completed_at, now()), closed_at = now()
     WHERE id = $1`,
    [req.params.id]
  );

  return res.json({ workOrder: await loadWorkOrderDetail(req.params.id) });
});

module.exports = router;
