import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import { useParams } from 'react-router-dom';
import { ThumbsUp, ThumbsDown, CheckCircle, Sparkles, AlertTriangle, ExternalLink, X } from 'lucide-react';
import { getQuestionnaire, suggestAnswer, saveResponse, completeQuestionnaire } from '../api/questionnaires';
import { ragFeedback, getKbChunk } from '../api/rag';

interface QuestionnaireItem {
  id: string;
  itemIndex: number;
  questionText: string;
  responseType: string;
  currentState: string;
  sourceLocation?: string;
}

interface Suggestion {
  suggestionId: string;
  answerText: string;
  citations: string;
  confidence: number;
  coverageStatus: string;
}

interface ParsedCitation {
  type: string;
  id: string;
  label: string;
}

const parseCitations = (raw: string): ParsedCitation[] => {
  if (!raw) return [];
  let items: string[];
  try {
    const parsed = JSON.parse(raw);
    items = Array.isArray(parsed) ? parsed : [raw];
  } catch {
    items = raw.split(',');
  }
  return items.map((c, i) => {
    const trimmed = c.trim();
    if (trimmed.startsWith('kb_chunk:')) {
      return { type: 'kb_chunk', id: trimmed.replace('kb_chunk:', ''), label: `Chunk ${i + 1}` };
    }
    if (trimmed.startsWith('answer_library:')) {
      return { type: 'answer_library', id: trimmed.replace('answer_library:', ''), label: `Library ${i + 1}` };
    }
    return { type: 'unknown', id: trimmed, label: `Source ${i + 1}` };
  }).filter(c => c.id);
};

const stateColors: Record<string, { bg: string; text: string; label: string }> = {
  UNANSWERED: { bg: '#f0f0f0', text: '#666', label: 'Unanswered' },
  SUGGESTED: { bg: '#e3f2fd', text: '#1565c0', label: 'Suggested' },
  DRAFTED: { bg: '#fff8e1', text: '#f57f17', label: 'Draft' },
  NEEDS_REVIEW: { bg: '#fff3e0', text: '#e65100', label: 'Needs Review' },
  APPROVED: { bg: '#e8f5e9', text: '#2e7d32', label: 'Approved' },
};

const QuestionnaireDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const [questionnaire, setQuestionnaire] = useState<any>(null);
  const [items, setItems] = useState<QuestionnaireItem[]>([]);
  const [selectedIdx, setSelectedIdx] = useState(0);
  const [suggestion, setSuggestion] = useState<Suggestion | null>(null);
  const [answerText, setAnswerText] = useState('');
  const [explanation, setExplanation] = useState('');
  const [suggesting, setSuggesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [citationDrawer, setCitationDrawer] = useState<ParsedCitation | null>(null);
  const [citationContent, setCitationContent] = useState<string>('');

  const fetchData = async () => {
    if (!id) return;
    const res = await getQuestionnaire(id);
    setQuestionnaire(res.data.questionnaire);
    setItems(res.data.items || []);
  };

  useEffect(() => { fetchData(); }, [id]);

  const selectedItem = items[selectedIdx];

  const handleSuggest = async () => {
    if (!selectedItem || !id) return;
    setSuggesting(true);
    setSuggestion(null);
    try {
      const res = await suggestAnswer(id, selectedItem.id);
      setSuggestion(res.data);
      setAnswerText(res.data.answerText);
    } catch (err) {
      console.error(err);
    } finally {
      setSuggesting(false);
    }
  };

  const handleSave = async (status: string) => {
    if (!selectedItem || !id) return;
    setSaving(true);
    try {
      await saveResponse(id, selectedItem.id, { answerText, explanation, status });
      await fetchData();
    } catch (err) {
      console.error(err);
    } finally {
      setSaving(false);
    }
  };

  const handleFeedback = async (thumb: string) => {
    if (!suggestion) return;
    await ragFeedback(suggestion.suggestionId, thumb);
  };

  const handleCitationClick = async (citation: ParsedCitation) => {
    setCitationDrawer(citation);
    setCitationContent('Loading...');
    if (citation.type === 'kb_chunk') {
      try {
        const res = await getKbChunk(citation.id);
        const chunk = res.data;
        const header = [
          chunk.documentTitle && `Document: ${chunk.documentTitle}`,
          chunk.documentType && `Type: ${chunk.documentType}`,
          chunk.versionNum != null && `Version: ${chunk.versionNum}`,
          chunk.chunkIndex !== null && `Chunk #${chunk.chunkIndex}`,
        ].filter(Boolean).join('  ·  ');

        let meta = '';
        if (chunk.metadata) {
          try {
            const m = typeof chunk.metadata === 'string' ? JSON.parse(chunk.metadata) : chunk.metadata;
            const parts = [
              m.sheet && `Sheet: ${m.sheet}`,
              m.category && `Category: ${m.category}`,
              m.type && `Type: ${m.type}`,
              m.startRow && `Rows: ${m.startRow}–${m.endRow}`,
            ].filter(Boolean);
            if (parts.length) meta = '\n' + parts.join('  ·  ');
          } catch { /* ignore */ }
        }

        setCitationContent((header ? header + '\n' : '') + (meta ? meta + '\n\n' : '\n') + (chunk.text || '(empty chunk)'));
      } catch {
        setCitationContent('Failed to load chunk content.');
      }
    } else {
      setCitationContent(`Answer library entry: ${citation.id}`);
    }
  };

  const handleComplete = async () => {
    if (!id) return;
    const importAnswers = confirm('Import approved answers to your answer library?');
    await completeQuestionnaire(id, importAnswers);
    fetchData();
  };

  if (!questionnaire) return <div style={{ padding: 40, textAlign: 'center', color: '#999' }}>Loading...</div>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 48px)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 0', borderBottom: '1px solid #eee' }}>
        <div>
          <h1 style={{ fontSize: 20, fontWeight: 700, margin: 0 }}>{questionnaire.name}</h1>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 4 }}>
            <div style={{ width: 120, height: 6, background: '#eee', borderRadius: 3, overflow: 'hidden' }}>
              <div style={{ width: `${questionnaire.progressPercent}%`, height: '100%', background: '#6c63ff', borderRadius: 3 }} />
            </div>
            <span style={{ fontSize: 13, color: '#888' }}>{questionnaire.progressPercent}% complete</span>
          </div>
        </div>
        <button onClick={handleComplete} style={{
          background: '#2e7d32', color: '#fff', border: 'none', borderRadius: 8,
          padding: '8px 20px', cursor: 'pointer', fontWeight: 600, fontSize: 14,
          display: 'flex', alignItems: 'center', gap: 6,
        }}>
          <CheckCircle size={16} /> Complete
        </button>
      </div>

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden', marginTop: 16 }}>
        <div style={{ width: 340, overflowY: 'auto', borderRight: '1px solid #eee', paddingRight: 12 }}>
          {items.map((item, i) => {
            const sc = stateColors[item.currentState] || stateColors.UNANSWERED;
            const active = i === selectedIdx;
            return (
              <div key={item.id} onClick={() => { setSelectedIdx(i); setSuggestion(null); setAnswerText(''); setExplanation(''); }}
                style={{
                  padding: '12px 14px', borderRadius: 8, marginBottom: 4, cursor: 'pointer',
                  background: active ? '#f0f0ff' : '#fff',
                  border: active ? '1px solid #6c63ff' : '1px solid transparent',
                }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', gap: 8 }}>
                  <span style={{ fontSize: 13, color: '#333', lineHeight: 1.4 }}>
                    <strong style={{ color: '#888', marginRight: 6 }}>Q{item.itemIndex + 1}.</strong>
                    {item.questionText.length > 80 ? item.questionText.slice(0, 80) + '...' : item.questionText}
                  </span>
                  <span style={{
                    fontSize: 11, fontWeight: 600, padding: '2px 8px', borderRadius: 10,
                    background: sc.bg, color: sc.text, whiteSpace: 'nowrap', flexShrink: 0,
                  }}>{sc.label}</span>
                </div>
              </div>
            );
          })}
          {items.length === 0 && <p style={{ padding: 20, color: '#999', textAlign: 'center' }}>No items yet</p>}
        </div>

        <div style={{ flex: 1, overflowY: 'auto', paddingLeft: 24 }}>
          {selectedItem ? (
            <div>
              <h3 style={{ fontSize: 16, fontWeight: 600, marginBottom: 8, color: '#333' }}>{selectedItem.questionText}</h3>
              {selectedItem.sourceLocation && (() => {
                try {
                  const loc = JSON.parse(selectedItem.sourceLocation);
                  return (
                    <div style={{ fontSize: 12, color: '#888', marginBottom: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      {loc.sheet && <span style={sourceBadgeStyle}>Sheet: {loc.sheet}</span>}
                      {loc.category && <span style={sourceBadgeStyle}>Category: {loc.category}</span>}
                      {loc.row !== undefined && <span style={sourceBadgeStyle}>Row: {loc.row}</span>}
                    </div>
                  );
                } catch { return null; }
              })()}

              <button onClick={handleSuggest} disabled={suggesting} style={{
                display: 'flex', alignItems: 'center', gap: 6, background: '#6c63ff', color: '#fff',
                border: 'none', borderRadius: 8, padding: '8px 18px', cursor: 'pointer',
                fontSize: 13, fontWeight: 600, marginBottom: 16, opacity: suggesting ? 0.7 : 1,
              }}>
                <Sparkles size={14} /> {suggesting ? 'Generating...' : 'Get AI Suggestion'}
              </button>

              {suggestion && (
                <div style={{ background: '#f8f7ff', border: '1px solid #e0deff', borderRadius: 10, padding: 16, marginBottom: 20 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <span style={{ fontSize: 12, fontWeight: 600, color: '#6c63ff' }}>AI Suggestion</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <span style={{ fontSize: 12, color: '#888' }}>
                        Confidence: {Math.round(suggestion.confidence * 100)}%
                      </span>
                      {suggestion.coverageStatus === 'INSUFFICIENT_EVIDENCE' && (
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, color: '#e65100', background: '#fff3e0', padding: '2px 8px', borderRadius: 10 }}>
                          <AlertTriangle size={12} /> Insufficient Evidence
                        </span>
                      )}
                    </div>
                  </div>
                  <p style={{ fontSize: 14, color: '#333', lineHeight: 1.6, marginBottom: 12 }}>{suggestion.answerText}</p>
                  {suggestion.citations && (
                    <div style={{ marginBottom: 8 }}>
                      <span style={{ fontSize: 12, color: '#888', marginRight: 8 }}>Sources:</span>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 4 }}>
                        {parseCitations(suggestion.citations).map((cit, i) => (
                          <button key={i} onClick={() => handleCitationClick(cit)} style={{
                            display: 'inline-flex', alignItems: 'center', gap: 4,
                            background: '#e8eaf6', color: '#3949ab', border: 'none', borderRadius: 12,
                            padding: '3px 10px', fontSize: 11, fontWeight: 600, cursor: 'pointer',
                          }}>
                            <ExternalLink size={10} /> {cit.label}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                  <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                    <button onClick={() => handleFeedback('UP')} style={thumbBtnStyle} title="Good answer">
                      <ThumbsUp size={14} />
                    </button>
                    <button onClick={() => handleFeedback('DOWN')} style={thumbBtnStyle} title="Could be improved">
                      <ThumbsDown size={14} />
                    </button>
                  </div>
                </div>
              )}

              <div style={{ marginBottom: 12 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: '#333', marginBottom: 4 }}>Answer</label>
                <textarea value={answerText} onChange={e => setAnswerText(e.target.value)} rows={4}
                  style={{ width: '100%', padding: 10, border: '1px solid #ddd', borderRadius: 8, fontSize: 14, resize: 'vertical', boxSizing: 'border-box' as const }} />
              </div>

              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: '#333', marginBottom: 4 }}>Explanation (optional)</label>
                <textarea value={explanation} onChange={e => setExplanation(e.target.value)} rows={2}
                  style={{ width: '100%', padding: 10, border: '1px solid #ddd', borderRadius: 8, fontSize: 14, resize: 'vertical', boxSizing: 'border-box' as const }} />
              </div>

              <div style={{ display: 'flex', gap: 10 }}>
                <button onClick={() => handleSave('DRAFT')} disabled={saving} style={{
                  padding: '8px 20px', border: '1px solid #ddd', borderRadius: 8, background: '#fff',
                  cursor: 'pointer', fontSize: 13, fontWeight: 600, color: '#333',
                }}>Save Draft</button>
                <button onClick={() => handleSave('APPROVED')} disabled={saving} style={{
                  padding: '8px 20px', background: '#2e7d32', color: '#fff', border: 'none',
                  borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 600,
                }}>Approve</button>
              </div>
            </div>
          ) : (
            <div style={{ padding: 40, textAlign: 'center', color: '#999' }}>Select a question to begin</div>
          )}
        </div>
      </div>

      {citationDrawer && (
        <div style={{
          position: 'fixed', top: 0, right: 0, bottom: 0, width: 420,
          background: '#fff', boxShadow: '-4px 0 24px rgba(0,0,0,0.12)',
          zIndex: 1000, display: 'flex', flexDirection: 'column',
        }}>
          <div style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            padding: '16px 20px', borderBottom: '1px solid #eee',
          }}>
            <div>
              <h3 style={{ fontSize: 15, fontWeight: 600, margin: 0 }}>Citation Detail</h3>
              <span style={{ fontSize: 12, color: '#888' }}>
                {citationDrawer.type === 'kb_chunk' ? 'Knowledge Base Chunk' : 'Answer Library Entry'}
              </span>
            </div>
            <button onClick={() => setCitationDrawer(null)} style={{
              background: 'none', border: 'none', cursor: 'pointer', padding: 4,
            }}>
              <X size={18} />
            </button>
          </div>
          <div style={{ flex: 1, overflow: 'auto', padding: 20 }}>
            {(() => {
              const lines = citationContent.split('\n');
              const metaEnd = lines.findIndex(l => l === '' && lines.indexOf(l) > 0);
              const headerLines = metaEnd > 0 ? lines.slice(0, metaEnd) : [];
              const bodyText = metaEnd > 0 ? lines.slice(metaEnd + 1).join('\n') : citationContent;
              return (
                <>
                  {headerLines.length > 0 && (
                    <div style={{
                      background: '#f5f5f5', borderRadius: 8, padding: '10px 14px', marginBottom: 12,
                      fontSize: 12, color: '#555', lineHeight: 1.6,
                    }}>
                      {headerLines.map((line, i) => <div key={i}>{line}</div>)}
                    </div>
                  )}
                  <div style={{
                    background: '#fffde7', border: '1px solid #fff9c4', borderRadius: 8,
                    padding: 16, marginBottom: 16,
                  }}>
                    <div style={{ fontSize: 12, fontWeight: 600, color: '#f57f17', marginBottom: 8 }}>
                      {citationDrawer.label} &middot; ID: {citationDrawer.id.slice(0, 8)}...
                    </div>
                    <pre style={{
                      fontSize: 13, color: '#333', whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                      lineHeight: 1.6, margin: 0, fontFamily: 'inherit',
                    }}>
                      {bodyText}
                    </pre>
                  </div>
                </>
              );
            })()}
            <p style={{ fontSize: 12, color: '#999' }}>
              This source was used as evidence for the AI-generated answer.
            </p>
          </div>
        </div>
      )}
    </div>
  );
};

const thumbBtnStyle: CSSProperties = {
  background: '#fff', border: '1px solid #ddd', borderRadius: 6, padding: '5px 10px',
  cursor: 'pointer', display: 'flex', alignItems: 'center',
};

const sourceBadgeStyle: CSSProperties = {
  background: '#f0f0f0', padding: '2px 8px', borderRadius: 4, fontSize: 11, color: '#666',
};

export default QuestionnaireDetailPage;
