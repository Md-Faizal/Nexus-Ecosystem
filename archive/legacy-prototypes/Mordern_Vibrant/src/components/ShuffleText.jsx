import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

export default function ShuffleText({ words }) {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setIndex((prev) => (prev + 1) % words.length);
    }, 3000);
    return () => clearInterval(timer);
  }, [words]);

  const currentWord = words[index];

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: { opacity: 1, transition: { staggerChildren: 0.05 } },
    exit: { opacity: 0, transition: { staggerChildren: 0.03, staggerDirection: -1 } }
  };

  const childVariants = {
    hidden: { y: 40, opacity: 0 },
    visible: { y: 0, opacity: 1, transition: { duration: 0.4, ease: "easeOut" } },
    exit: { y: -40, opacity: 0, transition: { duration: 0.3, ease: "easeIn" } }
  };

  return (
    <span style={{ position: 'relative', display: 'inline-flex', overflow: 'hidden', verticalAlign: 'bottom', paddingBottom: '0.1em' }}>
      <AnimatePresence mode="wait">
        <motion.span
          key={index}
          variants={containerVariants}
          initial="hidden"
          animate="visible"
          exit="exit"
          style={{ display: 'inline-flex' }}
        >
          {currentWord.split('').map((char, i) => (
            <motion.span
              key={`${index}-${i}`}
              variants={childVariants}
              style={{ 
                display: 'inline-block', 
                background: 'var(--gradient-accent)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                paddingRight: char === ' ' ? '0.2em' : '0'
              }}
            >
              {char}
            </motion.span>
          ))}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}
