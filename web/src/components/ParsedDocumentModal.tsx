import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import { X, ChevronLeft, ChevronRight } from 'lucide-react';
import { getArtifactContent } from '../api/documents';

interface TableData {
  title: string;
  rows: string[][];
}

interface ParsedData {
  content: string;
  tables: TableData[];
  metadata?: Record<string, unknown>;
}

interface Props {
  versionId: string;
  filename: string;
  onClose: () => void;
}

const ParsedDocumentModal = ({ versionId, filename, onClose }: Props) => {
  const [data, setData] = useState<ParsedData | null>(null);
  const [activeTab, setActiveTab] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const load = async () => {
      try {
        const res = await getArtifactContent(versionId, 'PARSED_JSON');
        const parsed = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
        setData(parsed);
      } catch (err: any) {
        setError(err?.message || 'Failed to load parsed document');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [versionId]);

  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [onClose]);

  const hasTables = data?.tables && data.tables.length > 0;

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalStyle} onClick={e => e.stopPropagation()}>
        <div style={headerStyle}>
          <div>
            <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>{filename}</h2>
            {hasTables && (
              <span style={{ fontSize: 12, color: '#888' }}>
                {data!.tables.length} sheet{data!.tables.length !== 1 ? 's' : ''}
              </span>
            )}
          </div>
          <button onClick={onClose} style={closeBtnStyle}><X size={18} /></button>
        </div>

        {loading && <div style={centerStyle}>Loading...</div>}
        {error && <div style={{ ...centerStyle, color: '#c62828' }}>{error}</div>}

        {data && !hasTables && (
          <div style={{ padding: 24, overflow: 'auto', flex: 1 }}>
            <pre style={preStyle}>{data.content}</pre>
          </div>
        )}

        {data && hasTables && (
          <>
            <div style={tabBarStyle}>
              {data.tables.length > 5 && (
                <button
                  style={tabNavBtnStyle}
                  onClick={() => setActiveTab(Math.max(0, activeTab - 1))}
                  disabled={activeTab === 0}
                >
                  <ChevronLeft size={14} />
                </button>
              )}
              {data.tables.map((t, i) => (
                <button
                  key={i}
                  onClick={() => setActiveTab(i)}
                  style={{
                    ...tabStyle,
                    ...(i === activeTab ? activeTabStyle : {}),
                  }}
                >
                  {t.title}
                </button>
              ))}
              {data.tables.length > 5 && (
                <button
                  style={tabNavBtnStyle}
                  onClick={() => setActiveTab(Math.min(data.tables.length - 1, activeTab + 1))}
                  disabled={activeTab === data.tables.length - 1}
                >
                  <ChevronRight size={14} />
                </button>
              )}
            </div>
            <div style={{ flex: 1, overflow: 'auto', padding: 16 }}>
              <SheetTable table={data.tables[activeTab]} />
            </div>
          </>
        )}
      </div>
    </div>
  );
};

const SheetTable = ({ table }: { table: TableData }) => {
  if (!table.rows || table.rows.length === 0) {
    return <p style={{ color: '#999', fontStyle: 'italic' }}>Empty sheet</p>;
  }

  const headers = table.rows[0];
  const dataRows = table.rows.slice(1);

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={tableStyle}>
        <thead>
          <tr>
            <th style={{ ...thStyle, width: 40, color: '#999' }}>#</th>
            {headers.map((h, i) => (
              <th key={i} style={thStyle}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {dataRows.map((row, ri) => (
            <tr key={ri} style={ri % 2 === 0 ? {} : { background: '#fafafa' }}>
              <td style={{ ...tdStyle, color: '#999', fontSize: 11 }}>{ri + 1}</td>
              {row.map((cell, ci) => (
                <td key={ci} style={tdStyle}>{cell}</td>
              ))}
              {row.length < headers.length &&
                Array.from({ length: headers.length - row.length }).map((_, k) => (
                  <td key={`pad-${k}`} style={tdStyle}></td>
                ))
              }
            </tr>
          ))}
        </tbody>
      </table>
      <div style={{ fontSize: 11, color: '#999', marginTop: 8, textAlign: 'right' }}>
        {dataRows.length} row{dataRows.length !== 1 ? 's' : ''} &middot; {headers.length} column{headers.length !== 1 ? 's' : ''}
      </div>
    </div>
  );
};

const overlayStyle: CSSProperties = {
  position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  zIndex: 10000,
};
const modalStyle: CSSProperties = {
  background: '#fff', borderRadius: 12, width: '90vw', maxWidth: 1200,
  height: '85vh', display: 'flex', flexDirection: 'column',
  boxShadow: '0 20px 60px rgba(0,0,0,0.3)', overflow: 'hidden',
};
const headerStyle: CSSProperties = {
  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
  padding: '14px 20px', borderBottom: '1px solid #e8e8e8',
};
const closeBtnStyle: CSSProperties = {
  background: 'none', border: 'none', cursor: 'pointer',
  color: '#666', padding: 4, borderRadius: 4,
};
const centerStyle: CSSProperties = {
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  flex: 1, fontSize: 14, color: '#666',
};
const preStyle: CSSProperties = {
  whiteSpace: 'pre-wrap', wordWrap: 'break-word',
  fontSize: 13, lineHeight: 1.6, margin: 0,
  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
};
const tabBarStyle: CSSProperties = {
  display: 'flex', gap: 0, background: '#f9f9f9',
  borderBottom: '2px solid #e0e0e0', padding: '0 12px',
  overflowX: 'auto',
};
const tabStyle: CSSProperties = {
  padding: '10px 18px', border: 'none', background: 'none',
  cursor: 'pointer', fontSize: 13, fontWeight: 500, color: '#666',
  borderBottom: '2px solid transparent', marginBottom: -2,
  whiteSpace: 'nowrap',
};
const activeTabStyle: CSSProperties = {
  color: '#6c63ff', borderBottomColor: '#6c63ff', fontWeight: 600,
};
const tabNavBtnStyle: CSSProperties = {
  background: 'none', border: 'none', cursor: 'pointer',
  color: '#999', padding: '8px 4px', display: 'flex', alignItems: 'center',
};
const tableStyle: CSSProperties = {
  borderCollapse: 'collapse', width: '100%', fontSize: 12,
  background: '#fff', border: '1px solid #e0e0e0',
};
const thStyle: CSSProperties = {
  background: '#f5f5f5', fontWeight: 600, textAlign: 'left',
  padding: '8px 12px', border: '1px solid #e0e0e0',
  whiteSpace: 'nowrap', position: 'sticky', top: 0,
};
const tdStyle: CSSProperties = {
  padding: '6px 12px', border: '1px solid #e8e8e8',
  verticalAlign: 'top', maxWidth: 400, wordWrap: 'break-word',
};

export default ParsedDocumentModal;
