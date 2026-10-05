const { pool } = require('./db');

const BRANCH = 'الحدايق';
const DEVICE_TYPES = ['مكيف', 'غسالة', 'ثلاجة', 'سخان', 'بوتاجاز', 'أخرى'];
const TIME_ZONE = 'Africa/Cairo';
const STATUS_LABELS = {
  new: 'طلب جديد', assigned: 'مُعيَّن', in_progress: 'قيد التنفيذ', paused: 'متوقفة مؤقتاً',
  awaiting_approval: 'بانتظار موافقة العميل', rejected: 'رفض العميل العرض',
  completed: 'مكتملة', closed: 'مغلقة',
};

function fail(status, message) {
  const error = new Error(message);
  error.status = status;
  throw error;
}
function handleError(error, req, res, next) {
  if (error.status) return res.status(error.status).json({ error: error.message });
  if (error.code === '23505') return res.status(409).json({ error: 'هذا السجل موجود بالفعل' });
  return next(error);
}
function positiveId(value) {
  const number = Number(value);
  if ((typeof value !== 'number' && typeof value !== 'string') || String(value).trim() === '' || !Number.isSafeInteger(number) || number < 1) fail(400, 'معرّف غير صحيح');
  return number;
}
function pagination(query) {
  const limit = query.limit === undefined ? 30 : Number(query.limit);
  const offset = query.offset === undefined ? 0 : Number(query.offset);
  if (!Number.isSafeInteger(limit) || limit < 1 || !Number.isSafeInteger(offset) || offset < 0) {
    fail(400, 'قيم تقسيم الصفحات غير صحيحة');
  }
  return { limit: Math.min(limit, 100), offset };
}
function text(value, label, max = 500, required = false) {
  if (value === undefined || value === null) {
    if (required) fail(400, `${label} مطلوب`);
    return null;
  }
  if (typeof value !== 'string' || value.trim().length > max || (required && !value.trim())) {
    fail(400, `${label} غير صحيح`);
  }
  return value.trim();
}
function calendarDate(value) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && Number(value.slice(0, 4)) >= 1
    && !Number.isNaN(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
}
function scheduledTimestamp(value) {
  if (typeof value !== 'string') return false;
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?(Z|[+-]\d{2}:\d{2})$/.exec(value);
  if (!match || !calendarDate(match[1]) || Number(match[2]) > 23 || Number(match[3]) > 59 || Number(match[4] || 0) > 59 || Number.isNaN(Date.parse(value))) return false;
  if (match[5] !== 'Z') {
    const hours = Number(match[5].slice(1, 3));
    const minutes = Number(match[5].slice(4));
    if (hours > 14 || minutes > 59 || (hours === 14 && minutes !== 0)) return false;
  }
  return true;
}
function money(value, label) {
  if ((typeof value !== 'number' && typeof value !== 'string') || String(value).trim() === '') fail(400, `${label} غير صحيح`);
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0 || number > 99999999.99 || Math.abs(number * 100 - Math.round(number * 100)) > 0.00001) {
    fail(400, `${label} يجب أن يكون مبلغاً صحيحاً غير سالب`);
  }
  return number;
}
function roundMoney(value) { return Math.round((value + Number.EPSILON) * 100) / 100; }
function invoiceTotals(invoice, usedPartsTotal) {
  const laborFee = Number(invoice.labor_fee ?? invoice.laborFee ?? 0);
  const warrantyDiscount = Number(invoice.warranty_discount ?? invoice.warrantyDiscount ?? 0);
  const taxRate = Number(invoice.tax_rate ?? invoice.taxRate ?? 0.14);
  const partsTotal = Number(invoice.parts_total ?? usedPartsTotal ?? 0);
  const subtotal = roundMoney(laborFee + partsTotal - warrantyDiscount);
  return { laborFee, partsTotal, warrantyDiscount, taxRate, subtotal, total: roundMoney(subtotal * (1 + taxRate)) };
}
function revenueExpr(alias = 'wo') {
  return `round((i.labor_fee - i.warranty_discount + COALESCE(i.parts_total,
    (SELECT sum(p.price) FROM parts_used p WHERE p.work_order_id = ${alias}.id AND p.status = 'used'), 0)) * (1 + i.tax_rate), 2)`;
}
function imageData(value, required = false) {
  if (!value && !required) return null;
  if (typeof value !== 'string') fail(400, 'الصورة غير صحيحة');
  const raw = value.replace(/^data:image\/(png|jpeg);base64,/, '');
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(raw) || raw.length % 4 !== 0 || raw.length > 2800000) fail(400, 'الصورة غير صحيحة أو حجمها أكبر من ٢ ميجابايت');
  const bytes = Buffer.from(raw, 'base64');
  const png = bytes.length >= 8 && bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
  const jpeg = bytes.length >= 3 && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;
  if ((!png && !jpeg) || bytes.length > 2 * 1024 * 1024) fail(400, 'الصورة يجب أن تكون PNG أو JPEG');
  return raw;
}
async function transaction(fn) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await fn(client);
    await client.query('COMMIT');
    return result;
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally { client.release(); }
}
async function createChecklist(client, id) {
  const labels = ['فحص الجهاز وتحديد العطل', 'تنفيذ الإصلاح', 'اختبار الجهاز بعد الإصلاح'];
  for (let index = 0; index < labels.length; index += 1) {
    await client.query('INSERT INTO checklist_items (work_order_id, label, order_index) VALUES ($1,$2,$3)', [id, labels[index], index]);
  }
}
async function recordEvent(client, id, userId, action, metadata = {}) {
  await client.query('INSERT INTO work_order_events (work_order_id, user_id, action, metadata) VALUES ($1,$2,$3,$4)', [id, userId, action, JSON.stringify(metadata)]);
}
module.exports = { BRANCH, DEVICE_TYPES, TIME_ZONE, STATUS_LABELS, fail, handleError, positiveId, pagination, text, calendarDate, scheduledTimestamp, money, roundMoney, invoiceTotals, revenueExpr, imageData, transaction, createChecklist, recordEvent };
