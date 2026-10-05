const express = require('express');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const { fail, positiveId, text, money, transaction, recordEvent, handleError, roundMoney } = require('../domain');
const router = express.Router();
router.use(requireAuth);
for (const parameter of ['id', 'purchaseOrderId']) router.param(parameter, (req, res, next, value) => { positiveId(value); next(); });
function quantity(value, label, minimum = 0) {
  const result = Number(value);
  if ((typeof value !== 'number' && typeof value !== 'string') || String(value).trim() === '' || !Number.isSafeInteger(result) || result < minimum || result > 1000000) fail(400, `${label} غير صحيح`);
  return result;
}
function toItem(row) {
  return { id: row.id, name: row.name, sku: row.sku, category: row.category, price: Number(row.price),
    quantity: row.quantity, reorderThreshold: row.reorder_threshold, lowStock: row.quantity <= row.reorder_threshold,
    reservedForCode: row.reserved_for_code ?? null };
}
function toPurchaseOrder(row) {
  return { id: row.id, inventoryItemId: row.inventory_item_id, workOrderId: row.work_order_id,
    quantity: row.quantity, status: row.status, createdAt: row.created_at,
    itemName: row.item_name ?? null, workOrderCode: row.work_order_code ?? null };
}
router.get('/', requireRole('manager', 'technician', 'reception'), async (req, res) => {
  const filter = req.query.filter || 'all';
  if (!['all', 'low', 'pending'].includes(filter)) fail(400, 'فلتر المخزون غير صحيح');
  const where = filter === 'low' ? 'WHERE i.quantity <= i.reorder_threshold' : filter === 'pending' ? "WHERE EXISTS (SELECT 1 FROM purchase_orders po WHERE po.inventory_item_id = i.id AND po.status IN ('pending', 'ordered'))" : '';
  const { rows } = await pool.query(`SELECT i.*,
    (SELECT wo.code FROM purchase_orders po JOIN work_orders wo ON wo.id = po.work_order_id
      WHERE po.inventory_item_id = i.id AND po.status IN ('pending', 'ordered') ORDER BY po.created_at DESC LIMIT 1) AS reserved_for_code
    FROM inventory_items i ${where} ORDER BY (i.quantity <= i.reorder_threshold) DESC, i.name ASC`);
  const pending = await pool.query(`SELECT po.*, i.name AS item_name, wo.code AS work_order_code FROM purchase_orders po
    JOIN inventory_items i ON i.id = po.inventory_item_id LEFT JOIN work_orders wo ON wo.id = po.work_order_id
    WHERE po.status IN ('pending', 'ordered') ORDER BY po.created_at ASC`);
  const consumption = (await pool.query("SELECT COALESCE(sum(price), 0) AS total FROM parts_used WHERE status = 'used' AND created_at >= date_trunc('month', now())")).rows[0];
  return res.json({ items: rows.map(toItem), pendingPurchaseOrders: pending.rowCount,
    purchaseOrders: pending.rows.map(toPurchaseOrder), consumptionThisMonth: roundMoney(Number(consumption.total)) });
});
router.post('/', requireRole('manager'), async (req, res) => {
  const body = req.body || {};
  const name = text(body.name, 'اسم القطعة', 160, true);
  const sku = text(body.sku, 'كود القطعة', 80, true);
  const price = money(body.price, 'سعر القطعة');
  const count = quantity(body.quantity ?? 0, 'الكمية');
  const threshold = quantity(body.reorderThreshold ?? 0, 'حد إعادة الطلب');
  const { rows } = await pool.query('INSERT INTO inventory_items (name, sku, category, price, quantity, reorder_threshold) VALUES ($1,$2,$3,$4,$5,$6) RETURNING *', [name, sku, text(body.category, 'التصنيف', 80), price, count, threshold]);
  return res.status(201).json({ item: toItem(rows[0]) });
});
router.patch('/:id', requireRole('manager'), async (req, res) => {
  const body = req.body || {};
  const { rows } = await pool.query(`UPDATE inventory_items SET name = COALESCE($1, name), sku = COALESCE($2, sku),
    category = COALESCE($3, category), price = COALESCE($4, price), quantity = COALESCE($5, quantity),
    reorder_threshold = COALESCE($6, reorder_threshold) WHERE id = $7 RETURNING *`,
    [body.name === undefined ? null : text(body.name, 'اسم القطعة', 160, true), body.sku === undefined ? null : text(body.sku, 'كود القطعة', 80, true),
      text(body.category, 'التصنيف', 80), body.price === undefined ? null : money(body.price, 'سعر القطعة'),
      body.quantity === undefined ? null : quantity(body.quantity, 'الكمية'),
      body.reorderThreshold === undefined ? null : quantity(body.reorderThreshold, 'حد إعادة الطلب'), req.params.id]);
  if (!rows[0]) fail(404, 'القطعة غير موجودة');
  return res.json({ item: toItem(rows[0]) });
});
router.post('/:id/purchase-order', requireRole('manager', 'technician'), async (req, res) => {
  const body = req.body || {};
  const count = quantity(body.quantity ?? 1, 'كمية الشراء', 1);
  const workOrderId = body.workOrderId == null ? null : positiveId(body.workOrderId);
  const purchaseOrder = await transaction(async (client) => {
    if (workOrderId) {
      const order = (await client.query('SELECT technician_id, status FROM work_orders WHERE id = $1 FOR UPDATE', [workOrderId])).rows[0];
      if (!order) fail(404, 'طلب الصيانة غير موجود');
      if (req.user.role === 'technician' && order.technician_id !== req.user.id) fail(403, 'هذا الطلب غير مسند إليك');
      if (['closed', 'rejected'].includes(order.status)) fail(409, 'طلب الصيانة مغلق');
    } else if (req.user.role === 'technician') fail(400, 'حدد طلب الصيانة المرتبط بالشراء');
    if (!(await client.query('SELECT id FROM inventory_items WHERE id = $1', [req.params.id])).rowCount) fail(404, 'القطعة غير موجودة');
    const row = (await client.query("INSERT INTO purchase_orders (inventory_item_id, work_order_id, quantity, status) VALUES ($1,$2,$3,'ordered') RETURNING *", [req.params.id, workOrderId, count])).rows[0];
    if (workOrderId) await recordEvent(client, workOrderId, req.user.id, 'تم إنشاء طلب شراء قطعة غيار', { inventoryItemId: Number(req.params.id), quantity: count });
    return row;
  });
  return res.status(201).json({ purchaseOrder: toPurchaseOrder(purchaseOrder) });
});
router.post('/purchase-orders/:purchaseOrderId/receive', requireRole('manager'), async (req, res) => {
  const purchaseOrder = await transaction(async (client) => {
    const reference = (await client.query('SELECT work_order_id FROM purchase_orders WHERE id = $1', [req.params.purchaseOrderId])).rows[0];
    if (!reference) fail(404, 'طلب الشراء غير موجود');
    // Match repair mutations' lock order: order, purchase order, inventory.
    if (reference.work_order_id) await client.query('SELECT id FROM work_orders WHERE id = $1 FOR UPDATE', [reference.work_order_id]);
    const row = (await client.query('SELECT * FROM purchase_orders WHERE id = $1 FOR UPDATE', [req.params.purchaseOrderId])).rows[0];
    if (!row) fail(404, 'طلب الشراء غير موجود');
    if (row.status === 'received') return row;
    await client.query('UPDATE inventory_items SET quantity = quantity + $1 WHERE id = $2', [row.quantity, row.inventory_item_id]);
    await client.query("UPDATE purchase_orders SET status = 'received' WHERE id = $1", [row.id]);
    if (row.work_order_id) await recordEvent(client, row.work_order_id, req.user.id, 'تم استلام قطعة غيار بالمخزون', { inventoryItemId: row.inventory_item_id, quantity: row.quantity });
    return { ...row, status: 'received' };
  });
  return res.json({ purchaseOrder: toPurchaseOrder(purchaseOrder) });
});
router.use(handleError);
module.exports = router;
