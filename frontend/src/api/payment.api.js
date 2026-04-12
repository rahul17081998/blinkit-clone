import api from './axios';

export const paymentApi = {
  getWallet:           ()       => api.get('/api/payments/wallet'),
  getHistory:          (params) => api.get('/api/payments/history', { params }),
  // Payment methods (enabled only + wallet balance in one call)
  getMethods:          ()       => api.get('/api/payments/methods'),
  // Razorpay
  createRazorpayOrder: (data)   => api.post('/api/payments/razorpay/create-order', data),
  verifyRazorpay:      (data)   => api.post('/api/payments/razorpay/verify', data),
};
