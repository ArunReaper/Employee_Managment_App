import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { api } from '../api';

// Shows the admin pages only to a logged-in admin; anyone else goes to the login page (SEC-001).
// The server enforces the same rule on every /api/employees call; this only spares a visitor a
// page of "Please log in" errors.
export default function RequireAdmin() {
  const [state, setState] = useState('checking');
  const location = useLocation();

  useEffect(() => {
    let active = true;
    api
      .currentAdmin()
      .then((me) => active && setState(me.adminName ? 'admin' : 'anonymous'))
      .catch(() => active && setState('anonymous'));
    return () => {
      active = false;
    };
  }, [location.pathname]);

  if (state === 'checking') {
    return <p className="muted center">Checking your session…</p>;
  }
  return state === 'admin' ? <Outlet /> : <Navigate to="/login" replace />;
}
