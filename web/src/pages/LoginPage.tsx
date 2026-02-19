import React, { useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { useNavigate } from 'react-router-dom';

const LoginPage = () => {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [tab, setTab] = useState<'login' | 'register'>('login');
  const [tenantSlug, setTenantSlug] = useState('');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (tab === 'login') {
        await login(email, password, tenantSlug);
      } else {
        await register(tenantSlug, name, email, password);
      }
      navigate('/questionnaires');
    } catch (err: any) {
      setError(err.response?.data?.error || 'Something went wrong');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f5f5f7' }}>
      <div style={{ background: '#fff', borderRadius: 12, padding: 40, width: 400, boxShadow: '0 4px 24px rgba(0,0,0,0.08)' }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 4, textAlign: 'center' }}>SecTool</h1>
        <p style={{ fontSize: 14, color: '#666', textAlign: 'center', marginBottom: 24 }}>Questionnaire Automation</p>
        <div style={{ display: 'flex', marginBottom: 24, borderBottom: '2px solid #eee' }}>
          {(['login', 'register'] as const).map(t => (
            <button key={t} onClick={() => setTab(t)} style={{
              flex: 1, padding: '8px 0', border: 'none', background: 'none', cursor: 'pointer',
              fontWeight: tab === t ? 600 : 400, color: tab === t ? '#6c63ff' : '#999',
              borderBottom: tab === t ? '2px solid #6c63ff' : '2px solid transparent',
              marginBottom: -2, fontSize: 14,
            }}>
              {t === 'login' ? 'Sign In' : 'Sign Up'}
            </button>
          ))}
        </div>
        <form onSubmit={handleSubmit}>
          <label style={labelStyle}>Organization</label>
          <input value={tenantSlug} onChange={e => setTenantSlug(e.target.value)} placeholder="your-org" required style={inputStyle} />
          {tab === 'register' && (
            <>
              <label style={labelStyle}>Name</label>
              <input value={name} onChange={e => setName(e.target.value)} placeholder="Jane Doe" required style={inputStyle} />
            </>
          )}
          <label style={labelStyle}>Email</label>
          <input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="jane@example.com" required style={inputStyle} />
          <label style={labelStyle}>Password</label>
          <input type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="••••••••" required style={inputStyle} />
          {error && <p style={{ color: '#e74c3c', fontSize: 13, margin: '8px 0' }}>{error}</p>}
          <button type="submit" disabled={loading} style={{
            width: '100%', padding: '10px 0', background: '#6c63ff', color: '#fff',
            border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 14, fontWeight: 600,
            marginTop: 16, opacity: loading ? 0.7 : 1,
          }}>
            {loading ? 'Please wait...' : tab === 'login' ? 'Sign In' : 'Create Account'}
          </button>
        </form>
      </div>
    </div>
  );
};

const labelStyle: React.CSSProperties = { display: 'block', fontSize: 13, fontWeight: 500, color: '#333', marginBottom: 4, marginTop: 12 };
const inputStyle: React.CSSProperties = {
  width: '100%', padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6,
  fontSize: 14, outline: 'none', boxSizing: 'border-box',
};

export default LoginPage;
