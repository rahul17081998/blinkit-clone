import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';

// Requires any authenticated user
export function ProtectedRoute({ children }) {
  const { isLoggedIn, isInitialized } = useAuthStore();
  const location = useLocation();

  if (!isInitialized) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-10 w-10 border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!isLoggedIn) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
}

// Requires ADMIN role
export function AdminRoute({ children }) {
  const { isLoggedIn, role, isInitialized } = useAuthStore();
  const location = useLocation();

  if (!isInitialized) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-10 w-10 border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!isLoggedIn) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }

  return children;
}

// Requires DELIVERY_AGENT role
export function AgentRoute({ children }) {
  const { isLoggedIn, role, isInitialized } = useAuthStore();
  const location = useLocation();

  if (!isInitialized) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-10 w-10 border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!isLoggedIn) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (role !== 'DELIVERY_AGENT') {
    return <Navigate to="/" replace />;
  }

  return children;
}

// Redirects logged-in users away from auth pages
export function GuestRoute({ children }) {
  const { isLoggedIn, role, isInitialized } = useAuthStore();

  if (!isInitialized) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-10 w-10 border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (isLoggedIn) {
    if (role === 'ADMIN') return <Navigate to="/admin" replace />;
    if (role === 'DELIVERY_AGENT') return <Navigate to="/agent" replace />;
    return <Navigate to="/" replace />;
  }

  return children;
}
