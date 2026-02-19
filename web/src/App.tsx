import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import QuestionnairesListPage from './pages/QuestionnairesListPage';
import QuestionnaireDetailPage from './pages/QuestionnaireDetailPage';
import DocumentsPage from './pages/DocumentsPage';
import ColumnMappingPage from './pages/ColumnMappingPage';

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { user, loading } = useAuth();
  if (loading) return <div>Loading...</div>;
  if (!user) return <Navigate to="/login" />;
  return <>{children}</>;
};

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route index element={<Navigate to="/questionnaires" />} />
        <Route path="questionnaires" element={<QuestionnairesListPage />} />
        <Route path="questionnaires/:id" element={<QuestionnaireDetailPage />} />
        <Route path="questionnaires/:id/import" element={<ColumnMappingPage />} />
        <Route path="documents" element={<DocumentsPage />} />
      </Route>
    </Routes>
  );
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
