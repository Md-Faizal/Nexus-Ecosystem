import React, { useState } from 'react';
import ReactDOM from 'react-dom/client';
import Admin from './pages/Admin/Admin';
import PasscodeModal from './components/common/PasscodeModal';
import './admin.css';

function StandaloneAdminApp() {
  // Check if valid 4-digit PIN was already entered
  const [authenticated, setAuthenticated] = useState(() => {
    const saved = localStorage.getItem('faizal_admin_pin');
    return Boolean(saved && /^\d{4}$/.test(saved));
  });

  const handleSuccess = () => {
    setAuthenticated(true);
  };

  const handleLogout = () => {
    localStorage.removeItem('faizal_admin_pin');
    setAuthenticated(false);
  };

  if (!authenticated) {
    return (
      <div className="min-h-screen bg-[#050507] text-white flex flex-col items-center justify-center p-4 relative overflow-hidden font-sans">
        <PasscodeModal
          isOpen={true}
          onClose={() => {
            window.location.href = '/';
          }}
          onSuccess={handleSuccess}
        />
      </div>
    );
  }

  return <Admin onExit={handleLogout} />;
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <StandaloneAdminApp />
  </React.StrictMode>
);
