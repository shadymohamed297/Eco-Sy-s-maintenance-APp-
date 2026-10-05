const express = require('express');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const { initials } = require('./auth');
const { BRANCH, DEVICE_TYPES, TIME_ZONE, STATUS_LABELS, fail, handleError, positiveId, pagination, text, calendarDate, scheduledTimestamp, money, imageData, transaction, createChecklist, recordEvent, invoiceTotals } = require('../domain');

const router = express.Router();


async function loadWorkOrderDetail(id) {
  const { rows } = await pool.query(
    `SELECT wo.*, c.name AS customer_name, c.phone AS customer_phone, COALESCE(wo.service_address, c.address) AS customer_address,
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

  const timeline = await pool.query(`SELECT e.*, u.name AS actor_name FROM work_order_events e LEFT JOIN users u ON u.id = e.user_id WHERE e.work_order_id = $1 ORDER BY e.created_at ASC, e.id ASC`, [id]);
  const usedPartsTotal = parts.rows.filter((part) => part.status === 'used').reduce((total, part) => total + Number(part.price), 0);

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
          ...invoiceTotals(invoice.rows[0], usedPartsTotal),
          warrantyDiscount: Number(invoice.rows[0].warranty_discount),
          taxRate: Number(invoice.rows[0].tax_rate),
          paymentMethod: invoice.rows[0].payment_method,
          signatureName: invoice.rows[0].signature_name,
          signatureImage: invoice.rows[0].signature_image,
          signedAt: invoice.rows[0].signed_at,
          customerRating: invoice.rows[0].customer_rating,
        }
      : null,
    timeline: timeline.rows.map((event) => ({ id: event.id, action: event.action, actorName: event.actor_name, createdAt: event.created_at, metadata: event.metadata })),
    photos: photos.rows.map((p) => ({
      id: p.id,
      kind: p.kind,
      imageData: p.image_data,
      createdAt: p.created_at,
    })),
  };
}

router.use(requireAuth);
router.use((req, res, next) => { req.body = req.body || {}; next(); });
for (const parameter of ['id', 'itemId', 'deviceId', 'partId']) {
  router.param(parameter, (req, res, next, value) => { positiveId(value); next(); });
}

function toCustomerDto(row) {
  return { id: row.id, name: row.name, phone: row.phone, address: row.address, branch: row.branch };
}
function toDeviceDto(row) {
  return { id: row.id, customerId: row.customer_id, deviceType: row.device_type, brand: row.brand,
    model: row.model, serialNumber: row.serial_number, underWarranty: row.under_warranty, warrantyEnd: row.warranty_end };
}
function validateDevice(body) {
  if (body.deviceType !== undefined && !DEVICE_TYPES.includes(body.deviceType)) fail(400, 'نوع الجهاز غير صحيح');
  if (body.underWarranty !== undefined && typeof body.underWarranty !== 'boolean') fail(400, 'حالة الضمان غير صحيحة');
  if (body.warrantyEnd && !calendarDate(body.warrantyEnd)) fail(400, 'تاريخ الضمان غير صحيح');
}
async function lockOrder(client, req, allowedStatuses) {
  const { rows } = await client.query('SELECT * FROM work_orders WHERE id = $1 FOR UPDATE', [req.params.id]);
  const order = rows[0];
  if (!order) fail(404, 'طلب الصيانة غير موجود');
  if (req.user.role === 'technician' && order.technician_id !== req.user.id) fail(403, 'هذا الطلب غير مسند إليك');
  if (allowedStatuses && !allowedStatuses.includes(order.status)) fail(409, 'حالة الطلب الحالية لا تسمح بهذا الإجراء');
  return order;
}
async function canReadOrder(req) {
  const { rows } = await pool.query('SELECT technician_id FROM work_orders WHERE id = $1', [req.params.id]);
  if (!rows[0]) fail(404, 'طلب الصيانة غير موجود');
  if (req.user.role === 'technician' && rows[0].technician_id !== req.user.id) fail(403, 'هذا الطلب غير مسند إليك');
}
async function sendOrder(res, id, status = 200) {
  return res.status(status).json({ workOrder: await loadWorkOrderDetail(id) });
}

router.get('/customers', requireRole('reception', 'manager'), async (req, res) => {
  const phone = text(req.query.phone, 'رقم الهاتف', 30, true);
  const customer = (await pool.query('SELECT * FROM customers WHERE phone = $1 ORDER BY id LIMIT 1', [phone])).rows[0];
  if (!customer) return res.json({ customer: null });
  const devices = await pool.query('SELECT * FROM devices WHERE customer_id = $1 ORDER BY created_at DESC', [customer.id]);
  return res.json({ customer: { ...toCustomerDto(customer), devices: devices.rows.map(toDeviceDto) } });
});
router.post('/customers', requireRole('reception', 'manager'), async (req, res) => {
  const name = text(req.body.name, 'الاسم', 120, true);
  const phone = text(req.body.phone, 'رقم الهاتف', 30, true);
  if (!/^[0-9+\s-]{8,20}$/.test(phone)) fail(400, 'رقم الهاتف غير صحيح');
  const address = text(req.body.address, 'العنوان', 300);
  const customer = await transaction(async (client) => {
    // Prevent simultaneous intakes from creating two records for the same phone.
    await client.query('SELECT pg_advisory_xact_lock(hashtext($1))', [phone]);
    const existing = (await client.query('SELECT * FROM customers WHERE phone = $1 ORDER BY id LIMIT 1', [phone])).rows[0];
    if (existing) return existing;
    return (await client.query('INSERT INTO customers (name, phone, address, branch) VALUES ($1,$2,$3,$4) RETURNING *', [name, phone, address, BRANCH])).rows[0];
  });
  return res.status(201).json({ customer: toCustomerDto(customer) });
});
router.post('/devices', requireRole('reception', 'manager'), async (req, res) => {
  const body = req.body || {};
  validateDevice(body);
  const customerId = positiveId(body.customerId);
  if (!DEVICE_TYPES.includes(body.deviceType)) fail(400, 'نوع الجهاز مطلوب');
  if (!(await pool.query('SELECT id FROM customers WHERE id = $1', [customerId])).rowCount) fail(404, 'العميل غير موجود');
  const { rows } = await pool.query(`INSERT INTO devices (customer_id, device_type, brand, model, serial_number, under_warranty, warranty_end)
    VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING *`, [customerId, body.deviceType, text(body.brand, 'الماركة', 60), text(body.model, 'الموديل', 60), text(body.serialNumber, 'الرقم التسلسلي', 120), body.underWarranty ?? false, body.warrantyEnd || null]);
  return res.status(201).json({ device: toDeviceDto(rows[0]) });
});
router.patch('/devices/:deviceId', requireRole('reception', 'manager'), async (req, res) => {
  const body = req.body || {};
  validateDevice(body);
  const { rows } = await pool.query(`UPDATE devices SET device_type = COALESCE($1, device_type), brand = COALESCE($2, brand),
    model = COALESCE($3, model), serial_number = COALESCE($4, serial_number), under_warranty = COALESCE($5, under_warranty),
    warranty_end = CASE WHEN $6::boolean THEN $7::date ELSE warranty_end END WHERE id = $8 RETURNING *`,
    [body.deviceType || null, text(body.brand, 'الماركة', 60), text(body.model, 'الموديل', 60), text(body.serialNumber, 'الرقم التسلسلي', 120), body.underWarranty ?? null, body.warrantyEnd !== undefined, body.warrantyEnd || null, req.params.deviceId]);
  if (!rows[0]) fail(404, 'الجهاز غير موجود');
  return res.json({ device: toDeviceDto(rows[0]) });
});

router.post('/', requireRole('reception', 'manager'), async (req, res) => {
  const body = req.body || {};
  const customerId = positiveId(body.customerId);
  const deviceId = positiveId(body.deviceId);
  const issue = text(body.issueDescription, 'وصف العطل', 2000, true);
  const priority = body.priority ?? 'normal';
  if (!['normal', 'urgent'].includes(priority)) fail(400, 'الأولوية غير صحيحة');
  const id = await transaction(async (client) => {
    const customer = (await client.query('SELECT c.address FROM devices d JOIN customers c ON c.id = d.customer_id WHERE d.id = $1 AND c.id = $2', [deviceId, customerId])).rows[0];
    if (!customer) fail(400, 'الجهاز لا يخص هذا العميل');
    const serviceAddress = body.serviceAddress == null ? customer.address : text(body.serviceAddress, 'عنوان الزيارة', 300);
    const orderId = Number((await client.query("SELECT nextval(pg_get_serial_sequence('work_orders','id')) AS id")).rows[0].id);
    const code = `WO-${String(orderId).padStart(6, '0')}`;
    await client.query(`INSERT INTO work_orders (id, code, customer_id, device_id, branch, issue_description, priority, created_by, service_address)
      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`, [orderId, code, customerId, deviceId, BRANCH, issue, priority, req.user.id, serviceAddress]);
    await createChecklist(client, orderId);
    await recordEvent(client, orderId, req.user.id, 'تم إنشاء طلب الصيانة');
    return orderId;
  });
  return sendOrder(res, id, 201);
});
router.get('/', requireRole('reception', 'manager'), async (req, res) => {
  const { limit, offset } = pagination(req.query);
  const where = [], params = [];
  if (req.query.status) {
    const statuses = String(req.query.status).split(',').map((value) => value.trim());
    if (statuses.some((status) => !STATUS_LABELS[status])) fail(400, 'حالة الطلب غير صحيحة');
    params.push(statuses); where.push(`wo.status = ANY($${params.length})`);
  }
  if (req.query.branch) { params.push(req.query.branch); where.push(`wo.branch = $${params.length}`); }
  if (req.query.search) {
    params.push(`%${text(req.query.search, 'البحث', 200)}%`);
    where.push(`(wo.code ILIKE $${params.length} OR c.name ILIKE $${params.length} OR c.phone ILIKE $${params.length} OR wo.issue_description ILIKE $${params.length})`);
  }
  const whereSql = where.length ? `WHERE ${where.join(' AND ')}` : '';
  const total = Number((await pool.query(`SELECT count(*) FROM work_orders wo JOIN customers c ON c.id = wo.customer_id ${whereSql}`, params)).rows[0].count);
  params.push(limit, offset);
  const { rows } = await pool.query(`SELECT wo.*, c.name AS customer_name, c.phone AS customer_phone, COALESCE(wo.service_address, c.address) AS customer_address,
    d.device_type, t.name AS technician_name FROM work_orders wo JOIN customers c ON c.id = wo.customer_id
    JOIN devices d ON d.id = wo.device_id LEFT JOIN users t ON t.id = wo.technician_id ${whereSql}
    ORDER BY wo.created_at DESC, wo.id DESC LIMIT $${params.length - 1} OFFSET $${params.length}`, params);
  return res.json({ total, limit, offset, items: rows.map(toSummary) });
});
function toSummary(row) {
  return { id: row.id, code: row.code, status: row.status, statusLabel: STATUS_LABELS[row.status], priority: row.priority,
    issueDescription: row.issue_description, branch: row.branch, source: row.source, scheduledAt: row.scheduled_at,
    createdAt: row.created_at, startedAt: row.started_at, completedAt: row.completed_at, closedAt: row.closed_at,
    customerName: row.customer_name, customerPhone: row.customer_phone, customerAddress: row.customer_address,
    deviceType: row.device_type, technicianName: row.technician_name };
}
router.get('/mine', requireRole('technician'), async (req, res) => {
  const { rows } = await pool.query(`SELECT id FROM work_orders WHERE technician_id = $1 AND (
    (scheduled_at AT TIME ZONE '${TIME_ZONE}')::date = (now() AT TIME ZONE '${TIME_ZONE}')::date
    OR status IN ('in_progress', 'paused', 'awaiting_approval')
    OR (closed_at AT TIME ZONE '${TIME_ZONE}')::date = (now() AT TIME ZONE '${TIME_ZONE}')::date)
    ORDER BY scheduled_at ASC NULLS LAST, id`, [req.user.id]);
  const items = await Promise.all(rows.map((row) => loadWorkOrderDetail(row.id)));
  const relevantItems = items.filter((item) => item.status !== 'rejected');
  return res.json({ items, progress: { completed: relevantItems.filter((item) => item.status === 'closed').length, total: relevantItems.length } });
});
router.get('/:id', async (req, res) => { await canReadOrder(req); return sendOrder(res, req.params.id); });
router.patch('/:id', requireRole('reception', 'manager'), async (req, res) => {
  const body = req.body || {};
  if (body.priority && !['normal', 'urgent'].includes(body.priority)) fail(400, 'الأولوية غير صحيحة');
  await transaction(async (client) => {
    await lockOrder(client, req, ['new', 'assigned', 'in_progress', 'paused', 'awaiting_approval']);
    const issue = text(body.issueDescription, 'وصف العطل', 2000, body.issueDescription !== undefined);
    await client.query(`UPDATE work_orders SET issue_description = COALESCE($1, issue_description), priority = COALESCE($2, priority), branch = CASE WHEN $3 THEN $4 ELSE branch END WHERE id = $5`, [issue, body.priority || null, body.branch !== undefined, BRANCH, req.params.id]);
    await recordEvent(client, req.params.id, req.user.id, 'تم تحديث بيانات الطلب', { issueDescription: issue, priority: body.priority ?? null });
  });
  return sendOrder(res, req.params.id);
});
router.patch('/:id/assign', requireRole('reception', 'manager'), async (req, res) => {
  const technicianId = positiveId(req.body.technicianId);
  const scheduledAt = req.body.scheduledAt;
  if (!scheduledTimestamp(scheduledAt)) fail(400, 'موعد الزيارة يجب أن يتضمن المنطقة الزمنية');
  await transaction(async (client) => {
    await lockOrder(client, req, ['new', 'assigned', 'paused']);
    const technician = (await client.query("SELECT name FROM users WHERE id = $1 AND role = 'technician' AND active = true FOR SHARE", [technicianId])).rows[0];
    if (!technician) fail(400, 'الفني غير موجود أو حسابه غير نشط');
    await client.query("UPDATE work_orders SET technician_id = $1, scheduled_at = $2, status = 'assigned' WHERE id = $3", [technicianId, scheduledAt, req.params.id]);
    await recordEvent(client, req.params.id, req.user.id, 'تم إسناد الطلب وتحديد الزيارة', { technicianId, technicianName: technician.name, scheduledAt });
  });
  return sendOrder(res, req.params.id);
});
for (const [path, allowedStatuses, nextStatus, action] of [
  ['start', ['assigned', 'paused'], 'in_progress', 'بدأ الفني العمل'],
  ['pause', ['in_progress'], 'paused', 'توقف العمل مؤقتاً'],
  ['approve', ['awaiting_approval'], 'in_progress', 'تم تسجيل موافقة العميل'],
  ['reject', ['awaiting_approval'], 'rejected', 'تم تسجيل رفض العميل'],
]) {
  router.post(`/:id/${path}`, requireRole(...(path === 'start' || path === 'pause' ? ['technician'] : ['reception', 'manager'])), async (req, res) => {
    await transaction(async (client) => {
      await lockOrder(client, req, allowedStatuses);
      const reason = path === 'reject' ? text(req.body?.reason, 'سبب الرفض', 1000) : null;
      await client.query(`UPDATE work_orders SET status = $1,
        started_at = CASE WHEN $2 = 'start' THEN COALESCE(started_at, now()) ELSE started_at END,
        approved_at = CASE WHEN $2 = 'approve' THEN now() ELSE approved_at END,
        reject_reason = CASE WHEN $2 = 'reject' THEN $3 ELSE reject_reason END,
        closed_at = CASE WHEN $2 = 'reject' THEN now() ELSE closed_at END WHERE id = $4`, [nextStatus, path, reason, req.params.id]);
      await recordEvent(client, req.params.id, req.user.id, action, reason ? { reason } : {});
    });
    return sendOrder(res, req.params.id);
  });
}
router.patch('/:id/quote', requireRole('technician'), async (req, res) => {
  const estimatedCost = money(req.body.estimatedCost, 'التكلفة التقديرية');
  const note = text(req.body.note, 'ملاحظات العرض', 1000);
  await transaction(async (client) => {
    await lockOrder(client, req, ['in_progress', 'paused']);
    await client.query("UPDATE work_orders SET status = 'awaiting_approval', estimated_cost = $1, quote_note = $2, quoted_at = now(), approved_at = NULL, reject_reason = NULL WHERE id = $3", [estimatedCost, note, req.params.id]);
    await recordEvent(client, req.params.id, req.user.id, 'تم إرسال عرض سعر للعميل', { estimatedCost, note });
  });
  return sendOrder(res, req.params.id);
});
router.patch('/checklist-items/:itemId', requireRole('technician'), async (req, res) => {
  const { status } = req.body || {};
  if (!['pending', 'done', 'issue'].includes(status)) fail(400, 'حالة الفحص غير صحيحة');
  const note = text(req.body.note, 'ملاحظات الفحص', 1000);
  const orderId = await transaction(async (client) => {
    const item = (await client.query('SELECT work_order_id, label FROM checklist_items WHERE id = $1', [req.params.itemId])).rows[0];
    if (!item) fail(404, 'بند الفحص غير موجود');
    await lockOrder(client, { ...req, params: { id: item.work_order_id } }, ['in_progress', 'paused']);
    await client.query('UPDATE checklist_items SET status = $1, note = COALESCE($2, note) WHERE id = $3', [status, note, req.params.itemId]);
    await recordEvent(client, item.work_order_id, req.user.id, 'تم تحديث بند الفحص', { label: item.label, status, note });
    return item.work_order_id;
  });
  return sendOrder(res, orderId);
});
router.post('/:id/parts', requireRole('technician'), async (req, res) => {
  const name = text(req.body.name, 'اسم القطعة', 160, true);
  const status = req.body.status || 'used';
  if (!['used', 'requested', 'unavailable'].includes(status)) fail(400, 'حالة القطعة غير صحيحة');
  const inventoryItemId = req.body.inventoryItemId ? positiveId(req.body.inventoryItemId) : null;
  if (status === 'used' && !inventoryItemId) fail(400, 'اختر قطعة من المخزون لتسجيل سعرها وكميتها الفعلية');
  await transaction(async (client) => {
    await lockOrder(client, req, ['in_progress', 'paused']);
    let price = 0, actualName = name;
    if (inventoryItemId) {
      const item = (await client.query('SELECT * FROM inventory_items WHERE id = $1 FOR UPDATE', [inventoryItemId])).rows[0];
      if (!item) fail(404, 'القطعة غير موجودة في المخزون');
      price = Number(item.price); actualName = item.name;
      if (status === 'used') {
        if (item.quantity < 1) fail(409, 'القطعة غير متوفرة في المخزون');
        await client.query('UPDATE inventory_items SET quantity = quantity - 1 WHERE id = $1', [inventoryItemId]);
      }
    }
    await client.query('INSERT INTO parts_used (work_order_id, inventory_item_id, name, price, status) VALUES ($1,$2,$3,$4,$5)', [req.params.id, inventoryItemId, actualName, price, status]);
    if (status === 'requested' && inventoryItemId) await client.query("INSERT INTO purchase_orders (inventory_item_id, work_order_id, quantity, status) VALUES ($1,$2,1,'pending')", [inventoryItemId, req.params.id]);
    await recordEvent(client, req.params.id, req.user.id, 'تم تسجيل قطعة غيار', { name: actualName, status, price });
  });
  return sendOrder(res, req.params.id, 201);
});
router.patch('/:id/parts/:partId', requireRole('technician'), async (req, res) => {
  if (req.body?.status !== 'used') fail(400, 'هذا الإجراء مخصص لتسجيل استخدام القطعة');
  await transaction(async (client) => {
    await lockOrder(client, req, ['in_progress', 'paused']);
    const part = (await client.query('SELECT * FROM parts_used WHERE id = $1 AND work_order_id = $2 FOR UPDATE', [req.params.partId, req.params.id])).rows[0];
    if (!part) fail(404, 'القطعة غير موجودة');
    const inventoryItemId = req.body.inventoryItemId == null ? part.inventory_item_id : positiveId(req.body.inventoryItemId);
    if (!inventoryItemId) fail(400, 'اختر قطعة من المخزون');
    if (part.status === 'used') {
      if (part.inventory_item_id !== inventoryItemId) fail(409, 'أزل القطعة المستخدمة أولاً لتغيير مصدرها');
      return;
    }
    const item = (await client.query('SELECT * FROM inventory_items WHERE id = $1 FOR UPDATE', [inventoryItemId])).rows[0];
    if (!item) fail(404, 'القطعة غير موجودة في المخزون');
    if (item.quantity < 1) fail(409, 'القطعة غير متوفرة في المخزون');
    await client.query('UPDATE inventory_items SET quantity = quantity - 1 WHERE id = $1', [inventoryItemId]);
    await client.query("UPDATE parts_used SET inventory_item_id = $1, name = $2, price = $3, status = 'used' WHERE id = $4", [inventoryItemId, item.name, item.price, part.id]);
    await client.query("DELETE FROM purchase_orders WHERE work_order_id = $1 AND inventory_item_id = $2 AND status = 'pending'", [req.params.id, part.inventory_item_id]);
    await recordEvent(client, req.params.id, req.user.id, 'تم استخدام قطعة الغيار المطلوبة', { name: item.name, price: Number(item.price) });
  });
  return sendOrder(res, req.params.id);
});

// Resolve/remove an unused request after procurement or when it is no longer needed.
router.delete('/:id/parts/:partId', requireRole('technician'), async (req, res) => {
  await transaction(async (client) => {
    await lockOrder(client, req, ['in_progress', 'paused']);
    const part = (await client.query('SELECT * FROM parts_used WHERE id = $1 AND work_order_id = $2 FOR UPDATE', [req.params.partId, req.params.id])).rows[0];
    if (!part) fail(404, 'القطعة غير موجودة');
    if (part.status === 'used' && part.inventory_item_id) await client.query('UPDATE inventory_items SET quantity = quantity + 1 WHERE id = $1', [part.inventory_item_id]);
    if (part.status === 'requested') await client.query("DELETE FROM purchase_orders WHERE work_order_id = $1 AND inventory_item_id = $2 AND status = 'pending'", [req.params.id, part.inventory_item_id]);
    await client.query('DELETE FROM parts_used WHERE id = $1', [part.id]);
    await recordEvent(client, req.params.id, req.user.id, 'تم إزالة قطعة من الطلب', { name: part.name, status: part.status });
  });
  return sendOrder(res, req.params.id);
});
router.post('/:id/photos', requireRole('technician'), async (req, res) => {
  const { kind } = req.body || {};
  if (!['before', 'after'].includes(kind)) fail(400, 'نوع الصورة غير صحيح');
  const image = imageData(req.body.imageData, true);
  await transaction(async (client) => {
    await lockOrder(client, req, ['assigned', 'in_progress', 'paused']);
    await client.query('INSERT INTO work_order_photos (work_order_id, kind, image_data) VALUES ($1,$2,$3)', [req.params.id, kind, image]);
    await recordEvent(client, req.params.id, req.user.id, kind === 'before' ? 'تم إضافة صورة قبل الإصلاح' : 'تم إضافة صورة بعد الإصلاح');
  });
  return sendOrder(res, req.params.id, 201);
});
router.get('/:id/invoice-preview', requireRole('technician', 'manager', 'reception'), async (req, res) => {
  await canReadOrder(req);
  const workOrder = await loadWorkOrderDetail(req.params.id);
  const parts = workOrder.parts.filter((part) => part.status === 'used');
  const saved = workOrder.invoice;
  if (workOrder.status === 'closed' && saved) {
    const { laborFee, partsTotal, warrantyDiscount, taxRate, subtotal, total } = saved;
    return res.json({ laborFee, partsTotal, warrantyDiscount, taxRate, subtotal, total, parts });
  }
  const laborFee = req.query.laborFee === undefined ? (saved?.laborFee ?? 0) : money(req.query.laborFee, 'تكلفة العمل');
  const discount = req.query.warrantyDiscount === undefined ? (saved?.warrantyDiscount ?? 0) : money(req.query.warrantyDiscount, 'خصم الضمان');
  const partsTotal = saved?.partsTotal ?? parts.reduce((total, part) => total + part.price, 0);
  if (discount > laborFee + partsTotal) fail(400, 'خصم الضمان أكبر من قيمة الفاتورة');
  const amounts = invoiceTotals({ laborFee, warrantyDiscount: discount, taxRate: saved?.taxRate ?? 0.14 }, partsTotal);
  return res.json({ ...amounts, parts });
});
router.post('/:id/close', requireRole('technician'), async (req, res) => {
  const body = req.body || {};
  const laborFee = money(body.laborFee, 'تكلفة العمل');
  const discount = money(body.warrantyDiscount ?? 0, 'خصم الضمان');
  if (!['card', 'cash', 'account'].includes(body.paymentMethod)) fail(400, 'طريقة الدفع غير صحيحة');
  const signatureName = text(body.signatureName, 'اسم العميل', 120, true);
  const signatureImage = imageData(body.signatureImage, true);
  const customerRating = body.customerRating ?? null;
  if (customerRating !== null && (!Number.isInteger(customerRating) || customerRating < 1 || customerRating > 5)) fail(400, 'التقييم يجب أن يكون بين ١ و٥');
  await transaction(async (client) => {
    const order = await lockOrder(client, req);
    if (order.status === 'closed') return; // An interrupted mobile response can be retried safely.
    if (order.status !== 'in_progress' && order.status !== 'completed') fail(409, 'يجب بدء العمل والموافقة على العرض قبل إغلاق الطلب');
    if ((await client.query("SELECT id FROM checklist_items WHERE work_order_id = $1 AND status != 'done' LIMIT 1", [order.id])).rowCount) fail(409, 'أكمل جميع بنود الفحص قبل إغلاق الطلب');
    if ((await client.query("SELECT id FROM parts_used WHERE work_order_id = $1 AND status != 'used' LIMIT 1", [order.id])).rowCount) fail(409, 'قم بتسوية قطع الغيار المطلوبة أو غير المتوفرة قبل إغلاق الطلب');
    const partsTotal = Number((await client.query("SELECT COALESCE(sum(price), 0) AS total FROM parts_used WHERE work_order_id = $1 AND status = 'used'", [order.id])).rows[0].total);
    if (discount > laborFee + partsTotal) fail(400, 'خصم الضمان أكبر من قيمة الفاتورة');
    await client.query(`INSERT INTO invoices (work_order_id, labor_fee, warranty_discount, parts_total, tax_rate, payment_method, signature_name, signature_image, signed_at, customer_rating)
      VALUES ($1,$2,$3,$4,0.14,$5,$6,$7,now(),$8)`, [order.id, laborFee, discount, partsTotal, body.paymentMethod, signatureName, signatureImage, customerRating]);
    await client.query("UPDATE work_orders SET status = 'closed', completed_at = COALESCE(completed_at, now()), closed_at = now() WHERE id = $1", [order.id]);
    await recordEvent(client, order.id, req.user.id, 'تم إغلاق الطلب وإصدار الفاتورة', { laborFee, warrantyDiscount: discount, partsTotal, paymentMethod: body.paymentMethod });
  });
  return sendOrder(res, req.params.id);
});
router.use(handleError);
module.exports = router;
module.exports.loadWorkOrderDetail = loadWorkOrderDetail;
module.exports.toSummary = toSummary;
