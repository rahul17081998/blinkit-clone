import api from './axios';

export const userApi = {
  getProfile:        ()                    => api.get('/api/users/profile'),
  deleteAccount:     ()                    => api.delete('/api/auth/account'),
  updateProfile:     (data)                => api.put('/api/users/profile', data),
  uploadProfilePhoto: (file)               => {
    const form = new FormData();
    form.append('file', file);
    return api.post('/api/users/profile/photo', form, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  getAddresses:      ()                    => api.get('/api/users/addresses'),
  addAddress:        (data)                => api.post('/api/users/addresses', data),
  updateAddress:     (addressId, data)     => api.put(`/api/users/addresses/${addressId}`, data),
  deleteAddress:     (addressId)           => api.delete(`/api/users/addresses/${addressId}`),
  setDefaultAddress: (addressId)           => api.put(`/api/users/addresses/${addressId}/default`),
};
