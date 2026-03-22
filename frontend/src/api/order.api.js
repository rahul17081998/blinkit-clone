import api from './axios';

export const orderApi = {
  placeOrder:  (addressId, notes = '') => api.post('/api/orders', { addressId, notes }),
  getOrders:   (params)                => api.get('/api/orders', { params }),
  getOrder:    (orderId)               => api.get(`/api/orders/${orderId}`),
  cancelOrder: (orderId)               => api.post(`/api/orders/${orderId}/cancel`),
};
