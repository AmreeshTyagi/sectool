import { Outlet, Link, useLocation } from 'react-router-dom';
import { FileText, ClipboardList, LogOut, User } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';

const Layout = () => {
  const { user, logout } = useAuth();
  const location = useLocation();

  const navItems = [
    { to: '/questionnaires', label: 'Questionnaires', icon: ClipboardList },
    { to: '/documents', label: 'Documents', icon: FileText },
  ];

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      <aside style={{
        width: 240,
        background: '#1a1a2e',
        color: '#fff',
        display: 'flex',
        flexDirection: 'column' as const,
        padding: '16px 0',
      }}>
        <div style={{ padding: '0 20px', marginBottom: 32 }}>
          <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0 }}>SecTool</h2>
          <p style={{ fontSize: 12, color: '#888', margin: 0 }}>Questionnaire Automation</p>
        </div>
        <nav style={{ flex: 1 }}>
          {navItems.map(item => {
            const active = location.pathname.startsWith(item.to);
            return (
              <Link key={item.to} to={item.to} style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '10px 20px',
                color: active ? '#fff' : '#aaa',
                background: active ? 'rgba(255,255,255,0.1)' : 'transparent',
                textDecoration: 'none',
                fontSize: 14,
                borderLeft: active ? '3px solid #6c63ff' : '3px solid transparent',
              }}>
                <item.icon size={18} />
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div style={{ padding: '12px 20px', borderTop: '1px solid #333' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <User size={16} />
            <span style={{ fontSize: 13 }}>{user?.name || 'User'}</span>
          </div>
          <button onClick={logout} style={{
            display: 'flex', alignItems: 'center', gap: 8,
            background: 'none', border: 'none', color: '#888',
            cursor: 'pointer', fontSize: 13, padding: 0,
          }}>
            <LogOut size={14} /> Sign out
          </button>
        </div>
      </aside>
      <main style={{ flex: 1, background: '#f5f5f7', padding: 24, overflow: 'auto' }}>
        <Outlet />
      </main>
    </div>
  );
};

export default Layout;
