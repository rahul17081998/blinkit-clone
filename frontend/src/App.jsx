import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { useSessionInit } from './hooks/useSessionInit';
import {
  ProtectedRoute,
  AdminRoute,
  AgentRoute,
  GuestRoute,
} from './routes/ProtectedRoute';

// Auth pages
import LoginPage from './pages/auth/LoginPage';
import SignupPage from './pages/auth/SignupPage';
import VerifyOtpPage from './pages/auth/VerifyOtpPage';
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage';
import ResetPasswordPage from './pages/auth/ResetPasswordPage';

// Customer pages
import HomePage from './pages/customer/HomePage';
import CategoryPage from './pages/customer/CategoryPage';
import SearchPage from './pages/customer/SearchPage';
import ProductDetailPage from './pages/customer/ProductDetailPage';
import CartPage from './pages/customer/CartPage';
import CheckoutPage from './pages/customer/CheckoutPage';
import OrdersPage from './pages/customer/OrdersPage';
import OrderDetailPage from './pages/customer/OrderDetailPage';

// Admin pages (stub)
import AdminDashboardPage from './pages/admin/AdminDashboardPage';

// Agent pages (stub)
import AgentDashboardPage from './pages/agent/AgentDashboardPage';

function AppRoutes() {
  useSessionInit(); // restore session from refreshToken on page load

  return (
    <>
      <Toaster
        position="top-center"
        toastOptions={{
          duration: 3000,
          style: {
            borderRadius: '12px',
            fontFamily: 'Inter, sans-serif',
            fontSize: '14px',
          },
          success: {
            iconTheme: { primary: '#F8C200', secondary: '#000' },
          },
        }}
      />
      <Routes>
        {/* ── Public Auth Routes ──────────────────────────── */}
        <Route
          path="/login"
          element={
            <GuestRoute>
              <LoginPage />
            </GuestRoute>
          }
        />
        <Route
          path="/signup"
          element={
            <GuestRoute>
              <SignupPage />
            </GuestRoute>
          }
        />
        <Route path="/verify-otp" element={<VerifyOtpPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password/:token" element={<ResetPasswordPage />} />

        {/* ── Customer Routes ─────────────────────────────── */}
        <Route path="/" element={<ProtectedRoute><HomePage /></ProtectedRoute>} />
        <Route path="/category/:slug" element={<ProtectedRoute><CategoryPage /></ProtectedRoute>} />
        <Route path="/search" element={<ProtectedRoute><SearchPage /></ProtectedRoute>} />
        <Route path="/product/:productId" element={<ProtectedRoute><ProductDetailPage /></ProtectedRoute>} />
        <Route path="/cart"              element={<ProtectedRoute><CartPage /></ProtectedRoute>} />
        <Route path="/checkout"          element={<ProtectedRoute><CheckoutPage /></ProtectedRoute>} />
        <Route path="/orders"            element={<ProtectedRoute><OrdersPage /></ProtectedRoute>} />
        <Route path="/orders/:orderId"   element={<ProtectedRoute><OrderDetailPage /></ProtectedRoute>} />

        {/* ── Admin Routes ────────────────────────────────── */}
        <Route
          path="/admin"
          element={
            <AdminRoute>
              <AdminDashboardPage />
            </AdminRoute>
          }
        />
        <Route
          path="/admin/*"
          element={
            <AdminRoute>
              <AdminDashboardPage />
            </AdminRoute>
          }
        />

        {/* ── Agent Routes ────────────────────────────────── */}
        <Route
          path="/agent"
          element={
            <AgentRoute>
              <AgentDashboardPage />
            </AgentRoute>
          }
        />

        {/* ── Fallback ────────────────────────────────────── */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}
