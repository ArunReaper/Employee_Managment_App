import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { PageCard, BackHome, Field } from '../components/Layout';
import { Alert, useToast } from '../components/Notices';
import { useSubmit } from '../components/useSubmit';

// Replaces deleteEmployee.jsp + DeleteEmployeeServlet: ID and exact name must both match
// (RULE-013). A miss is now reported on the page (replaces RULE-014, approved 2026-10-31).
export default function DeleteEmployeePage() {
  const [form, setForm] = useState({ id: '', name: '' });
  const navigate = useNavigate();
  const notify = useToast();
  const { busy, error, submit } = useSubmit(async () => {
    const { message } = await api.deleteEmployee(form.id, form.name);
    notify(message);
    navigate('/welcome');
  });
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <PageCard title="Delete Employee" subtitle="Both the ID and the exact name must match">
      <form onSubmit={submit} className="form">
        <Field label="Employee ID" id="id">
          <input id="id" type="text" required value={form.id} onChange={set('id')} />
        </Field>
        <Field label="Employee Name" id="name">
          <input id="name" type="text" required value={form.name} onChange={set('name')} />
        </Field>
        <Alert message={error} />
        <button type="submit" className="btn btn-danger" disabled={busy}>
          {busy ? 'Deleting…' : 'Delete Employee'}
        </button>
      </form>
      <BackHome />
    </PageCard>
  );
}
