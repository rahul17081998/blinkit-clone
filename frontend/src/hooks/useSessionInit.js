import { useEffect } from 'react';
import { useAuthStore } from '../stores/authStore';
import { useCartStore } from '../stores/cartStore';
import { authApi } from '../api/auth.api';

export function useSessionInit() {
  const { setAuth, setInitialized, logout } = useAuthStore();
  const loadFromBackend = useCartStore(s => s.loadFromBackend);

  useEffect(() => {
    const init = async () => {
      const refreshToken = localStorage.getItem('refreshToken');
      const userId       = localStorage.getItem('userId');
      if (!refreshToken || !userId) {
        setInitialized();
        return;
      }
      try {
        const res = await authApi.refreshToken({ userId, refreshToken });
        const { userId: uid, email, role, accessToken, refreshToken: newRT } = res.data.data;
        setAuth({ userId: uid, email, role, accessToken, refreshToken: newRT });
        // Load backend cart after session restore
        await loadFromBackend();
      } catch {
        logout();
      } finally {
        setInitialized();
      }
    };
    init();
  }, []);
}
