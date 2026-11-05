import React from 'react';
import Header from './Header';
import Footer from './Footer';

export default function Layout({ children }) {
  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      flexDirection: 'column',
      position: 'relative'
    }}>
      <Header />
      
      <main style={{ 
        flexGrow: 1, 
        position: 'relative', 
        zIndex: 10,
        padding: 0
      }}>
        {children}
      </main>
      
      <Footer />
    </div>
  );
}
