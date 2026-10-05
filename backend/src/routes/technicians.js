const express = require('express');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const { initials } = require('./auth');
const { TIME_ZONE, fail, positiveId, pagination, calendarDate, handleError } = require('../domain');
const { loadWorkOrderDetail, toSummary } = require('./workOrders');
const router = express.Router();
router.use(requireAuth);
router.param('id', (req, res, next, value) => { positiveId(value); next(); });
const today = `(now() AT TIME ZONE '${TIME_ZONE}')::date`;
const rosterQuery = `SELECT u.id, u.name, u.branch, u.title, u.specialty, u.phone, u.email, u.active,
  count(wo.id) FILTER (WHERE wo.status = 'closed' AND wo.closed_at >= date_trunc('month', now())) AS completed_this_month,
  avg(i.customer_rating) FILTER (WHERE wo.status = 'closed' AND wo.closed_at >= now() - interval '90 days') AS avg_rating,
  avg(extract(epoch FROM (wo.closed_at - wo.created_at)) / 86400.0) FILTER (WHERE wo.status = 'closed' AND wo.closed_at >= now() - interval '90 days') AS avg_repair_days,
  count(wo.id) FILTER (WHERE (wo.scheduled_at AT TIME ZONE '${TIME_ZONE}')::date = ${today}
    AND wo.status IN ('assigned', 'in_progress', 'paused', 'awaiting_approval')) AS tasks_today,
  bool_or(wo.status = 'in_progress') AS is_busy,
  max(wo.code) FILTER (WHERE wo.status = 'in_progress') AS current_work_order_code
  FROM users u LEFT JOIN work_orders wo ON wo.technician_id = u.id LEFT JOIN invoices i ON i.work_order_id = wo.id`;
