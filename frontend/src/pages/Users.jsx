import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';

export default function Users() {
  const [users, setUsers] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [form, setForm] = useState({ name: '', email: '', password: '', role: 'DESIGNER' });

  const load = useCallback(async () => {
    try {
      setUsers(await api.users());
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleCreate(event) {
    event.preventDefault();
    setError('');
    setNotice('');
    try {
      const created = await api.createUser(form);
      setNotice(`User ${created.email} created.`);
      setForm({ name: '', email: '', password: '', role: 'DESIGNER' });
      await load();
    } catch (e) {
      setError(e.message);
    }
  }

  function field(name) {
    return { value: form[name], onChange: (e) => setForm({ ...form, [name]: e.target.value }) };
  }

  return (
    <div>
      <h2>Users</h2>
      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}

      <form className="card grid" onSubmit={handleCreate}>
        <div>
          <label>Name</label>
          <input {...field('name')} required />
        </div>
        <div>
          <label>Email</label>
          <input type="email" {...field('email')} required />
        </div>
        <div>
          <label>Password (6+ characters)</label>
          <input type="password" {...field('password')} minLength={6} required />
        </div>
        <div>
          <label>Role</label>
          <select {...field('role')}>
            <option value="DESIGNER">Designer</option>
            <option value="REVIEWER">Reviewer</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>
        <div className="align-end">
          <button className="btn btn-primary">Create user</button>
        </div>
      </form>

      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr><th>Name</th><th>Email</th><th>Role</th></tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.name}</td>
                <td>{u.email}</td>
                <td>{u.role}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
