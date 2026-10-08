import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, downloadBomExcel, formatDate } from '../api.js';
import { useAuth } from '../AuthContext.js';
import StateBadge from '../components/StateBadge.jsx';

function flatten(node, out = []) {
  out.push(node);
  (node.children || []).forEach((child) => flatten(child, out));
  return out;
}

export default function PartDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const canEdit = user.role === 'DESIGNER' || user.role === 'ADMIN';

  const [part, setPart] = useState(null);
  const [versions, setVersions] = useState([]);
  const [bom, setBom] = useState(null);
  const [whereUsed, setWhereUsed] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  const [reviewers, setReviewers] = useState('');
  const [childNumber, setChildNumber] = useState('');
  const [quantity, setQuantity] = useState(1);

  const load = useCallback(async () => {
    try {
      const [p, v, b, w] = await Promise.all([
        api.part(id),
        api.versions(id),
        api.bom(id),
        api.whereUsed(id),
      ]);
      setPart(p);
      setVersions(v);
      setBom(b);
      setWhereUsed(w);
      setTasks(v.length > 0 ? await api.tasksForVersion(id, v[v.length - 1].id) : []);
      setError('');
    } catch (e) {
      setError(e.message);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  // Runs an action, shows a message, then reloads the page data
  async function run(action, message) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
      if (message) setNotice(message);
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  if (!part) {
    return error ? <p className="error">{error}</p> : <p className="muted">Loading...</p>;
  }

  const latest = versions.length > 0 ? versions[versions.length - 1] : null;
  const bomEditable = canEdit && latest && latest.state === 'IN_WORK';
  const bomRows = bom ? flatten(bom) : [];

  function handleSubmitForReview(event) {
    event.preventDefault();
    const emails = reviewers.split(/[,\s;]+/).map((s) => s.trim()).filter(Boolean);
    if (emails.length === 0) {
      setError('Enter at least one reviewer email.');
      return;
    }
    run(() => api.submit(id, latest.id, emails), 'Sent for review.');
  }

  function handleAddBom(event) {
    event.preventDefault();
    run(async () => {
      await api.addBom(id, { childPartNumber: childNumber.trim(), quantity: Number(quantity), unit: 'EA' });
      setChildNumber('');
      setQuantity(1);
    }, 'Added to the BOM.');
  }

  return (
    <div>
      <p><Link to="/parts">&larr; All parts</Link></p>

      <div className="row between">
        <div>
          <h2>{part.partNumber}, {part.name}</h2>
          <p className="muted">
            {part.type} {part.description ? `· ${part.description}` : ''}
          </p>
        </div>
        <div className="row">
          {canEdit && latest && latest.state === 'RELEASED' && (
            <button className="btn btn-primary" disabled={busy}
              onClick={() => run(() => api.revise(id), 'New revision created.')}>
              Revise
            </button>
          )}
          <button className="btn" disabled={busy}
            onClick={() => run(() => downloadBomExcel(id, part.partNumber))}>
            Export BOM (Excel)
          </button>
        </div>
      </div>

      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}

      <h3>Revisions</h3>
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr><th>Revision</th><th>State</th><th>Created</th><th></th></tr>
          </thead>
          <tbody>
            {versions.map((v) => (
              <tr key={v.id}>
                <td>{v.revision}{latest && v.id === latest.id ? ' (latest)' : ''}</td>
                <td><StateBadge state={v.state} /></td>
                <td>{formatDate(v.createdAt)}</td>
                <td>
                  {canEdit && latest && v.id === latest.id && v.state === 'APPROVED' && (
                    <button className="btn btn-primary" disabled={busy}
                      onClick={() => run(() => api.release(id, v.id), 'Revision released.')}>
                      Release
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {canEdit && latest && latest.state === 'IN_WORK' && (
        <form className="card" onSubmit={handleSubmitForReview}>
          <h4>Send revision {latest.revision} for review</h4>
          <div className="row">
            <input
              value={reviewers}
              onChange={(e) => setReviewers(e.target.value)}
              placeholder="Reviewer emails, separated by commas (e.g. asha@plm.com, ravi@plm.com)"
            />
            <button className="btn btn-primary" disabled={busy}>Submit for review</button>
          </div>
        </form>
      )}

      {tasks.length > 0 && (
        <div>
          <h3>Approvals for revision {latest.revision}</h3>
          <div className="card table-wrap">
            <table className="table">
              <thead>
                <tr><th>Reviewer</th><th>Status</th><th>Comment</th><th>Decided</th></tr>
              </thead>
              <tbody>
                {tasks.map((t) => (
                  <tr key={t.taskId}>
                    <td>{t.approverEmail}</td>
                    <td><StateBadge state={t.status} /></td>
                    <td>{t.comment}</td>
                    <td>{formatDate(t.decidedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <h3>Bill of materials{latest ? `, revision ${latest.revision}` : ''}</h3>
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr><th>Part</th><th>Name</th><th>Rev</th><th>State</th><th>Qty</th><th>Unit</th><th></th></tr>
          </thead>
          <tbody>
            {bomRows.map((row) => (
              <tr key={`${row.level}-${row.linkId ?? 'root'}-${row.partId}`}>
                <td style={{ paddingLeft: 12 + row.level * 22 }}>
                  {row.level > 0 ? '└ ' : ''}
                  <Link to={`/parts/${row.partId}`}>{row.partNumber}</Link>
                </td>
                <td>{row.name}</td>
                <td>{row.revision}</td>
                <td><StateBadge state={row.state} /></td>
                <td>{row.level > 0 ? row.quantity : ''}</td>
                <td>{row.level > 0 ? row.unit : ''}</td>
                <td>
                  {bomEditable && row.level === 1 && (
                    <button className="btn btn-danger" disabled={busy}
                      onClick={() => run(() => api.removeBom(id, row.linkId), 'Removed from the BOM.')}>
                      Remove
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {bomRows.length <= 1 && (
              <tr><td colSpan="7" className="muted">This part has no child parts yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {bomEditable && (
        <form className="card" onSubmit={handleAddBom}>
          <h4>Add a child part</h4>
          <div className="row">
            <input
              value={childNumber}
              onChange={(e) => setChildNumber(e.target.value)}
              placeholder="Child part number, e.g. P-2001"
              required
            />
            <input
              type="number"
              min="1"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              className="narrow"
              required
            />
            <button className="btn btn-primary" disabled={busy}>Add</button>
          </div>
        </form>
      )}

      <h3>Where used</h3>
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr><th>Assembly</th><th>Name</th><th>Rev</th><th>State</th><th>Qty</th></tr>
          </thead>
          <tbody>
            {whereUsed.map((w, index) => (
              <tr key={`${w.partId}-${w.revision}-${index}`}>
                <td><Link to={`/parts/${w.partId}`}>{w.partNumber}</Link></td>
                <td>{w.name}</td>
                <td>{w.revision}</td>
                <td><StateBadge state={w.state} /></td>
                <td>{w.quantity}</td>
              </tr>
            ))}
            {whereUsed.length === 0 && (
              <tr><td colSpan="5" className="muted">Not used in any assembly.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
