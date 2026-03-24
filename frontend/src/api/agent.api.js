import api from './axios';

export const agentApi = {
  getMyProfile:      ()           => api.get('/api/delivery/partners/me'),
  registerProfile:   (data)       => api.post('/api/delivery/partners/register', data),
  updateProfile:     (data)       => api.put('/api/delivery/partners/me', data),
  setAvailability:   (available)  => api.put('/api/delivery/partners/me/availability', { available }),
  getMyTasks:        ()           => api.get('/api/delivery/tasks/mine'),
  updateTaskStatus:  (taskId, status, failureReason) =>
    api.put(`/api/delivery/tasks/${taskId}/status`, { status, ...(failureReason ? { failureReason } : {}) }),
  getAddressById:    (addressId)  => api.get(`/api/users/delivery/address/${addressId}`),
};
