import React from 'react';

const services = [
  {
    number: '01',
    title: 'Web applications',
    preview: 'MacOS for Web',
    description: 'Desktop-like browser experiences, application shells, and responsive product interfaces.'
  },
  {
    number: '02',
    title: 'CRM / ERP workflows',
    preview: 'Business system inside MacOS for Web',
    description: 'Structured dashboards, connected data views, and practical workflows for complex products.'
  },
  {
    number: '03',
    title: 'Native Android',
    preview: 'ERP Companion',
    description: 'Kotlin applications with Material 3 foundations and mobile-first interaction patterns.'
  },
  {
    number: '04',
    title: 'Interface systems',
    preview: 'Shared across both products',
    description: 'Reusable components, visual rules, and interaction details that keep a product coherent.'
  }
];

export default function ServicesSection({ onNavigate }) {
  return (
    <section id="section-services" className="w-screen px-6 pb-28 text-white md:px-12 md:pb-44 bg-[#0a0a0e]" aria-labelledby="services-heading">
      <div className="max-w-7xl mx-auto">
        <div className="flex items-end justify-between border-y border-white/15 py-4 text-sm tracking-tight">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#f59e0b]" />
            <h2 id="services-heading" className="font-mono text-xs md:text-sm uppercase tracking-widest text-[#f59e0b]">Services</h2>
          </div>
          <span className="font-mono text-xs md:text-sm text-white/50">CAPABILITIES &amp; ARCHITECTURE</span>
        </div>
        <div className="divide-y divide-white/10">
          {services.map((service) => (
            <div key={service.number} className="grid gap-4 py-8 md:grid-cols-[6rem_1fr_1fr] md:items-baseline group hover:bg-white/[0.02] transition-colors duration-300 px-4 -mx-4 rounded-xl">
              <span className="font-mono text-sm text-[#f59e0b] font-semibold">{service.number}</span>
              <div>
                <h3 className="text-2xl tracking-tight text-white md:text-3xl font-light group-hover:text-[#f59e0b] transition-colors duration-300">{service.title}</h3>
                <p className="mt-2 text-sm text-white/60 md:text-base leading-relaxed">{service.description}</p>
              </div>
              <button
                type="button"
                onClick={() => onNavigate?.('work')}
                className="justify-self-start border-b border-white/30 hover:border-[#f59e0b] pb-1 text-left text-sm tracking-tight text-white/80 hover:text-[#f59e0b] transition-all duration-300 hover:translate-x-1.5 md:justify-self-end font-mono"
              >
                Preview: {service.preview} -&gt;
              </button>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
