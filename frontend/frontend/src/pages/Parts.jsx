import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../AuthContext.js';
import StateBadge from '../components/StateBadge.jsx';

export default function Parts() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const canEdit = user.role === 'DESIGNER' || user.role === 'ADMIN';

  const [parts, setParts] = useState([]);
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const [form, setForm] = useState({ partNumber: '', name: '', description: '', type: 'COMPONENT' });
  const [showForm, setShowForm] = useState(false);

  const load = useCallback(async (q) => {
    setLoading(true);
    try {
      setParts(await api.parts(q));
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load('');
  }, [load]);

  function handleSearch(event) {
    event.preventDefault();
    load(query.trim());
  }

  async function handleCreate(event) {
    event.preventDefault();
    try {
      const created = await api.createPart(form);
      navigate(`/parts/${created.id}`);
    } catch (e) {
      setError(e.message);
    }
  }

  function field(name) {
    return { value: form[name], onChange: (e) => setForm({ ...form, [name]: e.target.value }) };
  }

  return (
    <div>
      <div className="row between">
        <h2>Parts</h2>
        {canEdit && (
          <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
            {showForm ? 'Close' : '+ New part'}
          </button>
        )}
      </div>

      {error && <p className="error">{error}</p>}

      {showForm && (
        <form className="card grid" onSubmit={handleCreate}>
          <div>
            <label>Part number</label>
            <input {...field('partNumber')} required placeholder="P-1001" />
          </div>
          <div>
            <label>Name</label>
            <input {...field('name')} required placeholder="Steel Bracket" />
          </div>
          <div>
            <label>Type</label>
            <select {...field('type')}>
              <option value="COMPONENT">Component</option>
              <option value="ASSEMBLY">Assembly</option>
            </select>
          </div>
          <div>
            <label>Description</label>
            <input {...field('description')} placeholder="Optional" />
          </div>
          <div className="align-end">
            <button className="btn btn-primary">Create part</button>
          </div>
        </form>
      )}

      <form className="row" onSubmit={handleSearch}>
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by part number or name"
        />
        <button className="btn">Search</button>
      </form>

      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Part number</th>
              <th>Name</th>
              <th>Type</th>
              <th>Revision</th>
              <th>State</th>
            </tr>
          </thead>
          <tbody>
            {parts.map((p) => (
              <tr key={p.id}>
                <td><Link to={`/parts/${p.id}`}>{p.partNumber}</Link></td>
                <td>{p.name}</td>
                <td>{p.type}</td>
                <td>{p.latestRevision}</td>
                <td><StateBadge state={p.latestState} /></td>
              </tr>
            ))}
            {!loading && parts.length === 0 && (
              <tr><td colSpan="5" className="muted">No parts found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
