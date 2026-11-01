import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { PageCard, BackHome, Field } from '../components/Layout';
import { Alert, useToast } from '../components/Notices';
import { useSubmit } from '../components/useSubmit';

// Replaces editEmployee.jsp + EditEmployeeServlet. The name is required, as on the JSP, but the
// server locates the employee by ID only (RULE-016, confirmed).
export default function EditEmployeePage() {
  const [form, setForm] = useState({ id: '', name: '', field: 'department', newValue: '' });
  const navigate = useNavigate();
  const notify = useToast();
  const { busy, error, submit } = useSubmit(async () => {
    const { message } = await api.editEmployee(form.id, { name: form.name, field: form.field, newValue: form.newValue });
    notify(message);
    navigate('/welcome');
  });
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <PageCard title="Edit Employee Details" subtitle="Change an employee's department or salary">
      <form onSubmit={submit} className="form form-grid">
        <Field label="Employee ID" id="id">
          <input id="id" type="text" required value={form.id} onChange={set('id')} />
        </Field>
        <Field label="Employee Name" id="name">
          <input id="name" type="text" required value={form.name} onChange={set('name')} />
        </Field>
        <Field label="Select Field to Edit" id="field">
          <select id="field" value={form.field} onChange={set('field')}>
            <option value="department">Department</option>
            <option value="salary">Salary</option>
          </select>
        </Field>
        <Field label="New Value" id="newValue">
          <input id="newValue" type="text" required value={form.newValue} onChange={set('newValue')} />
        </Field>
        <div className="span-2">
          <Alert message={error} />
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Updating…' : 'Update Employee'}
          </button>
        </div>
      </form>
      <BackHome />
    </PageCard>
  );
}
