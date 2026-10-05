require('dotenv').config();
const path = require('path');
const express = require('express');
// Must be required before any route files are required: it patches Express so a
// rejected promise inside an `async (req, res) => {...}` handler is forwarded to the
// error middleware below instead of becoming an unhandled rejection that crashes the
// whole process (this actually happened during development when Postgres blipped).
require('express-async-errors');
const cors = require('cors');

const PLACEHOLDER_SECRET = 'change-this-secret-in-production';
if (process.env.NODE_ENV === 'production' && (!process.env.JWT_SECRET || process.env.JWT_SECRET === PLACEHOLDER_SECRET)) {
  console.error('Refusing to start: set a real JWT_SECRET (not the .env.example placeholder) before running in production.');
  process.exit(1);
}

const { router: authRouter } = require('./routes/auth');
const workOrdersRouter = require('./routes/workOrders');
const techniciansRouter = require('./routes/technicians');
const inventoryRouter = require('./routes/inventory');
const dashboardRouter = require('./routes/dashboard');
const customersRouter = require('./routes/customers');
const reportsRouter = require('./routes/reports');
const employeesRouter = require('./routes/employees');
const publicRouter = require('./routes/public');

const app = express();
app.use(cors());
// Default 100kb is too small for a base64-encoded signature/photo upload in the body.
app.use(express.json({ limit: '8mb' }));

app.get('/health', (req, res) => res.json({ ok: true }));

// Public customer-facing website (request a repair, track status) — same server, same
// database, so the site and the staff app are one ecosystem rather than two things that
// have to be kept in sync.
app.use(express.static(path.join(__dirname, '..', 'public')));
app.use('/api/public', publicRouter);

app.use('/api/auth', authRouter);
app.use('/api/work-orders', workOrdersRouter);
app.use('/api/technicians', techniciansRouter);
app.use('/api/inventory', inventoryRouter);
app.use('/api/dashboard', dashboardRouter);
app.use('/api/customers', customersRouter);
app.use('/api/reports', reportsRouter);
app.use('/api/employees', employeesRouter);

app.use((err, req, res, next) => {
  if (!err.status || err.status >= 500) console.error({ message: err.message, code: err.code });
  res.status(err.status || 500).json({ error: err.status ? err.message : 'حصلت مشكلة أثناء حفظ البيانات. حاول مرة ثانية.' });
});

const port = process.env.PORT || 3000;
app.listen(port, () => console.log(`API listening on :${port}`));
