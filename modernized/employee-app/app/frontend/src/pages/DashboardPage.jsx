import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api';

const actions = [
  { to: '/employees/add', title: 'Add Employee', text: 'Record a new employee', tone: 'green', icon: '+' },
  { to: '/employees/delete', title: 'Remove Employee', text: 'Delete by ID and name', tone: 'red', icon: '−' },
  { to: '/employees', title: 'Show List of Employees', text: 'Browse every record', tone: 'blue', icon: '≡' },
  { to: '/employees/edit', title: 'Update Employee Details', text: 'Change salary or department', tone: 'amber', icon: '✎' },
];

// Replaces Welcome.jsp. Only reachable when logged in (RequireAdmin, SEC-001).
export default function DashboardPage() {
  const [adminName, setAdminName] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    api.currentAdmin().then((me) => setAdminName(me.adminName)).catch(() => setAdminName(null));
  }, []);

  // SEC-020 (replaces RULE-015): Logout ends the session on the server, then returns to the home page.
  // A failed call still leaves the page: the session then simply expires.
  const logout = async () => {
    await api.logOut().catch(() => {});
    navigate('/');
  };

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <div>
          <h1>{adminName ? `Welcome, ${adminName}!` : 'Welcome!'}</h1>
          <p className="muted">Choose an action below:</p>
        </div>
        <button type="button" className="btn btn-ghost" onClick={logout}>
          Logout
        </button>
      </header>
      <div className="action-grid">
        {actions.map((a) => (
          <Link key={a.to} to={a.to} className={`action action-${a.tone}`}>
            <span className="action-icon" aria-hidden="true">{a.icon}</span>
            <span className="action-title">{a.title}</span>
            <span className="action-text">{a.text}</span>
          </Link>
        ))}
      </div>
    </div>
  );
}
