const { Pool } = require('pg');

// Managed Postgres providers (Neon, Render, etc.) require TLS and often present a
// certificate chain that Node's default trust store can't fully verify — this is the
// standard, widely-documented way to connect to them from `pg` without failing on that.
const needsSsl = /\bsslmode=require\b/.test(process.env.DATABASE_URL || '') || process.env.NODE_ENV === 'production';

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: needsSsl ? { rejectUnauthorized: false } : undefined,
});

// node-postgres emits 'error' on the pool for background failures on already-idle
// clients (e.g. the DB restarting, a network blip) — these aren't tied to any
// in-flight request. Without a listener here, Node treats it as an uncaught
// exception and kills the entire process; the query that's actually in flight when
// this happens will still reject normally and be handled per-request as usual.
pool.on('error', (err) => {
  console.error('Unexpected error on idle database client', err);
});

module.exports = { pool };
