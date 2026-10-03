-- نظام مركز صيانة متكامل — schema
-- Roles: reception, technician, manager (fixed per account)

CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  email TEXT UNIQUE,
  phone TEXT UNIQUE,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('reception', 'technician', 'manager')),
  branch TEXT,
  title TEXT,
  specialty TEXT,
  distance_km NUMERIC(4, 1),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT users_login_identifier CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS customers (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  phone TEXT NOT NULL,
  address TEXT,
  branch TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS devices (
  id SERIAL PRIMARY KEY,
  customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  device_type TEXT NOT NULL CHECK (device_type IN ('مكيف', 'غسالة', 'ثلاجة', 'سخان', 'بوتاجاز', 'أخرى')),
  brand TEXT,
  model TEXT,
  serial_number TEXT,
  under_warranty BOOLEAN NOT NULL DEFAULT false,
  warranty_end DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS work_orders (
  id SERIAL PRIMARY KEY,
  code TEXT UNIQUE NOT NULL,
  customer_id INTEGER NOT NULL REFERENCES customers(id),
  device_id INTEGER NOT NULL REFERENCES devices(id),
  branch TEXT NOT NULL,
  issue_description TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'new'
    CHECK (status IN ('new', 'assigned', 'in_progress', 'paused', 'awaiting_approval', 'completed', 'closed')),
  priority TEXT NOT NULL DEFAULT 'normal' CHECK (priority IN ('normal', 'urgent')),
  technician_id INTEGER REFERENCES users(id),
  scheduled_at TIMESTAMPTZ,
  reopened_from_id INTEGER REFERENCES work_orders(id),
  created_by INTEGER REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  closed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_work_orders_technician ON work_orders(technician_id);
CREATE INDEX IF NOT EXISTS idx_work_orders_status ON work_orders(status);

CREATE TABLE IF NOT EXISTS checklist_items (
  id SERIAL PRIMARY KEY,
  work_order_id INTEGER NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
  label TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'done', 'issue')),
  note TEXT,
  order_index INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inventory_items (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  sku TEXT UNIQUE NOT NULL,
  category TEXT,
  price NUMERIC(10, 2) NOT NULL,
  quantity INTEGER NOT NULL DEFAULT 0,
  reorder_threshold INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS parts_used (
  id SERIAL PRIMARY KEY,
  work_order_id INTEGER NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
  inventory_item_id INTEGER REFERENCES inventory_items(id),
  name TEXT NOT NULL,
  price NUMERIC(10, 2) NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'used' CHECK (status IN ('used', 'requested', 'unavailable')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS purchase_orders (
  id SERIAL PRIMARY KEY,
  inventory_item_id INTEGER NOT NULL REFERENCES inventory_items(id),
  work_order_id INTEGER REFERENCES work_orders(id),
  quantity INTEGER NOT NULL DEFAULT 1,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'ordered', 'received')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS invoices (
  id SERIAL PRIMARY KEY,
  work_order_id INTEGER UNIQUE NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
  labor_fee NUMERIC(10, 2) NOT NULL DEFAULT 0,
  warranty_discount NUMERIC(10, 2) NOT NULL DEFAULT 0,
  tax_rate NUMERIC(4, 3) NOT NULL DEFAULT 0.14,
  payment_method TEXT CHECK (payment_method IN ('card', 'cash', 'account')),
  signature_name TEXT,
  signed_at TIMESTAMPTZ,
  customer_rating SMALLINT CHECK (customer_rating BETWEEN 1 AND 5),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Additive migrations (safe to re-run: schema.sql is applied on every deploy) ─────

-- Employee account management: lets a manager deactivate a departed/lost-phone
-- employee's access immediately, without deleting their historical records.
ALTER TABLE users ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;

-- Customer-approval quote workflow on a work order.
ALTER TABLE work_orders ADD COLUMN IF NOT EXISTS estimated_cost NUMERIC(10, 2);
ALTER TABLE work_orders ADD COLUMN IF NOT EXISTS quote_note TEXT;
ALTER TABLE work_orders ADD COLUMN IF NOT EXISTS quoted_at TIMESTAMPTZ;
ALTER TABLE work_orders ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;
ALTER TABLE work_orders ADD COLUMN IF NOT EXISTS reject_reason TEXT;

ALTER TABLE work_orders DROP CONSTRAINT IF EXISTS work_orders_status_check;
ALTER TABLE work_orders ADD CONSTRAINT work_orders_status_check
  CHECK (status IN ('new', 'assigned', 'in_progress', 'paused', 'awaiting_approval', 'rejected', 'completed', 'closed'));

-- Public website intake: lets reception tell a customer-submitted-online request apart
-- from one phoned/walked in and taken by staff.
ALTER TABLE work_orders ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'staff';
ALTER TABLE work_orders DROP CONSTRAINT IF EXISTS work_orders_source_check;
ALTER TABLE work_orders ADD CONSTRAINT work_orders_source_check CHECK (source IN ('staff', 'online'));

-- The customer's actual signature strokes, not just the typed name under it.
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS signature_image TEXT;

-- Before/after repair photos, taken with the device camera on-site.
CREATE TABLE IF NOT EXISTS work_order_photos (
  id SERIAL PRIMARY KEY,
  work_order_id INTEGER NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('before', 'after')),
  image_data TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
