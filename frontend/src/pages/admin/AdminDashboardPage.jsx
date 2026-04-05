import { useState, useRef, useEffect } from 'react';
import { NavLink, Link, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  Package,
  Tag,
  Warehouse,
  ShoppingBag,
  Ticket,
  Truck,
  CreditCard,
  MessageSquare,
  LogOut,
  Menu,
  X,
  User,
  Wallet,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { useAuthStore } from '../../stores/authStore';
import { useProfileStore } from '../../stores/profileStore';
import { authApi } from '../../api/auth.api';

import OverviewSection from './sections/OverviewSection';
import ProductsSection from './sections/ProductsSection';
import CategoriesSection from './sections/CategoriesSection';
import InventorySection from './sections/InventorySection';
import OrdersSection from './sections/OrdersSection';
import AdminOrderDetailPage from './sections/AdminOrderDetailPage';
import AdminProductDetailPage from './sections/AdminProductDetailPage';
import CouponsSection from './sections/CouponsSection';
import DeliverySection from './sections/DeliverySection';
import PaymentsSection from './sections/PaymentsSection';
import ReviewsSection from './sections/ReviewsSection';

const NAV_ITEMS = [
  { label: 'Overview',   path: '/admin',           icon: LayoutDashboard, end: true },
  { label: 'Products',   path: '/admin/products',   icon: Package },
  { label: 'Categories', path: '/admin/categories', icon: Tag },
  { label: 'Inventory',  path: '/admin/inventory',  icon: Warehouse },
  { label: 'Orders',     path: '/admin/orders',     icon: ShoppingBag },
  { label: 'Coupons',    path: '/admin/coupons',    icon: Ticket },
  { label: 'Delivery',   path: '/admin/delivery',   icon: Truck },
  { label: 'Payments',   path: '/admin/payments',   icon: CreditCard },
  { label: 'Reviews',    path: '/admin/reviews',    icon: MessageSquare },
];

function SidebarContent({ onNavClick, onLogout }) {
  return (
    <>
      {/* Logo */}
      <div className="h-16 flex items-center gap-2 px-5 border-b border-gray-700 flex-shrink-0">
        <a
          href="/"
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 hover:opacity-80 transition-opacity"
        >
          <div className="w-8 h-8 bg-yellow-400 rounded-lg flex items-center justify-center flex-shrink-0">
            <span className="text-gray-900 font-black text-base">B</span>
          </div>
          <span className="text-base font-black text-white">Blinkit Admin</span>
        </a>
      </div>

      {/* Nav */}
      <nav className="flex-1 py-4 overflow-y-auto">
        {NAV_ITEMS.map(({ label, path, icon: Icon, end }) => (
          <NavLink
            key={path}
            to={path}
            end={end}
            onClick={onNavClick}
            className={({ isActive }) =>
              `flex items-center gap-3 px-5 py-3 mx-2 rounded-xl text-sm font-medium transition-all ${
                isActive
                  ? 'bg-yellow-400 text-gray-900'
                  : 'text-gray-300 hover:bg-gray-800 hover:text-white'
              }`
            }
          >
            <Icon size={18} className="flex-shrink-0" />
            {label}
          </NavLink>
        ))}
      </nav>

      {/* Logout */}
      <div className="p-4 border-t border-gray-700 flex-shrink-0">
        <button
          onClick={onLogout}
          className="w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-medium text-gray-300 hover:bg-red-600 hover:text-white transition-all"
        >
          <LogOut size={18} />
          Logout
        </button>
      </div>
    </>
  );
}

export default function AdminDashboardPage() {
  const navigate = useNavigate();
  const { logout, email } = useAuthStore();
  const profileImageUrl = useProfileStore(s => s.profileImageUrl);
  const clearProfile = useProfileStore(s => s.clearProfile);

  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [showUserMenu, setShowUserMenu] = useState(false);
  const menuRef = useRef(null);

  // Close user menu on outside click
  useEffect(() => {
    const handler = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setShowUserMenu(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } catch {}
    logout();
    clearProfile();
    toast.success('Logged out');
    navigate('/login');
  };

  const closeSidebar = () => setSidebarOpen(false);
  const userInitial = email ? email[0].toUpperCase() : 'A';

  return (
    <div className="min-h-screen flex bg-gray-100">

      {/* ── Desktop sidebar (hidden on mobile) ── */}
      <aside className="hidden md:flex w-56 bg-gray-900 text-white flex-col fixed top-0 left-0 h-full z-40">
        <SidebarContent onNavClick={undefined} onLogout={handleLogout} />
      </aside>

      {/* ── Mobile sidebar drawer ── */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-40 md:hidden"
          onClick={closeSidebar}
        />
      )}
      <aside
        className={`fixed top-0 left-0 h-full w-64 bg-gray-900 text-white flex flex-col z-50 transform transition-transform duration-300 md:hidden ${
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <button
          onClick={closeSidebar}
          className="absolute top-4 right-4 text-gray-400 hover:text-white"
        >
          <X size={20} />
        </button>
        <SidebarContent onNavClick={closeSidebar} onLogout={handleLogout} />
      </aside>

      {/* ── Main content ── */}
      <div className="flex-1 md:ml-56 flex flex-col min-h-screen min-w-0 overflow-x-hidden">

        {/* Top header */}
        <header className="h-16 bg-white border-b border-gray-200 flex items-center px-4 md:px-6 sticky top-0 z-30 gap-3">

          {/* Hamburger — mobile only */}
          <button
            onClick={() => setSidebarOpen(true)}
            className="md:hidden p-1.5 rounded-lg text-gray-600 hover:bg-gray-100 transition-colors"
            aria-label="Open menu"
          >
            <Menu size={22} />
          </button>

          <span className="text-lg font-bold text-gray-900 flex-1">
            Blinkit Admin
          </span>

          {/* User profile circle */}
          <div className="relative" ref={menuRef}>
            <button
              onClick={() => setShowUserMenu(v => !v)}
              className="w-9 h-9 rounded-full bg-yellow-400 flex items-center justify-center text-gray-900 font-bold text-sm hover:bg-yellow-500 transition-colors overflow-hidden flex-shrink-0"
            >
              {profileImageUrl
                ? <img src={profileImageUrl} alt="avatar" className="w-full h-full object-cover" />
                : userInitial
              }
            </button>

            {showUserMenu && (
              <div className="absolute right-0 top-11 w-48 bg-white rounded-2xl shadow-lg border border-gray-100 py-2 z-50">
                <div className="px-4 py-2 border-b border-gray-100 mb-1">
                  <p className="text-xs text-gray-500 truncate">{email}</p>
                  <p className="text-xs font-semibold text-yellow-500">ADMIN</p>
                </div>
                <Link
                  to="/"
                  onClick={() => setShowUserMenu(false)}
                  className="flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
                >
                  <ShoppingBag size={15} /> Customer View
                </Link>
                <Link
                  to="/orders"
                  onClick={() => setShowUserMenu(false)}
                  className="flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
                >
                  <Package size={15} /> My Orders
                </Link>
                <Link
                  to="/wallet"
                  onClick={() => setShowUserMenu(false)}
                  className="flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
                >
                  <Wallet size={15} /> Wallet
                </Link>
                <Link
                  to="/profile"
                  onClick={() => setShowUserMenu(false)}
                  className="flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
                >
                  <User size={15} /> Profile
                </Link>
                <hr className="my-1 border-gray-100" />
                <button
                  onClick={handleLogout}
                  className="w-full flex items-center gap-2 px-4 py-2 text-sm text-red-500 hover:bg-red-50"
                >
                  <LogOut size={15} /> Logout
                </button>
              </div>
            )}
          </div>
        </header>

        {/* Content */}
        <main className="flex-1 p-4 md:p-6 overflow-y-auto w-full max-w-full">
          <div className="w-full max-w-full overflow-x-hidden">
            <Routes>
              <Route index element={<OverviewSection />} />
              <Route path="products" element={<ProductsSection />} />
              <Route path="products/:productId" element={<AdminProductDetailPage />} />
              <Route path="categories" element={<CategoriesSection />} />
              <Route path="inventory" element={<InventorySection />} />
              <Route path="orders" element={<OrdersSection />} />
              <Route path="orders/:orderId" element={<AdminOrderDetailPage />} />
              <Route path="coupons" element={<CouponsSection />} />
              <Route path="delivery" element={<DeliverySection />} />
              <Route path="payments" element={<PaymentsSection />} />
              <Route path="reviews" element={<ReviewsSection />} />
              <Route path="*" element={<Navigate to="/admin" replace />} />
            </Routes>
          </div>
        </main>
      </div>
    </div>
  );
}
