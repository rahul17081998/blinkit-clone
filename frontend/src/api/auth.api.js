import api from './axios';

export const authApi = {
  login: (data) => api.post('/api/auth/login', data),
  signup: (data) => api.post('/api/auth/signup', data),

  // GET /api/auth/verify?email=&otp=
  verifyOtp: ({ email, otp }) => api.get('/api/auth/verify', { params: { email, otp } }),

  // No resend endpoint in backend — user must re-signup or wait for existing OTP
  // resendOtp is omitted

  forgotPassword: (data) => api.post('/api/auth/forgot-password', data),
  resetPassword: (token, newPassword) => api.post(`/api/auth/reset-password/${token}`, { newPassword }),
  validateResetToken: (token) => api.get(`/api/auth/reset-password/validate/${token}`),

  // POST /api/auth/refresh — needs both userId and refreshToken
  refreshToken: ({ userId, refreshToken }) =>
    api.post('/api/auth/refresh', { userId, refreshToken }),

  logout: () => api.post('/api/auth/logout'),
};
