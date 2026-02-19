import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Search, X, AlertCircle } from 'lucide-react';
import { listQuestionnaires, createQuestionnaire, getPendingImports } from '../api/questionnaires';
import ImportReviewModal from '../components/ImportReviewModal';

interface Questionnaire {
  id: string;
  name: string;
  type: string;
  status: string;
  progressPercent: number;
  dueDate: string | null;
  ownerUserId: number | null;
  createdAt: string;
  updatedAt: string;
}

const statusColors: Record<string, { bg: string; text: string }> = {
  IN_PROGRESS: { bg: '#e8f4fd', text: '#1976d2' },
  APPROVED: { bg: '#e8f5e9', text: '#388e3c' },
  COMPLETED: { bg: '#f3e5f5', text: '#7b1fa2' },
};

const QuestionnairesListPage = () => {
  const navigate = useNavigate();
  const [questionnaires, setQuestionnaires] = useState<Questionnaire[]>([]);
  const [pendingCount, setPendingCount] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [newName, setNewName] = useState('');
  const [newType, setNewType] = useState('SPREADSHEET');
  const [newDueDate, setNewDueDate] = useState('');
  const [loading, setLoading] = useState(true);
  const [showReview, setShowReview] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const params: Record<string, string> = {};
      if (statusFilter) params.status = statusFilter;
      const [qRes, pRes] = await Promise.all([
        listQuestionnaires(params),
        getPendingImports(),
      ]);
      setQuestionnaires(qRes.data);
      setPendingCount(pRes.data.count || 0);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, [statusFilter]);

  const handleCreate = async () => {
    if (!newName.trim()) return;
    try {
      await createQuestionnaire({
        name: newName,
        type: newType,
        dueDate: newDueDate || undefined,
      });
      setShowModal(false);
      setNewName('');
      setNewDueDate('');
      fetchData();
    } catch (err) {
      console.error(err);
    }
  };

  const filtered = questionnaires.filter(q =>
    !search || q.name.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div>
      {pendingCount > 0 && (
        <div style={{
          background: '#fff3e0', border: '1px solid #ffcc02', borderRadius: 8,
          padding: '12px 20px', marginBottom: 16, display: 'flex', alignItems: 'center', gap: 12,
        }}>
          <AlertCircle size={18} color="#e65100" />
          <span style={{ flex: 1, fontSize: 14 }}>
            Review <strong>{pendingCount}</strong> answers pending import
          </span>
          <button onClick={() => setShowReview(true)} style={{
            background: '#e65100', color: '#fff', border: 'none', borderRadius: 6,
            padding: '6px 16px', cursor: 'pointer', fontSize: 13, fontWeight: 600,
          }}>Review</button>
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, margin: 0 }}>Questionnaires</h1>
        <button onClick={() => setShowModal(true)} style={{
          display: 'flex', alignItems: 'center', gap: 6, background: '#6c63ff', color: '#fff',
          border: 'none', borderRadius: 8, padding: '8px 18px', cursor: 'pointer', fontSize: 14, fontWeight: 600,
        }}>
          <Plus size={16} /> Add questionnaire
        </button>
      </div>

      <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} style={selectStyle}>
          <option value="">All statuses</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="APPROVED">Approved</option>
          <option value="COMPLETED">Completed</option>
        </select>
        <div style={{ position: 'relative', flex: 1, minWidth: 200 }}>
          <Search size={16} style={{ position: 'absolute', left: 10, top: 9, color: '#999' }} />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search questionnaires..."
            style={{ ...inputStyle, paddingLeft: 32, width: '100%' }} />
        </div>
        <button onClick={() => { setStatusFilter(''); setSearch(''); }} style={{
          background: 'none', border: '1px solid #ddd', borderRadius: 6, padding: '6px 14px',
          cursor: 'pointer', fontSize: 13, color: '#666',
        }}>Reset</button>
      </div>

      <div style={{ background: '#fff', borderRadius: 8, overflow: 'hidden', boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
          <thead>
            <tr style={{ background: '#f9f9fb', borderBottom: '1px solid #eee' }}>
              {['Questionnaire', 'Progress', 'Due date', 'Status', 'Type', 'Created', 'Updated'].map(h => (
                <th key={h} style={{ padding: '10px 14px', textAlign: 'left', fontWeight: 600, color: '#555', fontSize: 12, textTransform: 'uppercase' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={7} style={{ padding: 40, textAlign: 'center', color: '#999' }}>Loading...</td></tr>
            ) : filtered.length === 0 ? (
              <tr><td colSpan={7} style={{ padding: 40, textAlign: 'center', color: '#999' }}>No questionnaires found</td></tr>
            ) : filtered.map(q => {
              const sc = statusColors[q.status] || { bg: '#eee', text: '#333' };
              return (
                <tr key={q.id} onClick={() => navigate(`/questionnaires/${q.id}`)}
                  style={{ borderBottom: '1px solid #f0f0f0', cursor: 'pointer' }}
                  onMouseOver={e => (e.currentTarget.style.background = '#fafafa')}
                  onMouseOut={e => (e.currentTarget.style.background = '')}>
                  <td style={cellStyle}><strong>{q.name}</strong></td>
                  <td style={cellStyle}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div style={{ width: 80, height: 6, background: '#eee', borderRadius: 3, overflow: 'hidden' }}>
                        <div style={{ width: `${q.progressPercent}%`, height: '100%', background: '#6c63ff', borderRadius: 3 }} />
                      </div>
                      <span style={{ fontSize: 12, color: '#888' }}>{q.progressPercent}%</span>
                    </div>
                  </td>
                  <td style={cellStyle}>{q.dueDate || '—'}</td>
                  <td style={cellStyle}>
                    <span style={{ background: sc.bg, color: sc.text, padding: '3px 10px', borderRadius: 12, fontSize: 12, fontWeight: 600 }}>
                      {q.status.replace('_', ' ')}
                    </span>
                  </td>
                  <td style={cellStyle}>{q.type}</td>
                  <td style={cellStyle}>{q.createdAt ? new Date(q.createdAt).toLocaleDateString() : '—'}</td>
                  <td style={cellStyle}>{q.updatedAt ? new Date(q.updatedAt).toLocaleDateString() : '—'}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex',
          alignItems: 'center', justifyContent: 'center', zIndex: 1000,
        }}>
          <div style={{ background: '#fff', borderRadius: 12, padding: 32, width: 440, boxShadow: '0 8px 32px rgba(0,0,0,0.15)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <h2 style={{ fontSize: 18, fontWeight: 700, margin: 0 }}>Add questionnaire</h2>
              <button onClick={() => setShowModal(false)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}><X size={20} /></button>
            </div>
            <label style={labelStyle}>Name</label>
            <input value={newName} onChange={e => setNewName(e.target.value)} placeholder="Q1 2026 Security Review" style={inputStyle} />
            <label style={{ ...labelStyle, marginTop: 12 }}>Type</label>
            <select value={newType} onChange={e => setNewType(e.target.value)} style={selectStyle}>
              <option value="SPREADSHEET">Spreadsheet</option>
              <option value="DOCUMENT">Document</option>
              <option value="WEBSITE">Website</option>
            </select>
            <label style={{ ...labelStyle, marginTop: 12 }}>Due date</label>
            <input type="date" value={newDueDate} onChange={e => setNewDueDate(e.target.value)} style={inputStyle} />
            <button onClick={handleCreate} style={{
              width: '100%', marginTop: 20, padding: '10px 0', background: '#6c63ff', color: '#fff',
              border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600,
            }}>Create</button>
          </div>
        </div>
      )}

      {showReview && (
        <ImportReviewModal
          onClose={() => setShowReview(false)}
          onImported={() => fetchData()}
        />
      )}
    </div>
  );
};

const cellStyle: CSSProperties = { padding: '10px 14px' };
const selectStyle: CSSProperties = { padding: '7px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 14, background: '#fff', minWidth: 140 };
const inputStyle: CSSProperties = { width: '100%', padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 14, boxSizing: 'border-box' as const };
const labelStyle: CSSProperties = { display: 'block', fontSize: 13, fontWeight: 500, color: '#333', marginBottom: 4 };

export default QuestionnairesListPage;
