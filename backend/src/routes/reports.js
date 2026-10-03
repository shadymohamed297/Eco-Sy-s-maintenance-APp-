const express = require('express');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

const INTERVALS = { month: '30 days', quarter: '90 days', year: '365 days' };

function revenueExpr(alias = 'wo') {
  return `(
    i.labor_fee - i.warranty_discount
    + COALESCE((SELECT sum(p.price) FROM parts_used p WHERE p.work_order_id = ${alias}.id AND p.status = 'used'), 0)
  ) * (1 + i.tax_rate)`;
}

router.get('/', requireAuth, requireRole('manager'), async (req, res) => {
  const period = INTERVALS[req.query.period] ? req.query.period : 'month';
  const interval = INTERVALS[period];

  const totals = await pool.query(
    `SELECT count(*) AS completed_count,
            coalesce(sum(${revenueExpr()}), 0) AS total_revenue,
            avg(extract(epoch FROM (wo.closed_at - wo.created_at)) / 86400.0) AS avg_repair_days
     FROM work_orders wo
     JOIN invoices i ON i.work_order_id = wo.id
     WHERE wo.closed_at >= now() - $1::interval`,
    [interval]
  );

  const byTechnician = await pool.query(
    `SELECT t.id, t.name, count(*) AS completed_count,
            coalesce(sum(${revenueExpr()}), 0) AS revenue,
            avg(i.customer_rating) AS avg_rating
     FROM work_orders wo
     JOIN invoices i ON i.work_order_id = wo.id
     JOIN users t ON t.id = wo.technician_id
     WHERE wo.closed_at >= now() - $1::interval
     GROUP BY t.id, t.name
     ORDER BY revenue DESC`,
    [interval]
  );

  const byBranch = await pool.query(
    `SELECT coalesce(wo.branch, 'غير محدد') AS branch, count(*) AS completed_count,
            coalesce(sum(${revenueExpr()}), 0) AS revenue
     FROM work_orders wo
     JOIN invoices i ON i.work_order_id = wo.id
     WHERE wo.closed_at >= now() - $1::interval
     GROUP BY wo.branch
     ORDER BY revenue DESC`,
    [interval]
  );

  const byDeviceType = await pool.query(
    `SELECT d.device_type, count(*) AS completed_count,
            coalesce(sum(${revenueExpr()}), 0) AS revenue
     FROM work_orders wo
     JOIN invoices i ON i.work_order_id = wo.id
     JOIN devices d ON d.id = wo.device_id
     WHERE wo.closed_at >= now() - $1::interval
     GROUP BY d.device_type
     ORDER BY revenue DESC`,
    [interval]
  );

  const t = totals.rows[0];
  return res.json({
    period,
    totals: {
      completedCount: Number(t.completed_count),
      totalRevenue: Math.round(Number(t.total_revenue)),
      avgRepairDays: t.avg_repair_days ? Math.round(Number(t.avg_repair_days) * 10) / 10 : null,
    },
    byTechnician: byTechnician.rows.map((r) => ({
      id: r.id,
      name: r.name,
      completedCount: Number(r.completed_count),
      revenue: Math.round(Number(r.revenue)),
      avgRating: r.avg_rating ? Math.round(Number(r.avg_rating) * 10) / 10 : null,
    })),
    byBranch: byBranch.rows.map((r) => ({
      branch: r.branch,
      completedCount: Number(r.completed_count),
      revenue: Math.round(Number(r.revenue)),
    })),
    byDeviceType: byDeviceType.rows.map((r) => ({
      deviceType: r.device_type,
      completedCount: Number(r.completed_count),
      revenue: Math.round(Number(r.revenue)),
    })),
  });
});

function csvEscape(value) {
  const s = value === null || value === undefined ? '' : String(value);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

router.get('/export', requireAuth, requireRole('manager'), async (req, res) => {
  const period = INTERVALS[req.query.period] ? req.query.period : 'month';
  const interval = INTERVALS[period];

  const { rows } = await pool.query(
    `SELECT wo.code, c.name AS customer_name, c.phone AS customer_phone, d.device_type,
            t.name AS technician_name, wo.branch, wo.created_at, wo.closed_at,
            i.labor_fee, i.warranty_discount, i.payment_method, i.customer_rating,
            COALESCE((SELECT sum(p.price) FROM parts_used p WHERE p.work_order_id = wo.id AND p.status = 'used'), 0) AS parts_total,
            ${revenueExpr()} AS total
     FROM work_orders wo
     JOIN invoices i ON i.work_order_id = wo.id
     JOIN customers c ON c.id = wo.customer_id
     JOIN devices d ON d.id = wo.device_id
     LEFT JOIN users t ON t.id = wo.technician_id
     WHERE wo.closed_at >= now() - $1::interval
     ORDER BY wo.closed_at DESC`,
    [interval]
  );

  const header = [
    'code', 'customer_name', 'customer_phone', 'device_type', 'technician_name', 'branch',
    'created_at', 'closed_at', 'labor_fee', 'parts_total', 'warranty_discount', 'total',
    'payment_method', 'customer_rating',
  ];
  const lines = [header.join(',')];
  for (const r of rows) {
    lines.push([
      r.code, r.customer_name, r.customer_phone, r.device_type, r.technician_name, r.branch,
      r.created_at?.toISOString(), r.closed_at?.toISOString(),
      r.labor_fee, r.parts_total, r.warranty_discount, Math.round(Number(r.total) * 100) / 100,
      r.payment_method, r.customer_rating,
    ].map(csvEscape).join(','));
  }

  res.setHeader('Content-Type', 'text/csv; charset=utf-8');
  res.setHeader('Content-Disposition', `attachment; filename="report-${period}.csv"`);
  // BOM so Excel opens the Arabic customer/technician names as UTF-8 instead of mangling them.
  return res.send('﻿' + lines.join('\n'));
});

module.exports = router;
