import api from './axios';

export const cartApi = {
  getCart:      ()                    => api.get('/api/cart'),
  addItem:      (productId, quantity) => api.post('/api/cart/items', { productId, quantity }),
  updateItem:   (productId, quantity) => api.put(`/api/cart/items/${productId}`, { quantity }),
  removeItem:   (productId)           => api.delete(`/api/cart/items/${productId}`),
  clearCart:    ()                    => api.delete('/api/cart'),
  applyPromo:          (couponCode) => api.post('/api/cart/promo', { code: couponCode }),
  removePromo:         ()           => api.delete('/api/cart/promo'),
  removeDeliveryPromo: ()           => api.delete('/api/cart/promo/delivery'),
};
