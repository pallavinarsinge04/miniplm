import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';

export default function Inbox() {
  const [tasks, setTasks] = useState([]);
  const [comments, setComments] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      setTasks(await api.inbox());
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function decide(task, decision) {
    const comment = (comments[task.taskId] || '').trim();
    if (decision === 'REJECTED' && !comment) {
      setError('Please write a comment explaining the rejection.');
      return;
    }
    try {
      await api.decide(task.taskId, decision, comment);
      setNotice(`${task.partNumber} revision ${task.revision}: ${decision.toLowerCase()}.`);
      await load();
    } catch (e) {
      setError(e.message);
    }
  }

  return (
    <div>
      <h2>My approvals</h2>
      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}

      {!loading && tasks.length === 0 && <p className="muted">Nothing is waiting for your review.</p>}

      {tasks.map((task) => (
        <div className="card" key={task.taskId}>
          <div className="row between">
            <div>
              <strong>
                <Link to={`/parts/${task.partId}`}>{task.partNumber}</Link>
              </strong>
              {' '}{task.partName}, revision {task.revision}
            </div>
          </div>
          <div className="row">
            <input
              placeholder="Comment (required to reject)"
              value={comments[task.taskId] || ''}
              onChange={(e) => setComments({ ...comments, [task.taskId]: e.target.value })}
            />
            <button className="btn btn-primary" onClick={() => decide(task, 'APPROVED')}>Approve</button>
            <button className="btn btn-danger" onClick={() => decide(task, 'REJECTED')}>Reject</button>
          </div>
        </div>
      ))}
    </div>
  );
}
