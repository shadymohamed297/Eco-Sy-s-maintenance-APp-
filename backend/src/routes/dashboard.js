const express = require('express');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

router.get('/reception', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const branch = req.query.branch || req.user.branch;

  const counts = await pool.query(
    `SELECT
       count(*) FILTER (WHERE status = 'new') AS new_count,
       count(*) FILTER (WHERE status IN ('assigned', 'in_progress', 'paused')) AS in_progress_count,
       count(*) FILTER (
         WHERE status IN ('new', 'assigned')
         AND scheduled_at IS NOT NULL AND scheduled_at < now()
       ) AS late_count
     FROM work_orders`
  );

  const approvals = await pool.query(
    `SELECT wo.id, wo.code, wo.issue_description, wo.created_at,
            c.name AS customer_name, d.device_type
     FROM work_orders wo
     JOIN customers c ON c.id = wo.customer_id
     JOIN devices d ON d.id = wo.device_id
     WHERE wo.status = 'awaiting_approval'
     ORDER BY wo.created_at ASC`
  );

  const appointments = await pool.query(
    `SELECT wo.id, wo.code, wo.issue_description, wo.scheduled_at, wo.status, wo.priority,
            c.name AS customer_name, c.branch, d.device_type,
            t.name AS technician_name
     FROM work_orders wo
     JOIN customers c ON c.id = wo.customer_id
     JOIN devices d ON d.id = wo.device_id
     LEFT JOIN users t ON t.id = wo.technician_id
     WHERE wo.scheduled_at::date = current_date
     ORDER BY wo.scheduled_at ASC`
  );

  const row = counts.rows[0];
  return res.json({
    branch,
    counts: {
      new: Number(row.new_count),
      inProgress: Number(row.in_progress_count),
      late: Number(row.late_count),
    },
    pendingApprovals: {
      count: approvals.rowCount,
      oldestHours: approvals.rowCount
        ? Math.floor((Date.now() - new Date(approvals.rows[0].created_at)) / 3600000)
        : 0,
      items: approvals.rows.map((r) => ({
        id: r.id,
        code: r.code,
        issueDescription: r.issue_description,
        createdAt: r.created_at,
        customerName: r.customer_name,
        deviceType: r.device_type,
      })),
    },
    todaysAppointments: appointments.rows.map((r) => ({
      id: r.id,
      code: r.code,
      issueDescription: r.issue_description,
      scheduledAt: r.scheduled_at,
      status: r.status,
      priority: r.priority,
      customerName: r.customer_name,
      branch: r.branch,
      deviceType: r.device_type,
      technicianName: r.technician_name,
    })),
  });
});

router.get('/manager', requireAuth, requireRole('manager'), async (req, res) => {
  const period = req.query.period === 'quarter' || req.query.period === 'year' ? req.query.period : 'month';
  const interval = { month: '30 days', quarter: '90 days', year: '365 days' }[period];
  const prevInterval = { month: '60 days', quarter: '180 days', year: '730 days' }[period];

  const kpis = await pool.query(
    `WITH current_period AS (
       SELECT * FROM work_orders WHERE closed_at >= now() - $1::interval
     ), previous_period AS (
       SELECT * FROM work_orders WHERE closed_at >= now() - $2::interval AND closed_at < now() - $1::interval
     )
     SELECT
       (SELECT count(*) FROM current_period) AS completed_count,
       (SELECT count(*) FROM previous_period) AS prev_completed_count,
       (SELECT avg(extract(epoch FROM (closed_at - created_at)) / 86400.0) FROM current_period) AS avg_repair_days,
       (SELECT count(*) FILTER (WHERE reopened_from_id IS NOT NULL)::numeric
          / GREATEST(count(*), 1) FROM current_period) AS reopen_rate,
       (SELECT avg(i.customer_rating) FROM invoices i JOIN current_period cp ON cp.id = i.work_order_id) AS avg_rating,
       (SELECT count(*) FROM invoices i JOIN current_period cp ON cp.id = i.work_order_id
          WHERE i.customer_rating IS NOT NULL) AS rating_count`,
    [interval, prevInterval]
  );

  const weeklyRevenue = await pool.query(
    `SELECT date_trunc('week', wo.closed_at) AS week_start,
            sum(i.labor_fee + i.warranty_discount
                + COALESCE((SELECT sum(p.price) FROM parts_used p WHERE p.work_order_id = wo.id AND p.status = 'used'), 0)
            ) * (1 + avg(i.tax_rate)) AS revenue
     FROM work_orders wo
     JOIN invoices i ON i.work_order_id = wo.id
     WHERE wo.closed_at >= now() - $1::interval
     GROUP BY week_start
     ORDER BY week_start ASC`,
    [interval]
  );

  const faultDistribution = await pool.query(
    `SELECT d.device_type,
            count(*) AS total,
            round(100.0 * count(*) / GREATEST(sum(count(*)) OVER (), 1), 1) AS pct
     FROM work_orders wo
     JOIN devices d ON d.id = wo.device_id
     WHERE wo.created_at >= now() - $1::interval
     GROUP BY d.device_type
     ORDER BY total DESC`,
    [interval]
  );

  const row = kpis.rows[0];
  const completed = Number(row.completed_count);
  const prevCompleted = Number(row.prev_completed_count);
  const completedDeltaPct = prevCompleted > 0
    ? Math.round(((completed - prevCompleted) / prevCompleted) * 1000) / 10
    : null;

  return res.json({
    period,
    kpis: {
      completedCount: completed,
      completedDeltaPct,
      avgRepairDays: row.avg_repair_days ? Math.round(Number(row.avg_repair_days) * 10) / 10 : null,
      reopenRatePct: row.reopen_rate ? Math.round(Number(row.reopen_rate) * 1000) / 10 : 0,
      avgRating: row.avg_rating ? Math.round(Number(row.avg_rating) * 10) / 10 : null,
      ratingCount: Number(row.rating_count),
    },
    weeklyRevenue: weeklyRevenue.rows.map((r) => ({
      weekStart: r.week_start,
      revenue: Math.round(Number(r.revenue)),
    })),
    faultDistribution: faultDistribution.rows.map((r) => ({
      deviceType: r.device_type,
      total: Number(r.total),
      pct: Number(r.pct),
    })),
  });
});

module.exports = router;
