// Smooth section animations
const observer = new IntersectionObserver(
  (entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('active');
        observer.unobserve(entry.target);
      }
    });
  },
  { threshold: 0.15 }
);

document.querySelectorAll('[data-animate]').forEach(el => observer.observe(el));

// Parallax effect
window.addEventListener('scroll', () => {
  document.querySelectorAll('[data-bg]').forEach(section => {
    const speed = 0.4;
    const offset = window.scrollY * speed;
    section.style.backgroundPositionY = `calc(50% + ${offset}px)`;
  });
});
