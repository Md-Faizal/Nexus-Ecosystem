import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { nexusAuth } from '../sdk/nexus-auth';
import { NexusMonogramIcon } from './NexusSignInButton';

export default function NexusAuthModal({ isOpen, onClose, onSuccess }) {
  const [activeTab, setActiveTab] = useState('otp'); // 'otp' | 'magic' | 'password' | 'recovery' | 'legacy_pin'
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [identifier, setIdentifier] = useState('');
  
  // OTP State
  const [otpSent, setOtpSent] = useState(false);
  const [otpDigits, setOtpDigits] = useState(['', '', '', '', '', '']);
  const otpRefs = [useRef(null), useRef(null), useRef(null), useRef(null), useRef(null), useRef(null)];

  // Legacy PIN State
  const [pinDigits, setPinDigits] = useState(['', '', '', '']);
  const pinRefs = [useRef(null), useRef(null), useRef(null), useRef(null)];

  // Recovery State
  const [recoveryIdentifier, setRecoveryIdentifier] = useState('');
  const [securityAnswer, setSecurityAnswer] = useState('');
  const [newPassword, setNewPassword] = useState('');

  // UI status
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);
  const [countdown, setCountdown] = useState(0);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  useEffect(() => {
    if (isOpen) {
      setErrorMsg(null);
      setSuccessMsg(null);
      setLoading(false);
    }
  }, [isOpen, activeTab]);

  if (!isOpen) return null;

  const handleAuthSuccess = (token, user) => {
    setSuccessMsg('Authentication confirmed!');
    setTimeout(() => {
      onSuccess ? onSuccess(token, user) : (window.location.href = '/admin.html');
    }, 600);
  };

  // ── OTP Handlers ────────────────────────────────────────────────────────────

  const handleSendOtp = async (e) => {
    e?.preventDefault();
    if (!email.trim() || !email.includes('@')) {
      setErrorMsg('Please enter a valid email address.');
      return;
    }
    setLoading(true);
    setErrorMsg(null);
    try {
      await nexusAuth.sendOtp(email.trim());
      setOtpSent(true);
      setCountdown(60);
      setSuccessMsg('6-digit code dispatched to your email.');
      setTimeout(() => otpRefs[0].current?.focus(), 200);
    } catch (err) {
      setErrorMsg(err.message || 'Failed to send verification code.');
    } finally {
      setLoading(false);
    }
  };

  const handleOtpChange = (index, val) => {
    const clean = val.replace(/\D/g, '');
    if (val.length > 1) {
      // Paste handling
      const pasteDigits = val.replace(/\D/g, '').slice(0, 6).split('');
      const next = [...otpDigits];
      pasteDigits.forEach((d, i) => { if (i < 6) next[i] = d; });
      setOtpDigits(next);
      if (pasteDigits.length === 6) verifyOtpCode(next.join(''));
      return;
    }

    const next = [...otpDigits];
    next[index] = clean;
    setOtpDigits(next);

    if (clean && index < 5) {
      otpRefs[index + 1].current?.focus();
    }
    if (next.every((d) => d !== '')) {
      verifyOtpCode(next.join(''));
    }
  };

  const handleOtpKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !otpDigits[index] && index > 0) {
      otpRefs[index - 1].current?.focus();
    }
  };

  const verifyOtpCode = async (code) => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const res = await nexusAuth.verifyOtp(email.trim(), code, 'Web Client');
      if (res.success && res.token) {
        handleAuthSuccess(res.token, res.user);
      }
    } catch (err) {
      setErrorMsg(err.message || 'Invalid or expired verification code.');
      setOtpDigits(['', '', '', '', '', '']);
      otpRefs[0].current?.focus();
    } finally {
      setLoading(false);
    }
  };

  // ── Magic Link Handler ──────────────────────────────────────────────────────

  const handleSendMagicLink = async (e) => {
    e.preventDefault();
    if (!email.trim() || !email.includes('@')) {
      setErrorMsg('Please enter a valid email address.');
      return;
    }
    setLoading(true);
    setErrorMsg(null);
    try {
      await nexusAuth.sendMagicLink(email.trim());
      setSuccessMsg('Magic link dispatched! Check your email inbox to sign in.');
      setCountdown(60);
    } catch (err) {
      setErrorMsg(err.message || 'Failed to dispatch magic link.');
    } finally {
      setLoading(false);
    }
  };

  // ── Password Login Handler ──────────────────────────────────────────────────

  const handlePasswordLogin = async (e) => {
    e.preventDefault();
    if (!identifier.trim() || !password) {
      setErrorMsg('Please enter your email/username and password.');
      return;
    }
    setLoading(true);
    setErrorMsg(null);
    try {
      const res = await nexusAuth.login(identifier.trim(), password, 'Web Admin');
      if (res.success && res.token) {
        handleAuthSuccess(res.token, res.user);
      }
    } catch (err) {
      setErrorMsg(err.message || 'Invalid username/email or password.');
    } finally {
      setLoading(false);
    }
  };

  // ── Security Question Recovery ──────────────────────────────────────────────

  const handleSecurityRecovery = async (e) => {
    e.preventDefault();
    if (!recoveryIdentifier.trim() || !securityAnswer.trim()) {
      setErrorMsg('Please provide your username/email and answer.');
      return;
    }
    setLoading(true);
    setErrorMsg(null);
    try {
      const res = await nexusAuth.recoverWithSecurityQuestion(
        recoveryIdentifier.trim(),
        securityAnswer.trim(),
        newPassword.trim() || null
      );
      if (res.success && res.token) {
        handleAuthSuccess(res.token, res.user);
      }
    } catch (err) {
      setErrorMsg(err.message || 'Verification failed. Please check your answer.');
    } finally {
      setLoading(false);
    }
  };

  // ── Legacy PIN Fallback (During Migration) ───────────────────────────────────

  const handleLegacyPinChange = async (index, val) => {
    const clean = val.replace(/\D/g, '');
    const next = [...pinDigits];
    next[index] = clean;
    setPinDigits(next);

    if (clean && index < 3) {
      pinRefs[index + 1].current?.focus();
    }
    if (next.every((d) => d !== '')) {
      const pinCode = next.join('');
      setLoading(true);
      try {
        const res = await fetch('/api/verify-pin', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ pin: pinCode })
        });
        const data = await res.json();
        if (data && data.success) {
          localStorage.setItem('faizal_admin_pin', pinCode);
          handleAuthSuccess(pinCode, { full_name: 'Administrator' });
        } else {
          setErrorMsg('Invalid legacy PIN.');
          setPinDigits(['', '', '', '']);
          pinRefs[0].current?.focus();
        }
      } catch (_) {
        setErrorMsg('Verification error.');
      } finally {
        setLoading(false);
      }
    }
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 15 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 15 }}
          transition={{ type: 'spring', damping: 25, stiffness: 300 }}
          className="relative w-full max-w-md bg-[#0d0e12]/95 border border-white/10 rounded-2xl p-7 shadow-2xl text-white overflow-hidden"
        >
          {/* Subtle Ambient Glow */}
          <div className="absolute -top-24 -left-24 w-48 h-48 bg-[#89ff00]/10 rounded-full blur-3xl pointer-events-none" />
          <div className="absolute -bottom-24 -right-24 w-48 h-48 bg-[#00e5ff]/10 rounded-full blur-3xl pointer-events-none" />

          {/* Header */}
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-3">
              <NexusMonogramIcon className="w-7 h-7" glow />
              <div>
                <h2 className="text-lg font-bold tracking-tight text-white">Nexus Identity</h2>
                <p className="text-xs text-neutral-400">Unified Fleet Authentication</p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="text-neutral-500 hover:text-white p-1 rounded-lg transition-colors text-sm"
            >
              ✕
            </button>
          </div>

          {/* Navigation Tabs */}
          <div className="flex border-b border-white/10 mb-6 gap-4 text-xs font-semibold uppercase tracking-wider text-neutral-400">
            <button
              onClick={() => { setActiveTab('otp'); setOtpSent(false); }}
              className={`pb-2 transition-colors relative ${activeTab === 'otp' ? 'text-[#89ff00]' : 'hover:text-white'}`}
            >
              Email OTP
              {activeTab === 'otp' && <motion.div layoutId="tabUnderline" className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#89ff00]" />}
            </button>
            <button
              onClick={() => setActiveTab('magic')}
              className={`pb-2 transition-colors relative ${activeTab === 'magic' ? 'text-[#00e5ff]' : 'hover:text-white'}`}
            >
              Magic Link
              {activeTab === 'magic' && <motion.div layoutId="tabUnderline" className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#00e5ff]" />}
            </button>
            <button
              onClick={() => setActiveTab('password')}
              className={`pb-2 transition-colors relative ${activeTab === 'password' ? 'text-white' : 'hover:text-white'}`}
            >
              Password
              {activeTab === 'password' && <motion.div layoutId="tabUnderline" className="absolute bottom-0 left-0 right-0 h-0.5 bg-white" />}
            </button>
            <button
              onClick={() => setActiveTab('legacy_pin')}
              className={`pb-2 transition-colors relative ${activeTab === 'legacy_pin' ? 'text-amber-400' : 'hover:text-white'}`}
            >
              PIN
              {activeTab === 'legacy_pin' && <motion.div layoutId="tabUnderline" className="absolute bottom-0 left-0 right-0 h-0.5 bg-amber-400" />}
            </button>
          </div>

          {/* Notifications */}
          {errorMsg && (
            <div className="mb-4 p-3 bg-red-950/50 border border-red-500/40 rounded-xl text-xs text-red-200">
              {errorMsg}
            </div>
          )}
          {successMsg && (
            <div className="mb-4 p-3 bg-emerald-950/50 border border-emerald-500/40 rounded-xl text-xs text-emerald-200">
              {successMsg}
            </div>
          )}

          {/* TAB 1: 6-DIGIT EMAIL OTP */}
          {activeTab === 'otp' && (
            <div>
              {!otpSent ? (
                <form onSubmit={handleSendOtp} className="space-y-4">
                  <p className="text-xs text-neutral-400 leading-relaxed">
                    Enter your email to receive a secure, single-use 6-digit verification code.
                  </p>
                  <div>
                    <label className="block text-xs font-medium text-neutral-300 mb-1.5">Email Address</label>
                    <input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="faizal@faizal.uk"
                      required
                      className="w-full px-3.5 py-2.5 bg-neutral-900 border border-white/10 rounded-xl text-sm text-white focus:outline-none focus:border-[#89ff00] transition-colors"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={loading}
                    className="w-full py-2.5 bg-[#89ff00] text-black font-semibold text-sm rounded-xl hover:bg-[#7ce600] active:scale-[0.99] transition-all disabled:opacity-50"
                  >
                    {loading ? 'Dispatching Code...' : 'Send Verification Code →'}
                  </button>
                </form>
              ) : (
                <div className="space-y-5 text-center">
                  <p className="text-xs text-neutral-400">
                    Enter the 6-digit code sent to <strong className="text-white">{email}</strong>
                  </p>
                  <div className="flex justify-center gap-2">
                    {otpDigits.map((digit, i) => (
                      <input
                        key={i}
                        ref={otpRefs[i]}
                        type="text"
                        inputMode="numeric"
                        maxLength={1}
                        value={digit}
                        onChange={(e) => handleOtpChange(i, e.target.value)}
                        onKeyDown={(e) => handleOtpKeyDown(i, e)}
                        className="w-11 h-12 text-center text-xl font-bold bg-neutral-900 border border-white/15 rounded-xl text-[#89ff00] focus:border-[#89ff00] focus:outline-none transition-all"
                      />
                    ))}
                  </div>
                  <div className="flex justify-between items-center text-xs text-neutral-500 pt-2">
                    <button
                      type="button"
                      onClick={() => setOtpSent(false)}
                      className="hover:text-white transition-colors"
                    >
                      Change Email
                    </button>
                    <button
                      type="button"
                      disabled={countdown > 0 || loading}
                      onClick={handleSendOtp}
                      className="text-[#89ff00] hover:underline disabled:opacity-40"
                    >
                      {countdown > 0 ? `Resend in ${countdown}s` : 'Resend Code'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* TAB 2: MAGIC LINK */}
          {activeTab === 'magic' && (
            <form onSubmit={handleSendMagicLink} className="space-y-4">
              <p className="text-xs text-neutral-400 leading-relaxed">
                Sign in with a one-click authentication link dispatched to your inbox. No passwords needed.
              </p>
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-1.5">Email Address</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="faizal@faizal.uk"
                  required
                  className="w-full px-3.5 py-2.5 bg-neutral-900 border border-white/10 rounded-xl text-sm text-white focus:outline-none focus:border-[#00e5ff] transition-colors"
                />
              </div>
              <button
                type="submit"
                disabled={loading || countdown > 0}
                className="w-full py-2.5 bg-[#00e5ff] text-black font-semibold text-sm rounded-xl hover:bg-[#00cbe2] active:scale-[0.99] transition-all disabled:opacity-50"
              >
                {loading ? 'Dispatching...' : countdown > 0 ? `Resend Link in ${countdown}s` : 'Send Magic Link →'}
              </button>
            </form>
          )}

          {/* TAB 3: PASSWORD LOGIN */}
          {activeTab === 'password' && (
            <form onSubmit={handlePasswordLogin} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-1.5">Username or Email</label>
                <input
                  type="text"
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  placeholder="faizal or me@faizal.uk"
                  required
                  className="w-full px-3.5 py-2.5 bg-neutral-900 border border-white/10 rounded-xl text-sm text-white focus:outline-none focus:border-white transition-colors"
                />
              </div>
              <div>
                <div className="flex justify-between items-center mb-1.5">
                  <label className="text-xs font-medium text-neutral-300">Password</label>
                  <button
                    type="button"
                    onClick={() => setActiveTab('recovery')}
                    className="text-xs text-neutral-400 hover:text-white hover:underline"
                  >
                    Forgot / Recover?
                  </button>
                </div>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  required
                  className="w-full px-3.5 py-2.5 bg-neutral-900 border border-white/10 rounded-xl text-sm text-white focus:outline-none focus:border-white transition-colors"
                />
              </div>
              <button
                type="submit"
                disabled={loading}
                className="w-full py-2.5 bg-white text-black font-semibold text-sm rounded-xl hover:bg-neutral-200 active:scale-[0.99] transition-all disabled:opacity-50"
              >
                {loading ? 'Authenticating...' : 'Sign In with Password →'}
              </button>
            </form>
          )}

          {/* TAB 4: RECOVERY VIA SECURITY QUESTION */}
          {activeTab === 'recovery' && (
            <form onSubmit={handleSecurityRecovery} className="space-y-4">
              <div className="flex justify-between items-center">
                <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-300">Account Recovery</h3>
                <button
                  type="button"
                  onClick={() => setActiveTab('password')}
                  className="text-xs text-neutral-500 hover:text-white"
                >
                  Back to Login
                </button>
              </div>
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-1.5">Username or Email</label>
                <input
                  type="text"
                  value={recoveryIdentifier}
                  onChange={(e) => setRecoveryIdentifier(e.target.value)}
                  placeholder="faizal"
                  required
                  className="w-full px-3.5 py-2.5 bg-neutral-900 border border-white/10 rounded-xl text-sm text-white focus:outline-none focus:border-purple-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-1.5">Security Question Answer</label>
                <input
                  type="text"
                  value={securityAnswer}
                  onChange={(e) => setSecurityAnswer(e.target.value)}
                  placeholder="Your configured security answer"
                  required
                  className="w-full px-3.5 py-2.5 bg-neutral-900 border border-white/10 rounded-xl text-sm text-white focus:outline-none focus:border-purple-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-1.5">New Password (Optional)</label>
                <input
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="At least 8 characters"
                  className="w-full px-3.5 py-2.5 bg-neutral-900 border border-white/10 rounded-xl text-sm text-white focus:outline-none focus:border-purple-400"
                />
              </div>
              <button
                type="submit"
                disabled={loading}
                className="w-full py-2.5 bg-purple-600 text-white font-semibold text-sm rounded-xl hover:bg-purple-500 active:scale-[0.99] transition-all disabled:opacity-50"
              >
                {loading ? 'Verifying...' : 'Recover Account →'}
              </button>
            </form>
          )}

          {/* TAB 5: LEGACY 4-DIGIT PIN */}
          {activeTab === 'legacy_pin' && (
            <div className="space-y-5 text-center">
              <p className="text-xs text-neutral-400">
                Legacy 4-digit PIN for migration compatibility.
              </p>
              <div className="flex justify-center gap-3">
                {pinDigits.map((digit, i) => (
                  <input
                    key={i}
                    ref={pinRefs[i]}
                    type="password"
                    inputMode="numeric"
                    maxLength={1}
                    value={digit}
                    onChange={(e) => handleLegacyPinChange(i, e.target.value)}
                    className="w-12 h-14 text-center text-2xl font-bold bg-neutral-900 border border-amber-500/30 rounded-xl text-amber-400 focus:border-amber-400 focus:outline-none transition-all"
                  />
                ))}
              </div>
            </div>
          )}

          {/* Social Provider Buttons */}
          <div className="mt-6 pt-5 border-t border-white/10">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-neutral-500 text-center mb-3">
              Or continue with Identity Providers
            </p>
            <div className="grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => {
                  setErrorMsg('Google Sign-In ready: configure client credentials in settings.');
                }}
                className="flex items-center justify-center gap-2 py-2 px-3 bg-neutral-900 hover:bg-neutral-800 border border-white/10 rounded-xl text-xs font-medium text-white transition-all"
              >
                <svg className="w-4 h-4" viewBox="0 0 24 24">
                  <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                  <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                  <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
                  <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
                </svg>
                Google
              </button>
              <button
                type="button"
                onClick={() => {
                  setErrorMsg('Meta Sign-In ready: configure client credentials in settings.');
                }}
                className="flex items-center justify-center gap-2 py-2 px-3 bg-neutral-900 hover:bg-neutral-800 border border-white/10 rounded-xl text-xs font-medium text-white transition-all"
              >
                <svg className="w-4 h-4" viewBox="0 0 24 24" fill="#1877F2">
                  <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z" />
                </svg>
                Meta
              </button>
            </div>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}

