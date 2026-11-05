import React, { useState } from 'react';
import { motion, useScroll, useTransform } from 'framer-motion';
import ScrollRevealText from '../components/ScrollRevealText';
import PageTransition from '../components/PageTransition';
import ShuffleText from '../components/ShuffleText';
import ReactiveText from '../components/ReactiveText';
import Background from '../components/Background';
import { ArrowUpRight, Code, Layers, LayoutGrid, Sparkles, Send } from 'lucide-react';

export default function Home() {
  const { scrollYProgress } = useScroll();
  const [formSubmitted, setFormSubmitted] = useState(false);

  const projects = [
    {
      id: 1,
      title: "Aether Design System",
      category: "Design System & UI Kit",
      description: "An enterprise-grade component architecture built for scalability, accessibility, and high performance.",
      tags: ["UI/UX", "Tokens", "Figma", "React"],
      gradient: "linear-gradient(135deg, #0284c7 0%, #38bdf8 100%)"
    },
    {
      id: 2,
      title: "Kenvue Spatial Experience",
      category: "Motion & WebGL Interface",
      description: "Interactive brand narrative featuring real-time 3D elements, fluid transitions, and spatial micro-interactions.",
      tags: ["3D", "Framer Motion", "WebGL", "Creative Dev"],
      gradient: "linear-gradient(135deg, #a855f7 0%, #ec4899 100%)"
    },
    {
      id: 3,
      title: "Fintech Dashboard Pro",
      category: "Product & Data Visualization",
      description: "Real-time analytics platform simplifying complex financial streams into intuitive visual dashboards.",
      tags: ["Dashboard", "Analytics", "React", "TypeScript"],
      gradient: "linear-gradient(135deg, #0f172a 0%, #0284c7 100%)"
    }
  ];

  const skills = [
    "Design Systems", "UI/UX Design", "Motion Graphics", 
    "Micro-Interactions", "Creative Coding", "Framer / React", 
    "Product Strategy", "WebGL / 3D"
  ];

  return (
    <PageTransition>
      <div style={{ position: 'relative', width: '100%' }}>
      {/* Section 1: Hero (INSPIRATION) */}
      <section style={{ minHeight: '100vh', width: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', position: 'relative', overflow: 'hidden' }}>
        {/* Blue and White Grid Background Exclusive to Section 1 */}
        <Background />

        {/* Bottom Left Title */}
        <div style={{ position: 'absolute', bottom: '2rem', left: '2.5rem', zIndex: 2, fontFamily: '"Spline Sans Mono", monospace', fontWeight: 500, lineHeight: '1.3em', fontSize: '0.85rem', letterSpacing: '0.1em', color: 'var(--text-primary)', opacity: 0.6 }}>
          UX DESIGNER
        </div>

        {/* Bottom Scroll to Explore */}
        <div style={{ position: 'absolute', bottom: '2rem', left: '50%', transform: 'translateX(-50%)', zIndex: 2, fontFamily: '"Spline Sans Mono", monospace', fontWeight: 500, lineHeight: '1.3em', fontSize: '0.85rem', letterSpacing: '0.1em', color: 'var(--text-primary)', opacity: 0.6, whiteSpace: 'nowrap' }}>
          (SCROLL TO EXPLORE)
        </div>

        {/* Bottom Right Name */}
        <div style={{ position: 'absolute', bottom: '2rem', right: '2.5rem', zIndex: 2, fontFamily: '"Spline Sans Mono", monospace', fontWeight: 500, lineHeight: '1.3em', fontSize: '0.85rem', letterSpacing: '0.1em', color: 'var(--text-primary)', opacity: 0.6 }}>
          MOHAMMAD FAIZAL
        </div>

        <motion.div 
          initial={{ opacity: 0, y: 50 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, ease: "easeOut" }}
          style={{ maxWidth: '1000px', textAlign: 'center', position: 'relative', zIndex: 2 }}
        >
          <h1 style={{ fontSize: 'clamp(3rem, 6vw, 6rem)', fontWeight: 800, lineHeight: 1.1, letterSpacing: '-0.02em', margin: 0 }}>
            <ReactiveText text="Making things make sense" /> <br/>
            <span style={{ fontFamily: 'Playfair Display, serif', fontStyle: 'italic', fontWeight: 400, color: 'var(--text-primary)', opacity: 0.9 }}>
              Expert design for your 
            </span> <br/>
            <ShuffleText words={['illustrations.', 'experiences.', 'interfaces.']} />
          </h1>
        </motion.div>
      </section>
      
      {/* Section 2: ABOUT (Philosophy & Capabilities) */}
      <section style={{ minHeight: '100vh', width: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', position: 'relative', padding: '6rem 6vw' }}>
        <motion.div 
          initial={{ opacity: 0, y: 40 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: false, amount: 0.3 }}
          transition={{ duration: 0.8 }}
          style={{ maxWidth: '1200px', width: '100%' }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '4rem', alignItems: 'center' }}>
            
            {/* Left: Scroll Reveal Statement */}
            <div>
              <span style={{ fontFamily: '"Spline Sans Mono", monospace', fontSize: '0.85rem', letterSpacing: '0.15em', textTransform: 'uppercase', opacity: 0.7, color: 'var(--text-primary)' }}>
                [ 02 // PHILOSOPHY ]
              </span>
              <div style={{ marginTop: '1.5rem' }}>
                <ScrollRevealText text="Crafting meaningful digital experiences through design system thinking, fluid motion graphics, and intuitive micro-interactions." />
              </div>
            </div>

            {/* Right: Stats & Skills Card Grid */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
              {/* Stat Counters */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
                <motion.div 
                  whileHover={{ y: -5 }}
                  style={{
                    backgroundColor: 'rgba(255, 255, 255, 0.4)',
                    backdropFilter: 'blur(12px)',
                    border: '1px solid rgba(255, 255, 255, 0.6)',
                    borderRadius: '1.5rem',
                    padding: '1.75rem',
                    boxShadow: '0 8px 32px rgba(0,0,0,0.04)'
                  }}
                >
                  <h3 style={{ fontSize: '2.5rem', fontWeight: 800, margin: 0, color: 'var(--text-primary)' }}>5+</h3>
                  <p style={{ margin: '0.5rem 0 0 0', opacity: 0.8, fontSize: '0.9rem', fontFamily: '"Spline Sans Mono", monospace' }}>Years of Experience</p>
                </motion.div>

                <motion.div 
                  whileHover={{ y: -5 }}
                  style={{
                    backgroundColor: 'rgba(255, 255, 255, 0.4)',
                    backdropFilter: 'blur(12px)',
                    border: '1px solid rgba(255, 255, 255, 0.6)',
                    borderRadius: '1.5rem',
                    padding: '1.75rem',
                    boxShadow: '0 8px 32px rgba(0,0,0,0.04)'
                  }}
                >
                  <h3 style={{ fontSize: '2.5rem', fontWeight: 800, margin: 0, color: 'var(--text-primary)' }}>30+</h3>
                  <p style={{ margin: '0.5rem 0 0 0', opacity: 0.8, fontSize: '0.9rem', fontFamily: '"Spline Sans Mono", monospace' }}>Shipped Products</p>
                </motion.div>
              </div>

              {/* Skill Badges Pill Container */}
              <div style={{
                backgroundColor: 'rgba(255, 255, 255, 0.4)',
                backdropFilter: 'blur(12px)',
                border: '1px solid rgba(255, 255, 255, 0.6)',
                borderRadius: '1.5rem',
                padding: '1.75rem',
                boxShadow: '0 8px 32px rgba(0,0,0,0.04)'
              }}>
                <h4 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Sparkles size={18} color="var(--accent-purple)" /> Core Capabilities
                </h4>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.6rem' }}>
                  {skills.map((skill) => (
                    <motion.span 
                      key={skill}
                      whileHover={{ scale: 1.05, backgroundColor: 'rgba(255,255,255,0.9)' }}
                      style={{
                        padding: '0.4rem 0.9rem',
                        backgroundColor: 'rgba(255, 255, 255, 0.6)',
                        borderRadius: '2rem',
                        fontSize: '0.82rem',
                        fontWeight: 600,
                        fontFamily: '"Spline Sans Mono", monospace',
                        border: '1px solid rgba(0,0,0,0.05)',
                        cursor: 'none'
                      }}
                    >
                      {skill}
                    </motion.span>
                  ))}
                </div>
              </div>
            </div>

          </div>
        </motion.div>
      </section>

      {/* Section 3: CASE STUDY (Selected Work) */}
      <section style={{ minHeight: '100vh', width: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', position: 'relative', padding: '6rem 6vw' }}>
        <motion.div 
          initial={{ opacity: 0, y: 40 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: false, amount: 0.2 }}
          transition={{ duration: 0.8 }}
          style={{ maxWidth: '1200px', width: '100%' }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '3rem' }}>
            <div>
              <span style={{ fontFamily: '"Spline Sans Mono", monospace', fontSize: '0.85rem', letterSpacing: '0.15em', textTransform: 'uppercase', opacity: 0.7, color: 'var(--text-primary)' }}>
                [ 03 // CASE STUDY ]
              </span>
              <h2 style={{ fontSize: 'clamp(2.5rem, 5vw, 4rem)', fontWeight: 800, margin: '0.5rem 0 0 0' }}>
                Featured Projects
              </h2>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '2rem', width: '100%' }}>
            {projects.map((proj, idx) => (
              <motion.div 
                key={proj.id}
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: false }}
                transition={{ duration: 0.6, delay: idx * 0.15 }}
                whileHover={{ y: -8 }}
                data-cursor-expand="true"
                style={{
                  backgroundColor: 'rgba(255, 255, 255, 0.45)',
                  backdropFilter: 'blur(16px)',
                  border: '1px solid rgba(255, 255, 255, 0.7)',
                  borderRadius: '1.75rem',
                  padding: '2rem',
                  display: 'flex',
                  flexDirection: 'column',
                  justify: 'space-between',
                  minHeight: '420px',
                  boxShadow: '0 12px 40px rgba(0,0,0,0.05)',
                  cursor: 'none'
                }}
              >
                <div>
                  {/* Image Placeholder with Gradient */}
                  <div style={{ 
                    width: '100%', 
                    height: '180px', 
                    borderRadius: '1.25rem', 
                    background: proj.gradient,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    marginBottom: '1.5rem',
                    boxShadow: 'inset 0 0 20px rgba(0,0,0,0.1)'
                  }}>
                    <Layers size={40} color="white" opacity={0.8} />
                  </div>

                  <span style={{ fontFamily: '"Spline Sans Mono", monospace', fontSize: '0.75rem', fontWeight: 600, opacity: 0.7 }}>
                    {proj.category}
                  </span>
                  <h3 style={{ fontSize: '1.5rem', fontWeight: 800, margin: '0.4rem 0 0.75rem 0', color: 'var(--text-primary)' }}>
                    {proj.title}
                  </h3>
                  <p style={{ opacity: 0.8, fontSize: '0.95rem', lineHeight: 1.5, margin: 0 }}>
                    {proj.description}
                  </p>
                </div>

                <div style={{ marginTop: '2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
                    {proj.tags.slice(0, 2).map((t) => (
                      <span key={t} style={{ fontSize: '0.75rem', fontFamily: '"Spline Sans Mono", monospace', padding: '0.2rem 0.6rem', borderRadius: '1rem', backgroundColor: 'rgba(255,255,255,0.7)' }}>
                        {t}
                      </span>
                    ))}
                  </div>

                  <motion.div 
                    whileHover={{ x: 3, y: -3 }}
                    style={{ display: 'flex', alignItems: 'center', gap: '0.2rem', fontWeight: 700, fontSize: '0.9rem' }}
                  >
                    Explore <ArrowUpRight size={18} />
                  </motion.div>
                </div>
              </motion.div>
            ))}
          </div>
        </motion.div>
      </section>

      {/* Section 4: CONTACT (Get In Touch & Footer) */}
      <section style={{ minHeight: '100vh', width: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', position: 'relative', padding: '6rem 6vw' }}>
        <motion.div 
          initial={{ opacity: 0, y: 40 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: false, amount: 0.3 }}
          transition={{ duration: 0.8 }}
          style={{ maxWidth: '900px', width: '100%', textAlign: 'center' }}
        >
          <span style={{ fontFamily: '"Spline Sans Mono", monospace', fontSize: '0.85rem', letterSpacing: '0.15em', textTransform: 'uppercase', opacity: 0.7, color: 'var(--text-primary)' }}>
            [ 04 // CONTACT ]
          </span>

          <h2 style={{ fontSize: 'clamp(2.5rem, 5.5vw, 4.5rem)', fontWeight: 800, margin: '1rem 0 1.5rem 0', letterSpacing: '-0.02em' }}>
            Let's build something <span style={{ color: 'var(--accent-purple)' }}>extraordinary.</span>
          </h2>
          
          <p style={{ fontSize: '1.15rem', opacity: 0.8, maxWidth: '600px', margin: '0 auto 3rem auto', lineHeight: 1.6 }}>
            Available for select freelance opportunities, design systems consulting & full-time design leadership.
          </p>

          {/* Quick Interactive Contact Form */}
          <div style={{
            backgroundColor: 'rgba(255, 255, 255, 0.45)',
            backdropFilter: 'blur(16px)',
            border: '1px solid rgba(255, 255, 255, 0.7)',
            borderRadius: '2rem',
            padding: '2.5rem',
            boxShadow: '0 12px 40px rgba(0,0,0,0.05)',
            textAlign: 'left'
          }}>
            {formSubmitted ? (
              <motion.div 
                initial={{ opacity: 0, scale: 0.95 }} 
                animate={{ opacity: 1, scale: 1 }}
                style={{ textAlign: 'center', padding: '2rem 0' }}
              >
                <Sparkles size={40} color="var(--accent-purple)" style={{ marginBottom: '1rem' }} />
                <h3 style={{ fontSize: '1.75rem', fontWeight: 800, margin: '0 0 0.5rem 0' }}>Message Sent!</h3>
                <p style={{ opacity: 0.8, margin: 0 }}>Thank you for reaching out. I'll respond within 24 hours.</p>
              </motion.div>
            ) : (
              <form onSubmit={(e) => { e.preventDefault(); setFormSubmitted(true); }} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '0.8rem', fontFamily: '"Spline Sans Mono", monospace', fontWeight: 600, marginBottom: '0.4rem', opacity: 0.8 }}>NAME</label>
                    <input 
                      required 
                      type="text" 
                      placeholder="Your name"
                      style={{
                        width: '100%',
                        padding: '0.85rem 1.25rem',
                        borderRadius: '1rem',
                        border: '1px solid rgba(0,0,0,0.1)',
                        backgroundColor: 'rgba(255,255,255,0.7)',
                        outline: 'none',
                        fontFamily: 'inherit',
                        fontSize: '0.95rem'
                      }} 
                    />
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '0.8rem', fontFamily: '"Spline Sans Mono", monospace', fontWeight: 600, marginBottom: '0.4rem', opacity: 0.8 }}>EMAIL</label>
                    <input 
                      required 
                      type="email" 
                      placeholder="your@email.com"
                      style={{
                        width: '100%',
                        padding: '0.85rem 1.25rem',
                        borderRadius: '1rem',
                        border: '1px solid rgba(0,0,0,0.1)',
                        backgroundColor: 'rgba(255,255,255,0.7)',
                        outline: 'none',
                        fontFamily: 'inherit',
                        fontSize: '0.95rem'
                      }} 
                    />
                  </div>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontFamily: '"Spline Sans Mono", monospace', fontWeight: 600, marginBottom: '0.4rem', opacity: 0.8 }}>MESSAGE</label>
                  <textarea 
                    required 
                    rows={3} 
                    placeholder="Tell me about your project..."
                    style={{
                      width: '100%',
                      padding: '0.85rem 1.25rem',
                      borderRadius: '1rem',
                      border: '1px solid rgba(0,0,0,0.1)',
                      backgroundColor: 'rgba(255,255,255,0.7)',
                      outline: 'none',
                      fontFamily: 'inherit',
                      fontSize: '0.95rem',
                      resize: 'none'
                    }} 
                  />
                </div>

                <motion.button 
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  type="submit"
                  data-cursor-expand="true"
                  style={{
                    padding: '1rem 2rem',
                    backgroundColor: 'var(--text-primary)',
                    color: 'white',
                    border: 'none',
                    borderRadius: '1.25rem',
                    fontWeight: 700,
                    fontSize: '1rem',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '0.5rem',
                    cursor: 'none',
                    marginTop: '0.5rem'
                  }}
                >
                  Send Message <Send size={18} />
                </motion.button>
              </form>
            )}
          </div>
        </motion.div>

        {/* Section 4 Footer Bar */}
        <div style={{ position: 'absolute', bottom: '1.5rem', left: '2rem', right: '2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontFamily: '"Spline Sans Mono", monospace', fontSize: '0.8rem', opacity: 0.7 }}>
          <span>© 2026 MOHAMMAD FAIZAL</span>
          <span>ALL RIGHTS RESERVED</span>
        </div>
      </section>
    </div>
    </PageTransition>
  );
}
