import apiClient from './client';

export const ragSuggest = (question: string) =>
  apiClient.post('/rag/suggest', { question });
export const ragFeedback = (suggestionId: string, thumb: string, comment?: string) =>
  apiClient.post('/rag/feedback', { suggestionId, thumb, comment });
export const getKbChunk = (chunkId: string) =>
  apiClient.get(`/kb/chunks/${chunkId}`);
