import React, { useState } from 'react';
import ReactDOM from 'react-dom/client';
import Admin from './Admin';
import PasscodeModal from '../shared/PasscodeModal';
import './admin.css';

function StandaloneAdminApp() {
  // Check if valid Nexus JWT token or legacy admin PIN exists
  const [authenticated, setAuthenticated] = useState(() => {
    const token = localStorage.getItem('nexus_auth_token');
    const legacyPin = localStorage.getItem('faizal_admin_pin');
    return Boolean(token || (legacyPin && /^\d{4}$/.test(legacyPin)));
  });

  const handleSuccess = () => {
    setAuthenticated(true);
  };

  const handleLogout = () => {
    localStorage.removeItem('nexus_auth_token');
    localStorage.removeItem('nexus_auth_user');
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
