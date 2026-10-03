const express = require('express');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

router.get('/', requireAuth, requireRole('manager', 'technician', 'reception'), async (req, res) => {
  const filter = req.query.filter || 'all';

  let where = '';
  if (filter === 'low') where = 'WHERE i.quantity <= i.reorder_threshold';
  if (filter === 'pending') where = "WHERE EXISTS (SELECT 1 FROM purchase_orders po WHERE po.inventory_item_id = i.id AND po.status = 'pending')";

  const { rows } = await pool.query(
    `SELECT i.*,
            (SELECT wo.code FROM purchase_orders po
               JOIN work_orders wo ON wo.id = po.work_order_id
               WHERE po.inventory_item_id = i.id AND po.status = 'pending'
               ORDER BY po.created_at DESC LIMIT 1) AS reserved_for_code
     FROM inventory_items i
     ${where}
     ORDER BY (i.quantity <= i.reorder_threshold) DESC, i.name ASC`
  );

  const pendingCount = await pool.query(
    "SELECT count(*) FROM purchase_orders WHERE status = 'pending'"
  );

  const consumptionThisMonth = await pool.query(
    `SELECT coalesce(sum(p.price), 0) AS total,
            coalesce(sum(p.price) FILTER (WHERE i.category IN ('مكيفات')), 0) AS compressor_share
     FROM parts_used p
     JOIN work_orders wo ON wo.id = p.work_order_id
     LEFT JOIN inventory_items i ON i.id = p.inventory_item_id
     WHERE p.status = 'used' AND wo.created_at >= date_trunc('month', now())`
  );

  return res.json({
    items: rows.map((r) => ({
      id: r.id,
      name: r.name,
      sku: r.sku,
      category: r.category,
      price: Number(r.price),
      quantity: r.quantity,
      reorderThreshold: r.reorder_threshold,
      lowStock: r.quantity <= r.reorder_threshold,
      reservedForCode: r.reserved_for_code,
    })),
    pendingPurchaseOrders: Number(pendingCount.rows[0].count),
    consumptionThisMonth: Number(consumptionThisMonth.rows[0].total),
  });
});

router.post('/:id/purchase-order', requireAuth, requireRole('manager', 'technician'), async (req, res) => {
  const { id } = req.params;
  const { quantity = 1, workOrderId = null } = req.body || {};

  const item = await pool.query('SELECT * FROM inventory_items WHERE id = $1', [id]);
  if (!item.rows[0]) {
    return res.status(404).json({ error: 'Inventory item not found' });
  }

  const { rows } = await pool.query(
    `INSERT INTO purchase_orders (inventory_item_id, work_order_id, quantity, status)
     VALUES ($1, $2, $3, 'ordered') RETURNING *`,
    [id, workOrderId, quantity]
  );
  const po = rows[0];

  return res.status(201).json({
    purchaseOrder: {
      id: po.id,
      inventoryItemId: po.inventory_item_id,
      workOrderId: po.work_order_id,
      quantity: po.quantity,
      status: po.status,
    },
  });
});

module.exports = router;
