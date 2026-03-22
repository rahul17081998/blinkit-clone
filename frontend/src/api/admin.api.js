import api from './axios';

export const adminApi = {
  // Products
  createProduct: (data) => api.post('/api/products/admin', data),
  updateProduct: (productId, data) => api.put(`/api/products/admin/${productId}`, data),
  deleteProduct: (productId) => api.delete(`/api/products/admin/${productId}`),
  toggleProduct: (productId) => api.put(`/api/products/admin/${productId}/toggle`),
  // Categories
  createCategory: (data) => api.post('/api/categories/admin', data),
  // Inventory
  getInventory: (params) => api.get('/api/inventory/admin', { params }),
  updateStock: (productId, quantityToAdd, reason) => api.put(`/api/inventory/admin/${productId}`, { quantityToAdd, reason }),
  // Orders
  getAllOrders: (params) => api.get('/api/orders/admin', { params }),
  getOrder: (orderId) => api.get(`/api/orders/admin/${orderId}`),
  updateOrderStatus: (orderId, status) => api.put(`/api/orders/admin/${orderId}/status`, { status }),
  // Coupons
  getCoupons: (params) => api.get('/api/coupons/admin', { params }),
  createCoupon: (data) => api.post('/api/coupons/admin', data),
  updateCoupon: (id, data) => api.put(`/api/coupons/admin/${id}`, data),
  deleteCoupon: (id) => api.delete(`/api/coupons/admin/${id}`),
  // Delivery
  getDeliveryTasks: (params) => api.get('/api/delivery/admin/tasks', { params }),
  assignDeliveryTask: (taskId, partnerId) => api.post(`/api/delivery/admin/tasks/${taskId}/assign`, { partnerId }),
  getDeliveryPartners: (params) => api.get('/api/delivery/admin/partners', { params }),
  toggleDeliveryPartner: (partnerId) => api.put(`/api/delivery/admin/partners/${partnerId}/toggle`),
  // Payments
  getWallets: (params) => api.get('/api/payments/admin/wallets', { params }),
  topUpWallet: (userId, data) => api.post(`/api/payments/admin/wallets/${userId}/topup`, data),
  getTransactions: (params) => api.get('/api/payments/admin/transactions', { params }),
  // Create delivery agent
  createDeliveryAgent: (data) => api.post('/api/auth/delivery-agent', data),
  // Admin user/address lookups
  getUserById: (userId) => api.get(`/api/users/admin/${userId}`),
  getAuthUserById: (userId) => api.get(`/api/auth/admin/user/${userId}`),
  getAddressById: (addressId) => api.get(`/api/users/admin/address/${addressId}`),
  // Delivery task by orderId
  getDeliveryTaskByOrder: (orderId) => api.get(`/api/delivery/admin/tasks/by-order/${orderId}`),
  getDeliveryPartner: (partnerId) => api.get(`/api/delivery/admin/partners/${partnerId}`),
};
