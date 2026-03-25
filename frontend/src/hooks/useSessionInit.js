import { useEffect } from 'react';
import { useAuthStore } from '../stores/authStore';
import { useCartStore } from '../stores/cartStore';
import { useProfileStore } from '../stores/profileStore';
import { authApi } from '../api/auth.api';
import { userApi } from '../api/user.api';

export function useSessionInit() {
  const { setAuth, setInitialized, logout } = useAuthStore();
  const loadFromBackend = useCartStore(s => s.loadFromBackend);
  const setProfile = useProfileStore(s => s.setProfile);

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
        // Load backend cart and profile after session restore
        await loadFromBackend();
        const profileRes = await userApi.getProfile();
        setProfile(profileRes.data.data);
      } catch {
        logout();
      } finally {
        setInitialized();
      }
    };
    init();
  }, []);
}
