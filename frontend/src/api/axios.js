import axios from 'axios';
import { useAuthStore } from '../stores/authStore';

// Use relative URL — Vite proxy forwards /api → http://localhost:8080 in dev
// In production, set VITE_API_BASE_URL env var
const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

// ── Main Axios instance ──────────────────────────────────────────
const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

// ── Attach accessToken from authStore on every request ──────────
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  console.log(`[Axios] ${config.method?.toUpperCase()} ${config.baseURL}${config.url}`);
  return config;
});

// ── Auto-refresh on 401 ─────────────────────────────────────────
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) prom.reject(error);
    else prom.resolve(token);
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => {
    console.log(`[Axios] ✓ ${response.status} ${response.config.url}`);
    return response;
  },
  async (error) => {
    console.error(`[Axios] ✗ ${error.response?.status} ${error.config?.url}`, error.response?.data);
    const originalRequest = error.config;

    // Don't try to refresh on auth endpoints themselves
    const isAuthEndpoint = originalRequest.url?.includes('/auth/login') ||
      originalRequest.url?.includes('/auth/signup') ||
      originalRequest.url?.includes('/auth/refresh-token');

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthEndpoint) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshToken = localStorage.getItem('refreshToken');
        const userId = localStorage.getItem('userId');
        if (!refreshToken || !userId) throw new Error('No refresh token');

        const response = await axios.post(`${BASE_URL || ''}/api/auth/refresh`, {
          userId,
          refreshToken,
        });

        const newAccessToken = response.data?.data?.accessToken;
        if (!newAccessToken) throw new Error('Refresh failed');

        useAuthStore.getState().setAccessToken(newAccessToken);
        processQueue(null, newAccessToken);
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        useAuthStore.getState().logout();
        // Show toast + redirect — import dynamically to avoid circular deps
        import('react-hot-toast').then(({ default: toast }) => {
          toast.error('Session expired. Please log in again.');
        });
        window.location.href = '/login';
        // Throw a special sentinel so callers know this is an auth error, not a domain error
        const authErr = new Error('SESSION_EXPIRED');
        authErr.isAuthError = true;
        return Promise.reject(authErr);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default api;
