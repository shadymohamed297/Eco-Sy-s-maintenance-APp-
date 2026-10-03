const jwt = require('jsonwebtoken');
const { pool } = require('../db');

async function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) {
    return res.status(401).json({ error: 'Missing authorization token' });
  }
  let payload;
  try {
    payload = jwt.verify(token, process.env.JWT_SECRET);
  } catch (err) {
    return res.status(401).json({ error: 'Invalid or expired token' });
  }

  // A 30-day token can outlive a deactivated employee (fired, phone lost, etc.), so a
  // manager deactivating an account needs to cut off access immediately rather than
  // waiting for the token to expire on its own.
  const { rows } = await pool.query('SELECT active FROM users WHERE id = $1', [payload.id]);
  if (!rows[0] || !rows[0].active) {
    return res.status(401).json({ error: 'Account is deactivated' });
  }

  req.user = payload;
  return next();
}

function requireRole(...roles) {
  return (req, res, next) => {
    if (!req.user || !roles.includes(req.user.role)) {
      return res.status(403).json({ error: 'Insufficient role permissions' });
    }
    return next();
  };
}

module.exports = { requireAuth, requireRole };
