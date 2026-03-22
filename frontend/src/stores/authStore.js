import { create } from 'zustand';

// accessToken lives in memory only — never persisted to localStorage
// refreshToken is stored in localStorage for session persistence across page reloads

export const useAuthStore = create((set, get) => ({
  // ── State ───────────────────────────────────────────────────
  userId: null,
  email: null,
  role: null,           // 'CUSTOMER' | 'ADMIN' | 'DELIVERY_AGENT'
  accessToken: null,    // IN MEMORY ONLY
  isLoggedIn: false,
  isInitialized: false, // true after we've attempted to restore session

  // ── Actions ─────────────────────────────────────────────────
  setAuth: ({ userId, email, role, accessToken, refreshToken }) => {
    if (refreshToken) localStorage.setItem('refreshToken', refreshToken);
    if (userId) localStorage.setItem('userId', userId);
    set({ userId, email, role, accessToken, isLoggedIn: true });
  },

  setAccessToken: (accessToken) => set({ accessToken }),

  logout: () => {
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userId');
    set({
      userId: null,
      email: null,
      role: null,
      accessToken: null,
      isLoggedIn: false,
    });
  },

  setInitialized: () => set({ isInitialized: true }),

  // Helper — get the dashboard path for this role
  getDashboardPath: () => {
    const { role } = get();
    if (role === 'ADMIN') return '/admin';
    if (role === 'DELIVERY_AGENT') return '/agent';
    return '/';
  },
}));
