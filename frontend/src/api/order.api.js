import api from './axios';

export const orderApi = {
  placeOrder:  (addressId, paymentMethod = 'WALLET', notes = '') => api.post('/api/orders', { addressId, paymentMethod, notes }),
  getOrders:   (params)                => api.get('/api/orders', { params }),
  getOrder:    (orderId)               => api.get(`/api/orders/${orderId}`),
  cancelOrder: (orderId)               => api.post(`/api/orders/${orderId}/cancel`),
  trackOrder:  (orderId)               => api.get(`/api/delivery/track/${orderId}`),
};
