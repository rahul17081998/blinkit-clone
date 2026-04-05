import { useState, useRef, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { ShoppingCart, Search, MapPin, User, LogOut, Package, Wallet } from 'lucide-react';
import toast from 'react-hot-toast';
import { useAuthStore } from '../../stores/authStore';
import { useCartStore } from '../../stores/cartStore';
import { useProfileStore } from '../../stores/profileStore';
import { authApi } from '../../api/auth.api';

export default function Header() {
  const [searchQuery, setSearchQuery] = useState('');
  const [showUserMenu, setShowUserMenu] = useState(false);
  const menuRef = useRef(null);
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const NO_SEARCH_PATHS = ['/orders', '/profile', '/wallet', '/cart', '/checkout'];
  const showSearch = !NO_SEARCH_PATHS.some(p => pathname === p || pathname.startsWith(p + '/'));

  const { isLoggedIn, email, role, logout } = useAuthStore();
  const profileImageUrl = useProfileStore(s => s.profileImageUrl);
  const itemCount = useCartStore(s => s.items.reduce((sum, i) => sum + i.quantity, 0));

  // Close menu when clicking outside
  useEffect(() => {
    const handler = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setShowUserMenu(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleSearch = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/search?q=${encodeURIComponent(searchQuery.trim())}`);
      setSearchQuery('');
    }
  };

  const clearProfile = useProfileStore(s => s.clearProfile);

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } catch {}
    logout();
    clearProfile();
    toast.success('Logged out');
    navigate('/login');
  };

  const userInitial = email ? email[0].toUpperCase() : 'U';

  return (
    <header className="sticky top-0 z-50 bg-white border-b border-gray-100 shadow-sm">
      <div className="max-w-7xl mx-auto px-4 h-16 flex items-center gap-3">

        {/* Logo */}
        <Link to="/" className="flex items-center gap-1.5 flex-shrink-0">
          <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center">
            <span className="text-dark font-black text-base">B</span>
          </div>
          <span className="text-lg font-black text-dark hidden sm:block">blinkit</span>
        </Link>

        {/* Delivery location */}
        <div className="hidden md:flex items-center gap-1 text-sm text-gray-600 flex-shrink-0 cursor-pointer hover:text-gray-900">
          <MapPin size={14} className="text-primary" />
          <span className="font-medium">Delivery in 10 min</span>
        </div>

        {/* Search bar */}
        {showSearch && <form onSubmit={handleSearch} className="flex-1 max-w-xl">
          <div className="relative">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder='Search "atta, dal, rice, eggs..."'
              className="w-full pl-9 pr-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
            />
          </div>
        </form>}

        {/* Right actions */}
        <div className="flex items-center gap-2 flex-shrink-0 ml-auto">

          {/* Cart */}
          <Link
            to="/cart"
            className="relative flex items-center gap-1.5 bg-primary hover:bg-yellow-400 text-dark font-semibold text-sm px-3 py-2 rounded-xl transition-colors"
          >
            <ShoppingCart size={18} />
            <span className="hidden sm:inline">Cart</span>
            {itemCount > 0 && (
              <span className="absolute -top-1.5 -right-1.5 bg-dark text-white text-xs font-bold w-5 h-5 rounded-full flex items-center justify-center">
                {itemCount > 9 ? '9+' : itemCount}
              </span>
            )}
          </Link>

          {/* User menu */}
          {isLoggedIn ? (
            <div className="relative" ref={menuRef}>
              <button
                onClick={() => setShowUserMenu(v => !v)}
                className="w-9 h-9 rounded-full bg-primary flex items-center justify-center text-dark font-bold text-sm hover:bg-yellow-400 transition-colors overflow-hidden"
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
                    <p className="text-xs font-semibold text-primary">{role}</p>
                  </div>
                  {role === 'ADMIN' && (
                    <Link to="/admin" onClick={() => setShowUserMenu(false)}
                      className="flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                      <User size={15} /> Admin Panel
                    </Link>
                  )}
                  <Link to="/orders" onClick={() => setShowUserMenu(false)}
                    className="flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                    <Package size={15} /> My Orders
                  </Link>
                  <Link to="/wallet" onClick={() => setShowUserMenu(false)}
                    className="flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                    <Wallet size={15} /> Wallet
                  </Link>
                  <Link to="/profile" onClick={() => setShowUserMenu(false)}
                    className="flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                    <User size={15} /> Profile
                  </Link>
                  <hr className="my-1 border-gray-100" />
                  <button onClick={handleLogout}
                    className="w-full flex items-center gap-2 px-4 py-2 text-sm text-red-500 hover:bg-red-50">
                    <LogOut size={15} /> Logout
                  </button>
                </div>
              )}
            </div>
          ) : (
            <Link to="/login"
              className="text-sm font-semibold text-dark border border-gray-200 px-3 py-2 rounded-xl hover:bg-gray-50">
              Login
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
