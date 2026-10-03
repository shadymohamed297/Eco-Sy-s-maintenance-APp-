const express = require('express');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const { initials } = require('./auth');

const router = express.Router();

router.get('/', requireAuth, requireRole('reception', 'manager'), async (req, res) => {
  const { rows } = await pool.query(
    `SELECT u.id, u.name, u.branch, u.title, u.specialty, u.distance_km,
            count(wo.id) FILTER (
              WHERE wo.scheduled_at::date = current_date
              AND wo.status IN ('assigned', 'in_progress', 'paused')
            ) AS tasks_today,
            max(wo.started_at) FILTER (WHERE wo.status IN ('in_progress', 'paused')) AS current_started_at
     FROM users u
     LEFT JOIN work_orders wo ON wo.technician_id = u.id
     WHERE u.role = 'technician'
     GROUP BY u.id
     ORDER BY u.distance_km ASC NULLS LAST`
  );

  const technicians = rows.map((r) => {
    const busyUntil = r.current_started_at
      ? new Date(new Date(r.current_started_at).getTime() + 2 * 3600000)
      : null;
    const isBusyNow = busyUntil ? busyUntil > new Date() : false;
    return {
      id: r.id,
      name: r.name,
      initials: initials(r.name),
      specialty: r.specialty,
      distanceKm: r.distance_km !== null ? Number(r.distance_km) : null,
      tasksToday: Number(r.tasks_today),
      busyUntil: isBusyNow ? busyUntil : null,
    };
  });

  return res.json({ technicians });
});

router.get('/roster', requireAuth, requireRole('manager'), async (req, res) => {
  const { rows } = await pool.query(
    `SELECT u.id, u.name, u.branch, u.title, u.specialty, u.phone, u.email,
            count(wo.id) FILTER (WHERE wo.closed_at >= date_trunc('month', now())) AS completed_this_month,
            avg(i.customer_rating) FILTER (WHERE wo.closed_at >= now() - interval '90 days') AS avg_rating,
            avg(extract(epoch FROM (wo.closed_at - wo.created_at)) / 86400.0)
              FILTER (WHERE wo.closed_at >= now() - interval '90 days') AS avg_repair_days,
            count(wo.id) FILTER (
              WHERE wo.scheduled_at::date = current_date AND wo.status IN ('assigned', 'in_progress', 'paused')
            ) AS tasks_today
     FROM users u
     LEFT JOIN work_orders wo ON wo.technician_id = u.id
     LEFT JOIN invoices i ON i.work_order_id = wo.id
     WHERE u.role = 'technician'
     GROUP BY u.id
     ORDER BY u.name ASC`
  );

  return res.json({
    technicians: rows.map((r) => ({
      id: r.id,
      name: r.name,
      initials: initials(r.name),
      branch: r.branch,
      specialty: r.specialty,
      phone: r.phone,
      email: r.email,
      tasksToday: Number(r.tasks_today),
      completedThisMonth: Number(r.completed_this_month),
      avgRating: r.avg_rating ? Math.round(Number(r.avg_rating) * 10) / 10 : null,
      avgRepairDays: r.avg_repair_days ? Math.round(Number(r.avg_repair_days) * 10) / 10 : null,
    })),
  });
});

router.get('/me/performance', requireAuth, requireRole('technician'), async (req, res) => {
  const stats = await pool.query(
    `SELECT
       count(wo.id) FILTER (WHERE wo.closed_at >= date_trunc('month', now())) AS completed_this_month,
       count(wo.id) FILTER (WHERE wo.closed_at >= now() - interval '90 days') AS completed_last_90_days,
       avg(i.customer_rating) FILTER (WHERE wo.closed_at >= now() - interval '90 days') AS avg_rating,
       avg(extract(epoch FROM (wo.closed_at - wo.created_at)) / 86400.0)
         FILTER (WHERE wo.closed_at >= now() - interval '90 days') AS avg_repair_days,
       count(wo.id) FILTER (WHERE wo.reopened_from_id IS NOT NULL AND wo.closed_at >= now() - interval '90 days')::numeric
         / GREATEST(count(wo.id) FILTER (WHERE wo.closed_at >= now() - interval '90 days'), 1) AS reopen_rate
     FROM work_orders wo
     LEFT JOIN invoices i ON i.work_order_id = wo.id
     WHERE wo.technician_id = $1`,
    [req.user.id]
  );

  const recent = await pool.query(
    `SELECT wo.id, wo.code, wo.closed_at, d.device_type, c.name AS customer_name, i.customer_rating
     FROM work_orders wo
     JOIN devices d ON d.id = wo.device_id
     JOIN customers c ON c.id = wo.customer_id
     LEFT JOIN invoices i ON i.work_order_id = wo.id
     WHERE wo.technician_id = $1 AND wo.status = 'closed'
     ORDER BY wo.closed_at DESC
     LIMIT 15`,
    [req.user.id]
  );

  const s = stats.rows[0];
  return res.json({
    completedThisMonth: Number(s.completed_this_month),
    completedLast90Days: Number(s.completed_last_90_days),
    avgRating: s.avg_rating ? Math.round(Number(s.avg_rating) * 10) / 10 : null,
    avgRepairDays: s.avg_repair_days ? Math.round(Number(s.avg_repair_days) * 10) / 10 : null,
    reopenRatePct: s.reopen_rate ? Math.round(Number(s.reopen_rate) * 1000) / 10 : 0,
    recentWorkOrders: recent.rows.map((r) => ({
      id: r.id,
      code: r.code,
      deviceType: r.device_type,
      customerName: r.customer_name,
      closedAt: r.closed_at,
      customerRating: r.customer_rating,
    })),
  });
});

module.exports = router;
