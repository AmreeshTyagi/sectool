import apiClient from './client';

export const login = (email: string, password: string, tenantSlug: string) =>
  apiClient.post('/auth/login', { email, password, tenantSlug });

export const register = (tenantSlug: string, name: string, email: string, password: string) =>
  apiClient.post('/auth/register', { tenantSlug, name, email, password });

export const getMe = () => apiClient.get('/auth/me');
