import React, { useRef } from 'react';
import { motion, useScroll, useTransform } from 'framer-motion';

const Word = ({ children, progress, range }) => {
  const opacity = useTransform(progress, range, [0.2, 1]);
  return (
    <span style={{ position: 'relative', display: 'inline-block', marginRight: '0.25em', marginTop: '0.25em' }}>
      <span style={{ position: 'absolute', opacity: 0.2 }}>{children}</span>
      <motion.span style={{ opacity: opacity }}>{children}</motion.span>
    </span>
  );
};

export default function ScrollRevealText({ text }) {
  const element = useRef(null);
  const { scrollYProgress } = useScroll({
    target: element,
    offset: ['start 0.9', 'start 0.25']
  });

  const words = text.split(" ");
  
  return (
    <p 
      ref={element} 
      style={{
        fontSize: 'clamp(2rem, 4vw, 4rem)',
        fontWeight: 700,
        lineHeight: 1.2,
        maxWidth: '1200px',
        display: 'flex',
        flexWrap: 'wrap'
      }}
    >
      {words.map((word, i) => {
        const start = i / words.length;
        const end = start + (1 / words.length);
        return (
          <Word key={i} progress={scrollYProgress} range={[start, end]}>
            {word}
          </Word>
        );
      })}
    </p>
  );
}
