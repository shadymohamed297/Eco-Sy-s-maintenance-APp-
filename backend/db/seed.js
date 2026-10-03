require('dotenv').config();
const bcrypt = require('bcryptjs');
const { pool } = require('../src/db');

const DEFAULT_PASSWORD = 'Passw0rd!';

async function seed() {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    await client.query(
      `TRUNCATE purchase_orders, invoices, parts_used, checklist_items, work_orders,
       devices, customers, inventory_items, users RESTART IDENTITY CASCADE`
    );

    const passwordHash = await bcrypt.hash(DEFAULT_PASSWORD, 10);

    const users = {};
    const insertUser = async (key, name, email, phone, role, branch, title, specialty, distanceKm) => {
      const { rows } = await client.query(
        `INSERT INTO users (name, email, phone, password_hash, role, branch, title, specialty, distance_km)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) RETURNING id`,
        [name, email, phone, passwordHash, role, branch, title, specialty, distanceKm]
      );
      users[key] = rows[0].id;
    };

    await insertUser('mona', 'منى صلاح', 'mona@markazsayana.eg', '+201001112223', 'reception', 'مدينة نصر', 'موظفة استقبال', null, null);
    await insertUser('khaled', 'خالد إبراهيم', 'khaled@markazsayana.eg', '+201002223334', 'manager', 'القاهرة', 'مدير المركز', null, null);
    await insertUser('mahmoud', 'محمود السيد', 'mahmoud@markazsayana.eg', '+201003334445', 'technician', 'مدينة نصر', 'فني أجهزة منزلية', 'تكييف وتبريد', 6);
    await insertUser('amr', 'عمرو فتحي', 'amr@markazsayana.eg', '+201004445556', 'technician', 'المعادي', 'فني أجهزة منزلية', 'سخانات وبوتاجاز', 11);
    await insertUser('mostafa', 'مصطفى كامل', 'mostafa@markazsayana.eg', '+201005556667', 'technician', 'الرحاب', 'فني أجهزة منزلية', 'غسالات وثلاجات', 14);

    const customers = {};
    const insertCustomer = async (key, name, phone, address, branch) => {
      const { rows } = await client.query(
        `INSERT INTO customers (name, phone, address, branch) VALUES ($1, $2, $3, $4) RETURNING id`,
        [name, phone, address, branch]
      );
      customers[key] = rows[0].id;
    };

    await insertCustomer('ahmed', 'م. أحمد شعراوي', '+201002148830', 'مدينة نصر، شارع مصطفى النحاس', 'مدينة نصر');
    await insertCustomer('heba', 'أ. هبة عبد الرحمن', '+201115557766', 'المعادي، شارع ٩', 'المعادي');
    await insertCustomer('rehab_compound', 'كومباوند الرحاب — عقد سنوي', '+201227778899', 'الرحاب، التجمع الأول', 'الرحاب');
    await insertCustomer('sara', 'أ. سارة يوسف', '+201118889900', 'مدينة نصر، الحي السابع', 'مدينة نصر');
    await insertCustomer('tarek', 'م. طارق فهمي', '+201229990011', 'المعادي الجديدة', 'المعادي');
    await insertCustomer('nour', 'أ. نور الدين حسن', '+201009991122', 'الرحاب، مجاورة ٣', 'الرحاب');

    const devices = {};
    const insertDevice = async (key, customerKey, type, brand, model, serial, underWarranty, warrantyEnd) => {
      const { rows } = await client.query(
        `INSERT INTO devices (customer_id, device_type, brand, model, serial_number, under_warranty, warranty_end)
         VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id`,
        [customers[customerKey], type, brand, model, serial, underWarranty, warrantyEnd]
      );
      devices[key] = rows[0].id;
    };

    await insertDevice('ahmed_ac', 'ahmed', 'مكيف', 'سامسونج', 'AR18TVH', 'SN-44-90218', true, '2027-03-20');
    await insertDevice('heba_washer', 'heba', 'غسالة', 'توشيبا', 'AW-DJ1000', 'SN-11-23871', false, null);
    await insertDevice('rehab_central_ac', 'rehab_compound', 'مكيف', 'كاريير', 'Central-40T', 'SN-77-10021', false, null);
    await insertDevice('sara_fridge', 'sara', 'ثلاجة', 'إل جي', 'GR-B247', 'SN-90-44120', false, null);
    await insertDevice('tarek_heater', 'tarek', 'سخان', 'إيديال', 'IH-50L', 'SN-22-98341', false, null);
    await insertDevice('nour_stove', 'nour', 'بوتاجاز', 'يونيفرسال', 'UNV-5B', 'SN-33-77102', false, null);

    const workOrders = {};
    const insertWO = async (key, code, customerKey, deviceKey, branch, issue, status, priority, techKey, scheduledAt, createdAt, startedAt, completedAt, closedAt) => {
      const { rows } = await client.query(
        `INSERT INTO work_orders
         (code, customer_id, device_id, branch, issue_description, status, priority, technician_id,
          scheduled_at, created_by, created_at, started_at, completed_at, closed_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) RETURNING id`,
        [code, customers[customerKey], devices[deviceKey], branch, issue, status, priority,
         techKey ? users[techKey] : null, scheduledAt, users.mona, createdAt, startedAt, completedAt, closedAt]
      );
      workOrders[key] = rows[0].id;
    };

    const today = new Date();
    const at = (h, m, dayOffset = 0) => {
      const d = new Date(today);
      d.setDate(d.getDate() + dayOffset);
      d.setHours(h, m, 0, 0);
      return d;
    };

    // The primary in-progress work order shown across screens 4-6
    await insertWO('wo2418', 'WO-2418', 'ahmed', 'ahmed_ac', 'مدينة نصر',
      'يعمل المكيف لكن لا يبرّد، ويوجد صوت من الوحدة الخارجية.',
      'in_progress', 'urgent', 'mahmoud', at(10, 30), at(9, 0, -1), at(10, 34), null, null);

    await insertWO('wo_heba', 'WO-2419', 'heba', 'heba_washer', 'المعادي',
      'تسريب مياه من أسفل الغسالة أثناء الغسيل.', 'new', 'normal', null, at(13, 0), at(9, 15), null, null, null);

    await insertWO('wo_rehab', 'WO-2420', 'rehab_compound', 'rehab_central_ac', 'الرحاب',
      'صيانة دورية سنوية للمكيف المركزي — عقد صيانة.', 'assigned', 'normal', 'mahmoud', at(15, 30), at(8, 0), null, null, null);

    await insertWO('wo_sara', 'WO-2417', 'sara', 'sara_fridge', 'مدينة نصر',
      'الثلاجة لا تبرد بشكل كافٍ — صوت غير طبيعي من الكمبروسر.', 'closed', 'normal', 'mahmoud',
      at(9, 0), at(8, 30, -1), at(9, 5), at(9, 35), at(9, 40));

    await insertWO('wo_tarek', 'WO-2421', 'tarek', 'tarek_heater', 'المعادي',
      'السخان لا يسخن الماء بالشكل الكافي.', 'assigned', 'normal', 'amr', at(14, 0, 1), at(9, 40), null, null, null);

    await insertWO('wo_nour', 'WO-2422', 'nour', 'nour_stove', 'الرحاب',
      'شعلة واحدة في البوتاجاز لا تشتعل.', 'new', 'urgent', null, null, at(9, 50), null, null, null);

    // Awaiting customer approval on a repair quote (shown in reception banner)
    await insertWO('wo_approval1', 'WO-2410', 'sara', 'sara_fridge', 'مدينة نصر',
      'تغيير كمبروسر الثلاجة — بانتظار موافقة العميل على عرض السعر.', 'awaiting_approval', 'normal', 'mostafa',
      null, at(9, 0, -3), at(9, 30, -3), null, null);
    await insertWO('wo_approval2', 'WO-2411', 'tarek', 'tarek_heater', 'المعادي',
      'استبدال ثرموستات السخان — بانتظار موافقة العميل على عرض السعر.', 'awaiting_approval', 'normal', 'amr',
      null, at(10, 0, -3), at(10, 20, -3), null, null);
    await insertWO('wo_approval3', 'WO-2412', 'nour', 'nour_stove', 'الرحاب',
      'استبدال صمام غاز — بانتظار موافقة العميل على عرض السعر.', 'awaiting_approval', 'normal', 'mostafa',
      null, at(11, 0, -3), at(11, 15, -3), null, null);

    // Historical work orders for manager dashboard aggregates (last ~30 days, all branches/technicians)
    const historyIssues = [
      ['مكيف', 'مكيف سبليت لا يبرد بالشكل الكافي', 'مدينة نصر', 'mahmoud'],
      ['غسالة', 'الغسالة تصدر صوتاً عالياً أثناء العصر', 'المعادي', 'amr'],
      ['ثلاجة', 'تسريب مياه أسفل الثلاجة', 'مدينة نصر', 'mostafa'],
      ['سخان', 'صمام الأمان يسرب مياه', 'المعادي', 'amr'],
      ['بوتاجاز', 'رائحة غاز عند التشغيل', 'الرحاب', 'mostafa'],
      ['مكيف', 'المكيف يعمل لكنه يبرد ببطء', 'الرحاب', 'mahmoud'],
      ['غسالة', 'الغسالة لا تدور نهائياً', 'مدينة نصر', 'amr'],
      ['ثلاجة', 'استبدال ثرموستات', 'المعادي', 'mostafa'],
    ];
    for (let i = 0; i < 24; i += 1) {
      const [type, issue, branch, techKey] = historyIssues[i % historyIssues.length];
      const dayOffset = -1 * (2 + (i % 28));
      const customerKey = ['ahmed', 'heba', 'sara', 'tarek', 'nour'][i % 5];
      const deviceKey = { ahmed: 'ahmed_ac', heba: 'heba_washer', sara: 'sara_fridge', tarek: 'tarek_heater', nour: 'nour_stove' }[customerKey];
      const reopened = i % 9 === 0;
      const code = `WO-${2100 + i}`;
      const createdAt = at(9, 0, dayOffset);
      const startedAt = at(10, 0, dayOffset);
      const completedAt = at(11 + (i % 4), 30, dayOffset);
      const closedAt = at(12 + (i % 4), 0, dayOffset);
      // eslint-disable-next-line no-await-in-loop
      await insertWO(`hist_${i}`, code, customerKey, deviceKey, branch, `${issue} (${type})`, 'closed', i % 6 === 0 ? 'urgent' : 'normal',
        techKey, createdAt, createdAt, startedAt, completedAt, closedAt);
    }

    // Checklist for WO-2418 (screen 5)
    await client.query(
      `INSERT INTO checklist_items (work_order_id, label, status, note, order_index) VALUES
       ($1, 'قياس ضغط الفريون', 'done', '58 psi', 1),
       ($1, 'تنظيف المكثف والمرشحات', 'done', NULL, 2),
       ($1, 'الضاغط لا يعمل — عطل مؤكد', 'issue', NULL, 3),
       ($1, 'اختبار التشغيل النهائي', 'pending', NULL, 4)`,
      [workOrders.wo2418]
    );

    // Checklist for closed fridge WO
    await client.query(
      `INSERT INTO checklist_items (work_order_id, label, status, note, order_index) VALUES
       ($1, 'فحص الثرموستات', 'done', NULL, 1),
       ($1, 'استبدال الثرموستات', 'done', NULL, 2),
       ($1, 'اختبار التبريد', 'done', NULL, 3)`,
      [workOrders.wo_sara]
    );

    const inventory = {};
    const insertItem = async (key, name, sku, category, price, quantity, threshold) => {
      const { rows } = await client.query(
        `INSERT INTO inventory_items (name, sku, category, price, quantity, reorder_threshold)
         VALUES ($1,$2,$3,$4,$5,$6) RETURNING id`,
        [name, sku, category, price, quantity, threshold]
      );
      inventory[key] = rows[0].id;
    };

    await insertItem('compressor', 'ضاغط روتاري 18000 BTU', 'CMP-18R', 'مكيفات', 8900, 1, 4);
    await insertItem('heating_element', 'عنصر تسخين سخان ٢٠٠٠ وات', 'HTR-20', 'سخانات', 640, 3, 6);
    await insertItem('capacitor', 'مكثف تشغيل 35µF', 'CAP-35', 'مكيفات', 320, 18, 10);
    await insertItem('drain_pump', 'مضخة تصريف غسالة', 'PMP-12', 'غسالات', 480, 14, 8);
    await insertItem('thermostat', 'ثرموستات ثلاجة', 'THM-07', 'ثلاجات', 260, 20, 8);
    await insertItem('gas_valve', 'صمام غاز بوتاجاز', 'GVL-03', 'بوتاجاز', 210, 12, 6);

    // Parts used on WO-2418: capacitor used, compressor requested/unavailable
    await client.query(
      `INSERT INTO parts_used (work_order_id, inventory_item_id, name, price, status) VALUES
       ($1, $2, 'مكثف تشغيل 35µF', 320, 'used'),
       ($1, $3, 'ضاغط روتاري 18000', 8900, 'requested')`,
      [workOrders.wo2418, inventory.capacitor, inventory.compressor]
    );

    await client.query(
      `UPDATE inventory_items SET quantity = quantity - 1 WHERE id = $1`,
      [inventory.capacitor]
    );

    await client.query(
      `INSERT INTO purchase_orders (inventory_item_id, work_order_id, quantity, status)
       VALUES ($1, $2, 1, 'pending')`,
      [inventory.compressor, workOrders.wo2418]
    );

    // Invoice for closed fridge WO (screen 6 example, rated)
    await client.query(
      `INSERT INTO invoices (work_order_id, labor_fee, warranty_discount, tax_rate, payment_method, signature_name, signed_at, customer_rating)
       VALUES ($1, 300, 0, 0.14, 'cash', 'سارة يوسف', $2, 5)`,
      [workOrders.wo_sara, at(9, 40)]
    );

    // Invoices for historical closed work orders (for manager revenue/CSAT aggregates)
    const histRows = await client.query(
      `SELECT id FROM work_orders WHERE code LIKE 'WO-21%' ORDER BY id`
    );
    let idx = 0;
    for (const row of histRows.rows) {
      const labor = 300 + (idx % 5) * 50;
      const rating = 3 + (idx % 3);
      // eslint-disable-next-line no-await-in-loop
      await client.query(
        `INSERT INTO invoices (work_order_id, labor_fee, warranty_discount, tax_rate, payment_method, signature_name, signed_at, customer_rating)
         VALUES ($1, $2, 0, 0.14, 'cash', 'العميل', now(), $3)`,
        [row.id, labor, rating]
      );
      idx += 1;
    }

    await client.query('COMMIT');
    console.log('Seed complete.');
    console.log(`Login with any account below, password: ${DEFAULT_PASSWORD}`);
    console.log('  reception: mona@markazsayana.eg');
    console.log('  manager:   khaled@markazsayana.eg');
    console.log('  technician:mahmoud@markazsayana.eg');
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

module.exports = { seed, DEFAULT_PASSWORD };

// Only auto-run (and close the shared pool afterward) when invoked directly as a CLI
// script (`npm run seed`) — not when required as a module by the running server, which
// needs to keep the pool open for its own requests.
if (require.main === module) {
  seed()
    .then(() => pool.end())
    .catch((err) => {
      console.error(err);
      process.exit(1);
    });
}
