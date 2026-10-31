import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api';
import { PageCard, Field } from '../components/Layout';
import { Alert, useToast } from '../components/Notices';
import { useSubmit } from '../components/useSubmit';

// Replaces signup.jsp + SignUpServlet: success goes to the login page. Once an admin exists, only a
// logged-in admin may sign up another; anyone else sees the server's "Sign-up is closed" message (SEC-007).
export default function SignUpPage() {
  const [form, setForm] = useState({ id: '', name: '' });
  const navigate = useNavigate();
  const notify = useToast();
  const { busy, error, submit } = useSubmit(async () => {
    const { message } = await api.signUp(form);
    notify(message);
    navigate('/login');
  });
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <div className="auth">
      <PageCard title="Admin Sign Up" subtitle="Create your administrator account">
        <form onSubmit={submit} className="form">
          <Field label="Admin ID" id="id">
            <input id="id" type="number" min="1" step="1" required value={form.id} onChange={set('id')} />
          </Field>
          <Field label="Admin Name" id="name">
            <input id="name" type="text" required value={form.name} onChange={set('name')} />
          </Field>
          <Alert message={error} />
          <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Signing up…' : 'Sign Up'}
          </button>
        </form>
        <p className="muted center">
          Already have an account? <Link to="/login">Log In</Link>
        </p>
      </PageCard>
    </div>
  );
}
