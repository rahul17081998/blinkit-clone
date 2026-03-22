import api from './axios';

export const reviewApi = {
  getProductReviews: (productId, params) =>
    api.get(`/api/reviews/product/${productId}`, { params }),
  getProductSummary: (productId) =>
    api.get(`/api/reviews/product/${productId}/summary`),
  getMyReviews: () => api.get('/api/reviews/me'),
  submitReview: (data) => api.post('/api/reviews', data),
  deleteReview: (reviewId) => api.delete(`/api/reviews/${reviewId}`),
};
