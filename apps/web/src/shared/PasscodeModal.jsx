import React from 'react';
import NexusAuthModal from './NexusAuthModal';

/**
 * PasscodeModal — Seamless backward-compatibility wrapper
 * Upgraded from legacy 4-digit PIN to the full Nexus Identity Auth Suite
 */
export default function PasscodeModal({ isOpen, onClose, onSuccess }) {
  return (
    <NexusAuthModal
      isOpen={isOpen}
      onClose={onClose}
      onSuccess={onSuccess}
    />
  );
}
