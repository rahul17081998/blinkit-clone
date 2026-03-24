import api from './axios';

export const paymentApi = {
  getWallet:      ()                    => api.get('/api/payments/wallet'),
  getHistory:     (params)              => api.get('/api/payments/history', { params }),
};
