import { useEffect, useState, useRef } from 'react';
import type { CSSProperties } from 'react';
import { Upload, FileText, Eye, Code } from 'lucide-react';
import { listDocuments, createDocument, createVersion, completeUpload, getPreviewUrl } from '../api/documents';
import ParsedDocumentModal from '../components/ParsedDocumentModal';

interface Doc {
  id: string;
  title: string;
  type: string;
  source: string;
  createdAt: string;
  latestVersionId?: string;
  latestVersionStatus?: string;
  originalFilename?: string;
}

const DocumentsPage = () => {
  const [documents, setDocuments] = useState<Doc[]>([]);
  const [title, setTitle] = useState('');
  const [docType, setDocType] = useState('POLICY');
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [parsedModal, setParsedModal] = useState<{ versionId: string; filename: string } | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const fetchDocs = async () => {
    try {
      const res = await listDocuments();
      setDocuments(res.data || []);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { fetchDocs(); }, []);

  const handleUpload = async () => {
    setError('');
    const file = fileRef.current?.files?.[0];
    if (!title.trim()) { setError('Please enter a document title.'); return; }
    if (!file) { setError('Please select a file.'); return; }
    setUploading(true);
    try {
      const docRes = await createDocument({ type: docType, title, source: 'UPLOAD' });
      const docId = docRes.data.documentId;

      const verRes = await createVersion(docId, {
        originalFilename: file.name,
        mimeType: file.type || 'application/octet-stream',
        sizeBytes: file.size,
        sha256: 'pending',
      });

      const { documentVersionId, uploadUrl } = verRes.data;

      const putRes = await fetch(uploadUrl, {
        method: 'PUT',
        body: file,
        headers: { 'Content-Type': file.type || 'application/octet-stream' },
      });

      if (!putRes.ok) {
        throw new Error(`S3 upload failed: ${putRes.status} ${putRes.statusText}`);
      }

      await completeUpload(documentVersionId);
      setTitle('');
      if (fileRef.current) fileRef.current.value = '';
      fetchDocs();
    } catch (err: any) {
      console.error('Upload failed:', err);
      setError(err?.response?.data?.message || err?.message || 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const openPreview = async (versionId: string | undefined) => {
    if (!versionId) return;
    try {
      const res = await getPreviewUrl(versionId);
      window.open(res.data.downloadUrl, '_blank');
    } catch (err) {
      console.error(err);
    }
  };

  const openParsed = (versionId: string | undefined, filename: string | undefined) => {
    if (!versionId) return;
    setParsedModal({ versionId, filename: filename || 'Document' });
  };

  return (
    <div>
      <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 20 }}>Documents</h1>

      <div style={{
        background: '#fff', borderRadius: 10, padding: 24, marginBottom: 24,
        boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
      }}>
        <h3 style={{ fontSize: 16, fontWeight: 600, marginBottom: 16 }}>Upload Document</h3>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'end' }}>
          <div>
            <label style={labelStyle}>Title</label>
            <input value={title} onChange={e => setTitle(e.target.value)} placeholder="e.g. SOC2 Policy" style={inputStyle} />
          </div>
          <div>
            <label style={labelStyle}>Type</label>
            <select value={docType} onChange={e => setDocType(e.target.value)} style={selectStyle}>
              <option value="POLICY">Policy</option>
              <option value="QUESTIONNAIRE">Questionnaire</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
          <div>
            <label style={labelStyle}>File</label>
            <input type="file" ref={fileRef} style={{ fontSize: 14 }} />
          </div>
          <button type="button" onClick={handleUpload} disabled={uploading} style={{
            display: 'flex', alignItems: 'center', gap: 6, background: '#6c63ff', color: '#fff',
            border: 'none', borderRadius: 8, padding: '8px 20px', cursor: 'pointer',
            fontSize: 14, fontWeight: 600, opacity: uploading ? 0.7 : 1, height: 38,
          }}>
            <Upload size={14} /> {uploading ? 'Uploading...' : 'Upload'}
          </button>
        </div>
        {error && (
          <p style={{ color: '#c0392b', fontSize: 13, marginTop: 12 }}>{error}</p>
        )}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
        {documents.map(doc => (
          <div key={doc.id} style={{
            background: '#fff', borderRadius: 10, padding: 20,
            boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
              <FileText size={20} color="#6c63ff" />
              <div>
                <div style={{ fontWeight: 600, fontSize: 15 }}>{doc.title}</div>
                <div style={{ fontSize: 12, color: '#888' }}>
                  {doc.type} &middot; {new Date(doc.createdAt).toLocaleDateString()}
                  {doc.originalFilename && <> &middot; {doc.originalFilename}</>}
                </div>
              </div>
            </div>
            {doc.latestVersionStatus && (
              <div style={{ fontSize: 12, marginBottom: 8 }}>
                Status: <span style={{
                  fontWeight: 600,
                  color: doc.latestVersionStatus === 'READY' ? '#2e7d32'
                    : doc.latestVersionStatus === 'FAILED' ? '#c62828'
                    : '#1565c0'
                }}>{doc.latestVersionStatus}</span>
              </div>
            )}
            <div style={{ display: 'flex', gap: 8 }}>
              {doc.latestVersionId ? (
                <>
                  <button onClick={() => openPreview(doc.latestVersionId)} style={linkBtnStyle}>
                    <Eye size={13} /> Original
                  </button>
                  <button onClick={() => openParsed(doc.latestVersionId, doc.originalFilename || doc.title)}
                    disabled={doc.latestVersionStatus !== 'READY'}
                    style={{ ...linkBtnStyle, ...(doc.latestVersionStatus !== 'READY' ? { opacity: 0.4, cursor: 'not-allowed' } : {}) }}>
                    <Code size={13} /> Parsed
                  </button>
                </>
              ) : (
                <span style={{ fontSize: 12, color: '#999' }}>No version uploaded yet</span>
              )}
            </div>
          </div>
        ))}
        {documents.length === 0 && (
          <p style={{ color: '#999', padding: 20 }}>No documents uploaded yet</p>
        )}
      </div>

      {parsedModal && (
        <ParsedDocumentModal
          versionId={parsedModal.versionId}
          filename={parsedModal.filename}
          onClose={() => setParsedModal(null)}
        />
      )}
    </div>
  );
};

const labelStyle: CSSProperties = { display: 'block', fontSize: 13, fontWeight: 500, color: '#333', marginBottom: 4 };
const inputStyle: CSSProperties = { padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 14, width: 200 };
const selectStyle: CSSProperties = { padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 14, background: '#fff' };
const linkBtnStyle: CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 4, background: 'none', border: '1px solid #ddd',
  borderRadius: 6, padding: '5px 12px', cursor: 'pointer', fontSize: 12, color: '#555',
};

export default DocumentsPage;
