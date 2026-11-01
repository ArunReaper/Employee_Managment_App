import { NavLink, Link, Outlet } from 'react-router-dom';

const links = [
  { to: '/welcome', label: 'Dashboard' },
  { to: '/employees', label: 'Employees', end: true },
  { to: '/employees/add', label: 'Add' },
  { to: '/employees/edit', label: 'Update' },
  { to: '/employees/delete', label: 'Remove' },
];

export default function Layout() {
  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="brand-mark" aria-hidden="true">E</span>
          Employee Manager
        </Link>
        <nav className="nav" aria-label="Main">
          {links.map((l) => (
            <NavLink key={l.to} to={l.to} end={l.end} className="nav-link">
              {l.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}

export function PageCard({ title, subtitle, children, wide = false }) {
  return (
    <section className={`card ${wide ? 'card-wide' : ''}`}>
      <header className="card-header">
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </header>
      {children}
    </section>
  );
}

export function BackHome() {
  return (
    <Link to="/welcome" className="back-link">
      ← Back to Home
    </Link>
  );
}

export function Field({ label, id, children }) {
  return (
    <label className="field" htmlFor={id}>
      <span className="field-label">{label}</span>
      {children}
    </label>
  );
}
