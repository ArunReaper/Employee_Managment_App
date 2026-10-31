import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import RequireAdmin from './components/RequireAdmin';
import HomePage from './pages/HomePage';
import SignUpPage from './pages/SignUpPage';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import EmployeeListPage from './pages/EmployeeListPage';
import AddEmployeePage from './pages/AddEmployeePage';
import EditEmployeePage from './pages/EditEmployeePage';
import DeleteEmployeePage from './pages/DeleteEmployeePage';

// One route per legacy JSP. The admin pages need a login (SEC-001, security hardening of 2026-10-31;
// replaces RULE-018), as the hardened legacy AuthFilter does.
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/signup" element={<SignUpPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAdmin />}>
        <Route element={<Layout />}>
          <Route path="/welcome" element={<DashboardPage />} />
          <Route path="/employees" element={<EmployeeListPage />} />
          <Route path="/employees/add" element={<AddEmployeePage />} />
          <Route path="/employees/edit" element={<EditEmployeePage />} />
          <Route path="/employees/delete" element={<DeleteEmployeePage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
