require('dotenv').config();
const fs = require('fs');
const path = require('path');
const { pool } = require('../src/db');

async function migrate() {
  const schema = fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');
  await pool.query(schema);
  console.log('Schema applied.');
  await pool.end();
}

migrate().catch((err) => {
  console.error(err);
  process.exit(1);
});