function toTechnician(row) {
  return { id: row.id, name: row.name, initials: initials(row.name), branch: row.branch,
    specialty: row.specialty, phone: row.phone, email: row.email, active: row.active,
    tasksToday: Number(row.tasks_today), completedThisMonth: Number(row.completed_this_month),
    avgRating: row.avg_rating === null ? null : Math.round(Number(row.avg_rating) * 10) / 10,
    avgRepairDays: row.avg_repair_days === null ? null : Math.round(Number(row.avg_repair_days) * 10) / 10,
    isBusy: Boolean(row.is_busy), currentWorkOrderCode: row.current_work_order_code,
    // No location telemetry or end-time estimate exists. Do not present invented values.
    distanceKm: null, busyUntil: null };
}
async function technicianDetail(id) {
  const row = (await pool.query(`${rosterQuery} WHERE u.id = $1 AND u.role = 'technician' GROUP BY u.id`, [id])).rows[0];
  if (!row) fail(404, 'الفني غير موجود');
  return toTechnician(row);
}
router.get('/', requireRole('reception', 'manager'), async (req, res) => {
  const { rows } = await pool.query(`${rosterQuery} WHERE u.role = 'technician' AND u.active = true GROUP BY u.id ORDER BY u.name ASC`);
  return res.json({ technicians: rows.map(toTechnician) });
});
router.get('/roster', requireRole('manager'), async (req, res) => {
  const { rows } = await pool.query(`${rosterQuery} WHERE u.role = 'technician' GROUP BY u.id ORDER BY u.active DESC, u.name ASC`);
  return res.json({ technicians: rows.map(toTechnician) });
});
router.get('/me/performance', requireRole('technician'), async (req, res) => {
  const stats = (await pool.query(`SELECT
    count(wo.id) FILTER (WHERE wo.status = 'closed' AND wo.closed_at >= date_trunc('month', now())) AS completed_this_month,
    count(wo.id) FILTER (WHERE wo.status = 'closed' AND wo.closed_at >= now() - interval '90 days') AS completed_last_90_days,
    avg(i.customer_rating) FILTER (WHERE wo.status = 'closed' AND wo.closed_at >= now() - interval '90 days') AS avg_rating,
    avg(extract(epoch FROM (wo.closed_at - wo.created_at)) / 86400.0) FILTER (WHERE wo.status = 'closed' AND wo.closed_at >= now() - interval '90 days') AS avg_repair_days,
    count(wo.id) FILTER (WHERE wo.reopened_from_id IS NOT NULL AND wo.status = 'closed' AND wo.closed_at >= now() - interval '90 days')::numeric
      / GREATEST(count(wo.id) FILTER (WHERE wo.status = 'closed' AND wo.closed_at >= now() - interval '90 days'), 1) AS reopen_rate
    FROM work_orders wo LEFT JOIN invoices i ON i.work_order_id = wo.id WHERE wo.technician_id = $1`, [req.user.id])).rows[0];
  const { rows } = await pool.query(`SELECT wo.id, wo.code, wo.closed_at, d.device_type, c.name AS customer_name, i.customer_rating
    FROM work_orders wo JOIN devices d ON d.id = wo.device_id JOIN customers c ON c.id = wo.customer_id
    LEFT JOIN invoices i ON i.work_order_id = wo.id WHERE wo.technician_id = $1 AND wo.status = 'closed' ORDER BY wo.closed_at DESC, wo.id DESC LIMIT 15`, [req.user.id]);
  return res.json({ completedThisMonth: Number(stats.completed_this_month), completedLast90Days: Number(stats.completed_last_90_days),
    avgRating: stats.avg_rating === null ? null : Math.round(Number(stats.avg_rating) * 10) / 10,
    avgRepairDays: stats.avg_repair_days === null ? null : Math.round(Number(stats.avg_repair_days) * 10) / 10,
    reopenRatePct: Math.round(Number(stats.reopen_rate) * 1000) / 10,
    recentWorkOrders: rows.map((row) => ({ id: row.id, code: row.code, deviceType: row.device_type, customerName: row.customer_name, closedAt: row.closed_at, customerRating: row.customer_rating })) });
});
router.get('/:id/itinerary', requireRole('manager'), async (req, res) => {
  const technician = await technicianDetail(req.params.id);
  const currentDate = (await pool.query(`SELECT to_char(now() AT TIME ZONE '${TIME_ZONE}', 'YYYY-MM-DD') AS date`)).rows[0].date;
  const date = req.query.date ?? currentDate;
  if (!calendarDate(date)) fail(400, 'تاريخ خط السير غير صحيح');
  const { rows } = await pool.query(`SELECT id FROM work_orders WHERE technician_id = $1 AND (
    (scheduled_at AT TIME ZONE '${TIME_ZONE}')::date = $2::date
    OR (started_at AT TIME ZONE '${TIME_ZONE}')::date = $2::date
    OR (closed_at AT TIME ZONE '${TIME_ZONE}')::date = $2::date
    OR ($2::date = ${today} AND status IN ('in_progress', 'paused', 'awaiting_approval')))
    ORDER BY scheduled_at ASC NULLS LAST, created_at ASC, id ASC`, [req.params.id, date]);
  return res.json({ technician, date, items: await Promise.all(rows.map((row) => loadWorkOrderDetail(row.id))) });
});
router.get('/:id/history', requireRole('manager'), async (req, res) => {
  await technicianDetail(req.params.id);
  const { limit, offset } = pagination(req.query);
  const total = Number((await pool.query('SELECT count(*) FROM work_orders WHERE technician_id = $1', [req.params.id])).rows[0].count);
  const { rows } = await pool.query(`SELECT wo.*, c.name AS customer_name, c.phone AS customer_phone, COALESCE(wo.service_address, c.address) AS customer_address,
    d.device_type, t.name AS technician_name FROM work_orders wo JOIN customers c ON c.id = wo.customer_id
    JOIN devices d ON d.id = wo.device_id JOIN users t ON t.id = wo.technician_id WHERE wo.technician_id = $1
    ORDER BY wo.created_at DESC, wo.id DESC LIMIT $2 OFFSET $3`, [req.params.id, limit, offset]);
  return res.json({ total, limit, offset, items: rows.map(toSummary) });
});
router.use(handleError);
module.exports = router;
