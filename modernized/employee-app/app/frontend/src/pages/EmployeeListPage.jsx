import { useEffect, useState } from 'react';
import { api } from '../api';
import { PageCard, BackHome } from '../components/Layout';
import { Alert } from '../components/Notices';

// Replaces showEmployee.jsp. Cells show the server's text as-is: salaryText is Java's
// Double.toString (RULE-002) and a NULL column shows "null", as the JSP printed it. React
// escapes everything, so a name containing HTML is shown as text (brief Q4).
const cell = (value) => String(value);

export default function EmployeeListPage() {
  const [employees, setEmployees] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.listEmployees().then(setEmployees).catch((e) => setError(e.message));
  }, []);

  return (
    <PageCard title="Employee List" subtitle={employees ? `${employees.length} employee(s)` : undefined} wide>
      <Alert message={error} />
      {!error && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Name</th>
                <th>Department</th>
                <th className="num">Salary</th>
              </tr>
            </thead>
            <tbody>
              {employees === null && (
                <tr>
                  <td colSpan="4" className="empty">Loading…</td>
                </tr>
              )}
              {employees?.length === 0 && (
                <tr>
                  <td colSpan="4" className="empty">No employees yet.</td>
                </tr>
              )}
              {employees?.map((e) => (
                <tr key={e.id}>
                  <td className="mono">{cell(e.id)}</td>
                  <td>{cell(e.name)}</td>
                  <td>
                    <span className="pill">{cell(e.department)}</span>
                  </td>
                  <td className="num mono">{e.salaryText}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <BackHome />
    </PageCard>
  );
}
