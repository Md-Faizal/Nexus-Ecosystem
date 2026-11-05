import React, { createContext, useContext, useState } from 'react';

export const TransitionContext = createContext();

export function TransitionProvider({ children }) {
  // Available states: 'IDLE' | 'EXITING' | 'LOADING' | 'ENTERING'
  const [transitionState, setTransitionState] = useState('IDLE');

  return (
    <TransitionContext.Provider value={{ transitionState, setTransitionState }}>
      {children}
    </TransitionContext.Provider>
  );
}

export function useTransitionState() {
  return useContext(TransitionContext);
}
