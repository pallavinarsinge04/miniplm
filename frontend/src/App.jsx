import { Navigate, NavLink, Outlet, Route, Routes, useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext.js';
import Login from './pages/Login.jsx';
import Parts from './pages/Parts.jsx';
import PartDetail from './pages/PartDetail.jsx';
import Inbox from './pages/Inbox.jsx';
import Users from './pages/Users.jsx';
import Audit from './pages/Audit.jsx';

function RequireAuth({ children }) {
  const { user } = useAuth();
  return user ? children : <Navigate to="/login" replace />;
}

function RequireRole({ roles, children }) {
  const { user } = useAuth();
  if (!roles.includes(user.role)) {
    return <p className="error">You do not have access to this page.</p>;
  }
  return children;
}

function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div>
      <header className="topbar">
        <span className="brand">Mini PLM</span>
        <nav>
          <NavLink to="/parts">Parts</NavLink>
          {user.role === 'REVIEWER' && <NavLink to="/inbox">My approvals</NavLink>}
          {user.role === 'ADMIN' && <NavLink to="/users">Users</NavLink>}
          {user.role === 'ADMIN' && <NavLink to="/audit">Audit log</NavLink>}
        </nav>
        <div className="who">
          <span>{user.name}</span>
          <span className="role">{user.role}</span>
          <button className="btn" onClick={handleLogout}>Log out</button>
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route path="/" element={<Navigate to="/parts" replace />} />
        <Route path="/parts" element={<Parts />} />
        <Route path="/parts/:id" element={<PartDetail />} />
        <Route path="/inbox" element={<RequireRole roles={['REVIEWER']}><Inbox /></RequireRole>} />
        <Route path="/users" element={<RequireRole roles={['ADMIN']}><Users /></RequireRole>} />
        <Route path="/audit" element={<RequireRole roles={['ADMIN']}><Audit /></RequireRole>} />
      </Route>
      <Route path="*" element={<Navigate to="/parts" replace />} />
    </Routes>
  );
}
