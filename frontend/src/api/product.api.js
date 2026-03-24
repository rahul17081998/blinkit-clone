import api from './axios';

export const productApi = {
  // GET /api/products?page=&size=&sortBy=&sortDir=
  getProducts: (params) => api.get('/api/products', { params }),
  // GET /api/products/category/{slug}?page=&size=
  getProductsByCategory: (slug, params) => api.get(`/api/products/category/${slug}`, { params }),
  // GET /api/products/search?q=&page=&size=
  searchProducts: (q, params) => api.get('/api/products/search', { params: { q, ...params } }),
  // GET /api/products/featured
  getFeaturedProducts: (params) => api.get('/api/products/featured', { params }),
  getProduct: (productId) => api.get(`/api/products/${productId}`),
  getCategories: () => api.get('/api/categories'),
  getInventory: (productId) => api.get(`/api/inventory/${productId}`),
  getActiveCoupons: () => api.get('/api/coupons/active'),
  getApplicableCoupons: (cartTotal) => api.get('/api/coupons/applicable', { params: { cartTotal } }),
};
