import apiClient from './client';

export const listDocuments = () => apiClient.get('/documents');
export const createDocument = (data: { type: string; title: string; source: string }) =>
  apiClient.post('/documents', data);
export const createVersion = (documentId: string, data: { originalFilename: string; mimeType: string; sizeBytes: number; sha256: string }) =>
  apiClient.post(`/documents/${documentId}/versions`, data);
export const completeUpload = (versionId: string) =>
  apiClient.post(`/document-versions/${versionId}/complete`);
export const getVersion = (versionId: string) =>
  apiClient.get(`/document-versions/${versionId}`);
export const getPreviewUrl = (versionId: string) =>
  apiClient.get(`/document-versions/${versionId}/preview`);
export const getArtifactUrl = (versionId: string, kind: string) =>
  apiClient.get(`/document-versions/${versionId}/artifacts/${kind}`);
export const getArtifactContent = (versionId: string, kind: string) =>
  apiClient.get(`/document-versions/${versionId}/artifacts/${kind}/content`);
