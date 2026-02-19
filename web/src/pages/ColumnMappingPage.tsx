import { useEffect, useState } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';
import { importSpreadsheet, submitColumnMappings } from '../api/questionnaires';

const mappingOptions = [
  { value: 'SKIP', label: 'Skip this column' },
  { value: 'QUESTION', label: 'Question' },
  { value: 'ANSWER', label: 'Answer (Yes/No/N/A)' },
  { value: 'ANSWER_AND_EXPLANATION', label: 'Answer and explanation' },
  { value: 'EXPLANATION', label: 'Explanation' },
];

const ColumnMappingPage = () => {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const objectKey = searchParams.get('objectKey') || '';
  const [columns, setColumns] = useState<string[]>([]);
  const [rows, setRows] = useState<string[][]>([]);
  const [mappings, setMappings] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!objectKey || !id) return;
    importSpreadsheet(id, { objectKey }).then(res => {
      const preview = res.data.preview;
      setColumns(preview.columns);
      setRows(preview.rows);
      const initial: Record<string, string> = {};
      preview.columns.forEach((_: string, i: number) => { initial[String(i)] = 'SKIP'; });
      setMappings(initial);
    }).catch(() => setError('Failed to load preview')).finally(() => setLoading(false));
  }, [id, objectKey]);

  const validate = () => {
    const questionCols = Object.values(mappings).filter(v => v === 'QUESTION').length;
    const answerCols = Object.values(mappings).filter(v => ['ANSWER', 'ANSWER_AND_EXPLANATION'].includes(v)).length;
    if (questionCols !== 1) return 'Exactly one column must be mapped as Question';
    if (answerCols < 1) return 'At least one answer column must be mapped';
    return '';
  };

  const handleSubmit = async () => {
    const err = validate();
    if (err) { setError(err); return; }
    setSubmitting(true);
    try {
      await submitColumnMappings(id!, { objectKey, mappings });
      navigate(`/questionnaires/${id}`);
    } catch {
      setError('Import failed');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <div style={{ padding: 40, textAlign: 'center', color: '#999' }}>Loading preview...</div>;

  return (
    <div>
      <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 8 }}>Map Spreadsheet Columns</h1>
      <p style={{ color: '#666', fontSize: 14, marginBottom: 24 }}>
        Assign a role to each column to import questions and answers.
      </p>

      {error && (
        <div style={{ background: '#fde8e8', color: '#c0392b', padding: '10px 16px', borderRadius: 8, marginBottom: 16, fontSize: 14 }}>
          {error}
        </div>
      )}

      <div style={{ overflowX: 'auto', marginBottom: 24 }}>
        <table style={{ borderCollapse: 'collapse', fontSize: 13, minWidth: '100%' }}>
          <thead>
            <tr>
              {columns.map((col, i) => (
                <th key={i} style={{ padding: '8px 12px', background: '#f9f9fb', borderBottom: '2px solid #eee', textAlign: 'left', minWidth: 160 }}>
                  <div style={{ marginBottom: 6, fontWeight: 600, color: '#333' }}>{col}</div>
                  <select value={mappings[String(i)] || 'SKIP'} onChange={e => setMappings({ ...mappings, [String(i)]: e.target.value })}
                    style={{ width: '100%', padding: '5px 8px', border: '1px solid #ddd', borderRadius: 4, fontSize: 12, background: '#fff' }}>
                    {mappingOptions.map(opt => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
                  </select>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, ri) => (
              <tr key={ri} style={{ borderBottom: '1px solid #f0f0f0' }}>
                {row.map((cell, ci) => (
                  <td key={ci} style={{ padding: '6px 12px', color: '#555', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {cell}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{ display: 'flex', gap: 12 }}>
        <button onClick={() => navigate(`/questionnaires/${id}`)} style={{
          padding: '10px 24px', border: '1px solid #ddd', borderRadius: 8, background: '#fff',
          cursor: 'pointer', fontSize: 14, color: '#666',
        }}>Cancel</button>
        <button onClick={handleSubmit} disabled={submitting} style={{
          padding: '10px 24px', background: '#6c63ff', color: '#fff', border: 'none',
          borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600,
          opacity: submitting ? 0.7 : 1,
        }}>{submitting ? 'Importing...' : 'Import Questions'}</button>
      </div>
    </div>
  );
};

export default ColumnMappingPage;
