import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { PageCard, BackHome, Field } from '../components/Layout';
import { Alert, useToast } from '../components/Notices';
import { useSubmit } from '../components/useSubmit';

// Replaces addEmployee.jsp + AddEmployeeServlet. The browser checks (id >= 1, salary >= 0, cents)
// are kept as in the JSP (RULE-010); the server re-checks. Success goes to the dashboard.
export default function AddEmployeePage() {
  const [form, setForm] = useState({ id: '', name: '', department: '', salary: '' });
  const navigate = useNavigate();
  const notify = useToast();
  const { busy, error, submit } = useSubmit(async () => {
    const { message } = await api.addEmployee(form);
    notify(message);
    navigate('/welcome');
  });
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <PageCard title="Add Employee" subtitle="Record a new employee">
      <form onSubmit={submit} className="form form-grid">
        <Field label="Employee ID" id="id">
          <input id="id" type="number" min="1" step="1" required value={form.id} onChange={set('id')} />
        </Field>
        <Field label="Employee Name" id="name">
          <input id="name" type="text" required value={form.name} onChange={set('name')} />
        </Field>
        <Field label="Department" id="department">
          <input id="department" type="text" required value={form.department} onChange={set('department')} />
        </Field>
        <Field label="Salary" id="salary">
          <input id="salary" type="number" min="0" step="0.01" required value={form.salary} onChange={set('salary')} />
        </Field>
        <div className="span-2">
          <Alert message={error} />
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Adding…' : 'Add Employee'}
          </button>
        </div>
      </form>
      <BackHome />
    </PageCard>
  );
}
