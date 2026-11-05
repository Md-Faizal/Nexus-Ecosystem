import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

export default function ReactiveText({ text }) {
  const [mousePosition, setMousePosition] = useState({ x: 0, y: 0 });

  useEffect(() => {
    const updateMousePosition = (e) => {
      setMousePosition({ x: e.clientX, y: e.clientY });
    };
    window.addEventListener('mousemove', updateMousePosition);
    return () => window.removeEventListener('mousemove', updateMousePosition);
  }, []);

  const words = text.split(" ");

  return (
    <span style={{ display: 'inline-flex', flexWrap: 'wrap', justifyContent: 'center' }}>
      {words.map((word, wordIdx) => (
        <span key={wordIdx} style={{ display: 'inline-flex', marginRight: '0.4em' }}>
          {word.split("").map((letter, letterIdx) => (
            <ReactiveLetter key={letterIdx} letter={letter} mouseX={mousePosition.x} mouseY={mousePosition.y} />
          ))}
        </span>
      ))}
    </span>
  );
}

const ReactiveLetter = ({ letter, mouseX, mouseY }) => {
  const [distance, setDistance] = useState(1000);
  const ref = React.useRef(null);

  useEffect(() => {
    if (!ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    
    const dist = Math.sqrt(
      Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2)
    );
    setDistance(dist);
  }, [mouseX, mouseY]);

  // Scale up slightly if the cursor is very near (one letter at a time)
  const scale = distance < 50 ? 1 + ((50 - distance) / 50) * 0.4 : 1;
  const y = distance < 50 ? -((50 - distance) / 50) * 12 : 0;
  const fontWeight = distance < 50 ? 900 : 800;
  const color = distance < 50 ? 'var(--accent-purple, #a855f7)' : 'inherit';

  return (
    <motion.span
      ref={ref}
      animate={{ scale, y, fontWeight, color }}
      transition={{ type: 'spring', stiffness: 400, damping: 25 }}
      style={{ display: 'inline-block', transformOrigin: 'bottom center' }}
    >
      {letter}
    </motion.span>
  );
};
