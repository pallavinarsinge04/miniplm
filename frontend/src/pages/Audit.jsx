import { useEffect, useState } from 'react';
import { api, formatDate } from '../api.js';

export default function Audit() {
  const [entries, setEntries] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    api.audit().then(setEntries).catch((e) => setError(e.message));
  }, []);

  return (
    <div>
      <h2>Audit log</h2>
      <p className="muted">The latest 100 actions, newest first.</p>
      {error && <p className="error">{error}</p>}
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr><th>When</th><th>Action</th><th>By</th><th>Item</th><th>Details</th></tr>
          </thead>
          <tbody>
            {entries.map((a) => (
              <tr key={a.id}>
                <td>{formatDate(a.performedAt)}</td>
                <td>{a.action}</td>
                <td>{a.performedBy}</td>
                <td>{a.entityType} #{a.entityId}</td>
                <td>{a.details}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
