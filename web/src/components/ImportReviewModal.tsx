import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import { X, Check, BookOpen } from 'lucide-react';
import { listPendingImportAnswers, importAnswersToLibrary } from '../api/questionnaires';

interface PendingAnswer {
  responseId: string;
  questionnaireItemId: string;
  questionText: string;
  answerText: string;
  explanation: string | null;
  questionnaireName: string;
}

interface Props {
  onClose: () => void;
  onImported: () => void;
}

const ImportReviewModal = ({ onClose, onImported }: Props) => {
  const [answers, setAnswers] = useState<PendingAnswer[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [importing, setImporting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    loadAnswers();
    const handleEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, []);

  const loadAnswers = async () => {
    try {
      const res = await listPendingImportAnswers();
      setAnswers(res.data);
      setSelected(new Set(res.data.map((a: PendingAnswer) => a.responseId)));
    } catch {
      setError('Failed to load pending answers');
    } finally {
      setLoading(false);
    }
  };

  const toggleSelect = (id: string) => {
    setSelected(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const toggleAll = () => {
    if (selected.size === answers.length) {
      setSelected(new Set());
    } else {
      setSelected(new Set(answers.map(a => a.responseId)));
    }
  };

  const handleImport = async () => {
    if (selected.size === 0) return;
    setImporting(true);
    try {
      await importAnswersToLibrary(Array.from(selected));
      onImported();
      onClose();
    } catch {
      setError('Failed to import answers');
    } finally {
      setImporting(false);
    }
  };

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalStyle} onClick={e => e.stopPropagation()}>
        <div style={headerStyle}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <BookOpen size={20} color="#6c63ff" />
            <h2 style={{ fontSize: 18, fontWeight: 700, margin: 0 }}>Review Answers for Import</h2>
          </div>
          <button onClick={onClose} style={closeBtn}><X size={20} /></button>
        </div>

        <p style={{ fontSize: 13, color: '#666', margin: '0 0 16px', padding: '0 24px' }}>
          Select approved answers to import into your answer library. Imported answers help the AI
          generate more accurate responses for future questionnaires.
        </p>

        {error && <div style={{ padding: '8px 24px', color: '#c62828', fontSize: 13 }}>{error}</div>}

        {loading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#999' }}>Loading...</div>
        ) : answers.length === 0 ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#999' }}>No pending answers to import.</div>
        ) : (
          <>
            <div style={{ padding: '0 24px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13, color: '#555' }}>
                <input type="checkbox" checked={selected.size === answers.length}
                  onChange={toggleAll} style={{ accentColor: '#6c63ff' }} />
                Select all ({selected.size}/{answers.length})
              </label>
            </div>
            <div style={{ flex: 1, overflow: 'auto', padding: '0 24px' }}>
              {answers.map(a => (
                <div key={a.responseId} style={{
                  ...answerCardStyle,
                  borderColor: selected.has(a.responseId) ? '#6c63ff' : '#e0e0e0',
                  background: selected.has(a.responseId) ? '#f8f7ff' : '#fff',
                }}>
                  <label style={{ display: 'flex', gap: 12, cursor: 'pointer' }}>
                    <input type="checkbox" checked={selected.has(a.responseId)}
                      onChange={() => toggleSelect(a.responseId)}
                      style={{ accentColor: '#6c63ff', marginTop: 2, flexShrink: 0 }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 11, color: '#888', marginBottom: 4 }}>{a.questionnaireName}</div>
                      <div style={{ fontSize: 14, fontWeight: 600, color: '#222', marginBottom: 6 }}>{a.questionText}</div>
                      <div style={{ fontSize: 13, color: '#444', lineHeight: 1.5 }}>
                        {a.answerText || <span style={{ color: '#999', fontStyle: 'italic' }}>No answer text</span>}
                      </div>
                      {a.explanation && (
                        <div style={{ fontSize: 12, color: '#777', marginTop: 4, fontStyle: 'italic' }}>
                          {a.explanation}
                        </div>
                      )}
                    </div>
                  </label>
                </div>
              ))}
            </div>
          </>
        )}

        <div style={footerStyle}>
          <button onClick={onClose} style={cancelBtnStyle}>Cancel</button>
          <button onClick={handleImport} disabled={importing || selected.size === 0} style={{
            ...importBtnStyle,
            opacity: importing || selected.size === 0 ? 0.5 : 1,
          }}>
            <Check size={16} />
            {importing ? 'Importing...' : `Import ${selected.size} answer${selected.size !== 1 ? 's' : ''}`}
          </button>
        </div>
      </div>
    </div>
  );
};

const overlayStyle: CSSProperties = {
  position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
  display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
};
const modalStyle: CSSProperties = {
  background: '#fff', borderRadius: 12, width: 640, maxHeight: '80vh',
  display: 'flex', flexDirection: 'column', boxShadow: '0 12px 40px rgba(0,0,0,0.2)',
};
const headerStyle: CSSProperties = {
  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
  padding: '20px 24px 12px', borderBottom: '1px solid #f0f0f0',
};
const closeBtn: CSSProperties = {
  background: 'none', border: 'none', cursor: 'pointer', color: '#999', padding: 4,
};
const answerCardStyle: CSSProperties = {
  border: '1px solid #e0e0e0', borderRadius: 8, padding: 14, marginBottom: 10,
  transition: 'border-color 0.15s, background 0.15s',
};
const footerStyle: CSSProperties = {
  display: 'flex', justifyContent: 'flex-end', gap: 10, padding: '16px 24px',
  borderTop: '1px solid #f0f0f0',
};
const cancelBtnStyle: CSSProperties = {
  background: 'none', border: '1px solid #ddd', borderRadius: 6, padding: '8px 16px',
  cursor: 'pointer', fontSize: 13, color: '#666',
};
const importBtnStyle: CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 6, background: '#6c63ff', color: '#fff',
  border: 'none', borderRadius: 6, padding: '8px 20px', cursor: 'pointer',
  fontSize: 13, fontWeight: 600,
};

export default ImportReviewModal;
