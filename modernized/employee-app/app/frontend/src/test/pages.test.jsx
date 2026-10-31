// UI behaviour tests: each page against a mocked API. The API itself is pinned by the backend
// characterization tests; these prove the UI shows the API's text unchanged and goes where the
// legacy JSP flow went.
import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { ToastProvider } from '../components/Notices';
import App from '../App';
import { NETWORK_ERROR } from '../api';

function Where() {
  return <div data-testid="where">{useLocation().pathname}</div>;
}

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ToastProvider>
        <App />
        <Where />
      </ToastProvider>
    </MemoryRouter>,
  );
}

const reply = (status, body) => Promise.resolve({ ok: status < 300, status, json: () => Promise.resolve(body) });

// The admin pages first ask GET /api/auth/me (RequireAdmin, SEC-001). mockApi answers it with a
// logged-in admin by default (pass admin: null for a visitor), so fetchMock records only the page's own calls.
function mockApi(handler, { admin = 'Ravi' } = {}) {
  const fetchMock = vi.fn((url, init = {}) => handler(url, init.method || 'GET', init.body && JSON.parse(init.body)));
  vi.stubGlobal('fetch', (url, init) =>
    url === '/api/auth/me' ? reply(200, { adminName: admin }) : fetchMock(url, init),
  );
  return fetchMock;
}

const where = () => screen.getByTestId('where').textContent;

// An admin page: render, then wait until RequireAdmin has confirmed the login
async function renderAdminPage(path) {
  renderAt(path);
  await waitFor(() => expect(screen.queryByText('Checking your session…')).not.toBeInTheDocument());
}

