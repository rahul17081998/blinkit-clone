import { NavLink, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
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
} from 'lucide-react';
import toast from 'react-hot-toast';
import { useAuthStore } from '../../stores/authStore';
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

export default function AdminDashboardPage() {
  const navigate = useNavigate();
  const { logout } = useAuthStore();

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } catch {}
    logout();
    toast.success('Logged out');
    navigate('/login');
  };

  return (
    <div className="min-h-screen flex bg-gray-100">
      {/* Sidebar */}
      <aside className="w-56 bg-gray-900 text-white flex flex-col fixed top-0 left-0 h-full z-40">
        {/* Logo */}
        <div className="h-16 flex items-center gap-2 px-5 border-b border-gray-700">
          <a
            href="/"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-2 hover:opacity-80 transition-opacity"
          >
            <div className="w-8 h-8 bg-yellow-400 rounded-lg flex items-center justify-center flex-shrink-0">
              <span className="text-gray-900 font-black text-base">B</span>
            </div>
            <span className="text-base font-black text-white">Blinkit</span>
          </a>
        </div>

        {/* Nav */}
        <nav className="flex-1 py-4 overflow-y-auto">
          {NAV_ITEMS.map(({ label, path, icon: Icon, end }) => (
            <NavLink
              key={path}
              to={path}
              end={end}
              className={({ isActive }) =>
                `flex items-center gap-3 px-5 py-3 mx-2 rounded-xl text-sm font-medium transition-all ${
                  isActive
                    ? 'bg-yellow-400 text-gray-900'
                    : 'text-gray-300 hover:bg-gray-800 hover:text-white'
                }`
              }
            >
              <Icon size={18} />
              {label}
            </NavLink>
          ))}
        </nav>

        {/* Logout */}
        <div className="p-4 border-t border-gray-700">
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-medium text-gray-300 hover:bg-red-600 hover:text-white transition-all"
          >
            <LogOut size={18} />
            Logout
          </button>
        </div>
      </aside>

      {/* Main content */}
      <div className="flex-1 ml-56 flex flex-col min-h-screen">
        {/* Top header */}
        <header className="h-16 bg-white border-b border-gray-200 flex items-center px-6 sticky top-0 z-30">
          <a
            href="/"
            target="_blank"
            rel="noopener noreferrer"
            className="text-lg font-bold text-gray-900 hover:text-yellow-500 transition-colors"
          >
            Blinkit
          </a>
        </header>

        {/* Content */}
        <main className="flex-1 p-6 overflow-auto">
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
        </main>
      </div>
    </div>
  );
}
