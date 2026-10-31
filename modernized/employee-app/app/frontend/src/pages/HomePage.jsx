import { Link } from 'react-router-dom';

// Replaces index.jsp
export default function HomePage() {
  return (
    <div className="hero">
      <div className="hero-card">
        <span className="hero-badge">Employee Manager</span>
        <h1>Welcome to Our Website</h1>
        <p className="muted">Join us or log in to continue</p>
        <div className="hero-actions">
          <Link to="/signup" className="btn btn-primary">Sign Up</Link>
          <Link to="/login" className="btn btn-ghost">Login</Link>
        </div>
      </div>
    </div>
  );
}