describe('Employee list (showEmployee.jsp)', () => {
  // RULE-002, RULE-023: rows shown with the server's text; NULL shows "null" as the JSP printed it
  it('shows every row with salaryText verbatim and null columns as "null"', async () => {
    mockApi(() =>
      reply(200, [
        { id: 101, name: 'Asha', department: 'Finance', salary: 15000, salaryText: '15000.0' },
        { id: 102, name: null, department: null, salary: 1e7, salaryText: '1.0E7' },
        { id: 103, name: 'Neg', department: 'Ops', salary: null, salaryText: 'NaN' },
      ]),
    );
    renderAt('/employees');
    await screen.findByText('Asha');
    const rows = screen.getAllByRole('row');
    const cells = rows.slice(1).map((r) => within(r).getAllByRole('cell').map((c) => c.textContent));
    expect(cells).toEqual([
      ['101', 'Asha', 'Finance', '15000.0'],
      ['102', 'null', 'null', '1.0E7'],
      ['103', 'Neg', 'Ops', 'NaN'],
    ]);
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['ID', 'Name', 'Department', 'Salary']);
  });

  // brief Q4 (accepted difference): HTML in a name is shown as text, never executed
  it('shows an HTML-like name as plain text', async () => {
    mockApi(() => reply(200, [{ id: 1, name: '<b>x</b>', department: 'D', salary: 1, salaryText: '1.0' }]));
    renderAt('/employees');
    expect(await screen.findByText('<b>x</b>')).toBeInTheDocument();
  });

  // replaces RULE-025 (deviation approved 2026-10-31): a read failure is reported, not an empty table
  it('shows the database-unavailable message instead of an empty table', async () => {
    mockApi(() => reply(503, { message: "We couldn't reach the database. Please try again later." }));
    renderAt('/employees');
    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't reach the database. Please try again later.");
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('shows a connection message when the server cannot be reached', async () => {
    mockApi(() => Promise.reject(new TypeError('Failed to fetch')));
    renderAt('/employees');
    expect(await screen.findByRole('alert')).toHaveTextContent(NETWORK_ERROR);
  });

  it('shows an empty state for no employees', async () => {
    mockApi(() => reply(200, []));
    renderAt('/employees');
    expect(await screen.findByText('No employees yet.')).toBeInTheDocument();
  });
});

describe('Add employee (addEmployee.jsp)', () => {
  it('posts the form as strings, toasts the message and goes to the dashboard', async () => {
    const fetchMock = mockApi((url) =>
      url === '/api/employees' ? reply(201, { message: 'Employee added successfully!' }) : reply(200, { adminName: null }),
    );
    await renderAdminPage('/employees/add');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Employee ID'), '101');
    await user.type(screen.getByLabelText('Employee Name'), 'Asha');
    await user.type(screen.getByLabelText('Department'), 'Finance');
    await user.type(screen.getByLabelText('Salary'), '15000');
    await user.click(screen.getByRole('button', { name: 'Add Employee' }));
    await waitFor(() => expect(where()).toBe('/welcome'));
    expect(screen.getByRole('status')).toHaveTextContent('Employee added successfully!');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ id: '101', name: 'Asha', department: 'Finance', salary: '15000' });
  });

  // RULE-005: duplicate id message, user stays on the form
  it('shows the server message and stays on the form on error', async () => {
    mockApi(() => reply(409, { message: 'An employee with this ID already exists. Please choose another ID.' }));
    await renderAdminPage('/employees/add');
    const user = userEvent.setup();
    for (const [label, value] of [['Employee ID', '1'], ['Employee Name', 'A'], ['Department', 'D'], ['Salary', '1']]) {
      await user.type(screen.getByLabelText(label), value);
    }
    await user.click(screen.getByRole('button', { name: 'Add Employee' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('An employee with this ID already exists. Please choose another ID.');
    expect(where()).toBe('/employees/add');
  });

  // RULE-010: browser constraints kept from the JSP
  it('keeps the JSP browser constraints on id and salary', async () => {
    mockApi(() => reply(200, {}));
    await renderAdminPage('/employees/add');
    expect(screen.getByLabelText('Employee ID')).toHaveAttribute('min', '1');
    expect(screen.getByLabelText('Salary')).toHaveAttribute('min', '0');
    expect(screen.getByLabelText('Salary')).toHaveAttribute('step', '0.01');
  });
});

describe('Edit employee (editEmployee.jsp)', () => {
  // RULE-016: id in the path, name/field/newValue in the body
  it('sends PATCH /api/employees/{id} and goes to the dashboard on success', async () => {
    const fetchMock = mockApi((url) =>
      url.startsWith('/api/employees/') ? reply(200, { message: 'Employee details updated successfully!' }) : reply(200, { adminName: null }),
    );
    await renderAdminPage('/employees/edit');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Employee ID'), '101');
    await user.type(screen.getByLabelText('Employee Name'), 'Anyone');
    await user.selectOptions(screen.getByLabelText('Select Field to Edit'), 'salary');
    await user.type(screen.getByLabelText('New Value'), '-500');
    await user.click(screen.getByRole('button', { name: 'Update Employee' }));
    await waitFor(() => expect(where()).toBe('/welcome'));
    expect(fetchMock.mock.calls[0][0]).toBe('/api/employees/101');
    expect(fetchMock.mock.calls[0][1].method).toBe('PATCH');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ name: 'Anyone', field: 'salary', newValue: '-500' });
  });

  it('shows "Employee not found!" and stays on the form', async () => {
    mockApi(() => reply(404, { message: 'Employee not found!' }));
    await renderAdminPage('/employees/edit');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Employee ID'), '9');
    await user.type(screen.getByLabelText('Employee Name'), 'X');
    await user.type(screen.getByLabelText('New Value'), 'HR');
    await user.click(screen.getByRole('button', { name: 'Update Employee' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Employee not found!');
    expect(where()).toBe('/employees/edit');
  });
});

describe('Delete employee (deleteEmployee.jsp)', () => {
  // replaces RULE-014 (deviation approved 2026-10-31): a miss is reported and the user stays
  it('shows "Employee not found!" on a miss instead of going to the dashboard', async () => {
    const fetchMock = mockApi(() => reply(404, { message: 'Employee not found!' }));
    await renderAdminPage('/employees/delete');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Employee ID'), '101');
    await user.type(screen.getByLabelText('Employee Name'), 'Asha Rao');
    await user.click(screen.getByRole('button', { name: 'Delete Employee' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Employee not found!');
    expect(where()).toBe('/employees/delete');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/employees/101?name=Asha%20Rao');
    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE');
  });

  it('goes to the dashboard with a toast on success', async () => {
    mockApi((url) => (url.startsWith('/api/employees/') ? reply(200, { message: 'Employee deleted successfully!' }) : reply(200, { adminName: null })));
    await renderAdminPage('/employees/delete');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Employee ID'), '101');
    await user.type(screen.getByLabelText('Employee Name'), 'Asha');
    await user.click(screen.getByRole('button', { name: 'Delete Employee' }));
    await waitFor(() => expect(where()).toBe('/welcome'));
    expect(screen.getByRole('status')).toHaveTextContent('Employee deleted successfully!');
  });
});

describe('Sign up, login and dashboard', () => {
  // RULE-009: success goes to the login page with the legacy message
  it('sign-up success shows the message and goes to login', async () => {
    mockApi(() => reply(201, { message: 'Sign up successful! Please log in.' }));
    renderAt('/signup');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Admin ID'), '7');
    await user.type(screen.getByLabelText('Admin Name'), 'Ravi');
    await user.click(screen.getByRole('button', { name: 'Sign Up' }));
    await waitFor(() => expect(where()).toBe('/login'));
    expect(screen.getByRole('status')).toHaveTextContent('Sign up successful! Please log in.');
  });

  // RULE-017: the name is sent exactly as typed (not trimmed); failure shows the legacy message
  it('login failure shows the legacy message and sends the untrimmed name', async () => {
    const fetchMock = mockApi(() => reply(401, { message: 'Invalid ID or Name. Please try again.' }));
    renderAt('/login');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Admin ID'), '7');
    await user.type(screen.getByLabelText('Admin Name'), ' Ravi');
    await user.click(screen.getByRole('button', { name: 'Login' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid ID or Name. Please try again.');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ id: '7', name: ' Ravi' });
  });

  it('login success goes to the dashboard, which greets the admin by name', async () => {
    mockApi(() => reply(200, { adminName: 'Ravi', message: 'Welcome, Ravi!' }));
    renderAt('/login');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Admin ID'), '7');
    await user.type(screen.getByLabelText('Admin Name'), 'Ravi');
    await user.click(screen.getByRole('button', { name: 'Login' }));
    expect(await screen.findByRole('heading', { name: 'Welcome, Ravi!' })).toBeInTheDocument();
    expect(where()).toBe('/welcome');
  });

  // SEC-020 (replaces RULE-015): Logout ends the session on the server, then goes home
  it('logout ends the session on the server and returns home', async () => {
    const fetchMock = mockApi(() => reply(200, { message: 'You have been logged out.' }));
    renderAt('/welcome');
    await screen.findByRole('heading', { name: 'Welcome, Ravi!' });
    await userEvent.setup().click(screen.getByRole('button', { name: 'Logout' }));
    await waitFor(() => expect(where()).toBe('/'));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/logout');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
  });

  // SEC-001 (replaces RULE-018): a visitor who has not logged in is sent to the login page
  it.each(['/welcome', '/employees', '/employees/add', '/employees/edit', '/employees/delete'])(
    'a visitor opening %s is sent to the login page',
    async (path) => {
      const fetchMock = mockApi(() => reply(200, []), { admin: null });
      renderAt(path);
      await waitFor(() => expect(where()).toBe('/login'));
      expect(screen.getByRole('heading', { name: 'Admin Login' })).toBeInTheDocument();
      expect(fetchMock).not.toHaveBeenCalled(); // no employee data was requested
    },
  );

  // SEC-009: the lockout message from the server is shown as-is
  it('login lockout shows the server message', async () => {
    mockApi(() => reply(429, { message: 'Too many failed attempts. Please try again in 15 minutes.' }));
    renderAt('/login');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Admin ID'), '7');
    await user.type(screen.getByLabelText('Admin Name'), 'Ravi');
    await user.click(screen.getByRole('button', { name: 'Login' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Too many failed attempts. Please try again in 15 minutes.');
    expect(where()).toBe('/login');
  });

  // SEC-007: once an admin exists, anonymous sign-up shows the server's refusal and stays on the form
  it('closed sign-up shows the server message', async () => {
    mockApi(() => reply(403, { message: 'Sign-up is closed. Ask an existing admin to create your account.' }));
    renderAt('/signup');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Admin ID'), '8');
    await user.type(screen.getByLabelText('Admin Name'), 'Mallory');
    await user.click(screen.getByRole('button', { name: 'Sign Up' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Sign-up is closed. Ask an existing admin to create your account.');
    expect(where()).toBe('/signup');
  });
});
