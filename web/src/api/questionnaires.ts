import apiClient from './client';

export const listQuestionnaires = (params?: Record<string, string>) =>
  apiClient.get('/questionnaires', { params });
export const createQuestionnaire = (data: { name: string; type: string; dueDate?: string; ownerUserId?: number }) =>
  apiClient.post('/questionnaires', data);
export const getQuestionnaire = (id: string) =>
  apiClient.get(`/questionnaires/${id}`);
export const importSpreadsheet = (id: string, data: { objectKey: string }) =>
  apiClient.post(`/questionnaires/${id}/import/spreadsheet`, data);
export const submitColumnMappings = (id: string, data: { objectKey: string; mappings: Record<string, string> }) =>
  apiClient.post(`/questionnaires/${id}/import/spreadsheet/columns`, data);
export const suggestAnswer = (id: string, itemId: string) =>
  apiClient.post(`/questionnaires/${id}/items/${itemId}/suggest`);
export const saveResponse = (id: string, itemId: string, data: { answerText: string; explanation?: string; status: string }) =>
  apiClient.post(`/questionnaires/${id}/items/${itemId}/response`, data);
export const completeQuestionnaire = (id: string, importAnswersToLibrary: boolean) =>
  apiClient.post(`/questionnaires/${id}/complete`, { importAnswersToLibrary });
export const getPendingImports = () => apiClient.get('/imports/pending-answers');
export const listPendingImportAnswers = () => apiClient.get('/imports/pending-answers/list');
export const importAnswersToLibrary = (responseIds: string[]) =>
  apiClient.post('/imports/pending-answers/import', { responseIds });
