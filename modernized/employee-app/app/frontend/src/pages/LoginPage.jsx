import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api';
import { PageCard, Field } from '../components/Layout';
import { Alert } from '../components/Notices';
import { useSubmit } from '../components/useSubmit';

// Replaces Login.jsp: ID + name, no password (RULE-017, confirmed); the name is sent exactly as typed.
// Too many failed attempts show the server's lockout message (SEC-009).
export default function LoginPage() {
  const [form, setForm] = useState({ id: '', name: '' });
  const navigate = useNavigate();
  const { busy, error, submit } = useSubmit(async () => {
    await api.logIn(form);
    navigate('/welcome');
  });
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <div className="auth">
      <PageCard title="Admin Login" subtitle="Sign in with your admin ID and name">
        <form onSubmit={submit} className="form">
          <Field label="Admin ID" id="id">
            <input id="id" type="number" min="1" step="1" required value={form.id} onChange={set('id')} />
          </Field>
          <Field label="Admin Name" id="name">
            <input id="name" type="text" required value={form.name} onChange={set('name')} />
          </Field>
          <Alert message={error} />
          <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Logging in…' : 'Login'}
          </button>
        </form>
        <p className="muted center">
          New here? <Link to="/signup">Sign Up</Link>
        </p>
      </PageCard>
    </div>
  );
}
