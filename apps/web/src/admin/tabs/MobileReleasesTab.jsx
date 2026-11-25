import React, { useState, useEffect, useCallback } from 'react';
import {
  Smartphone,
  Download,
  ShieldCheck,
  Copy,
  RefreshCw,
  ExternalLink,
  Clock,
  Tag,
  CheckCircle2,
  AlertTriangle
} from 'lucide-react';

export default function MobileReleasesTab({ showToast }) {
  const [releases, setReleases] = useState([]);
  const [githubReleases, setGithubReleases] = useState([]);
  const [loading, setLoading] = useState(true);
  const [copiedSha, setCopiedSha] = useState(null);

  const loadReleases = useCallback(async () => {
    setLoading(true);
    try {
      const [localRes, ghRes] = await Promise.allSettled([
        fetch(`/releases/mobile-releases.json?t=${Date.now()}`),
        fetch('https://api.github.com/repos/Md-Faizal/Nexus-Ecosystem/releases?per_page=10')
      ]);

      if (localRes.status === 'fulfilled' && localRes.value.ok) {
        const data = await localRes.value.json();
        setReleases(Array.isArray(data.releases) ? data.releases : []);
      }

      if (ghRes.status === 'fulfilled' && ghRes.value.ok) {
        const ghData = await ghRes.value.json();
        if (Array.isArray(ghData)) {
          const mobileOnly = ghData.filter(
            (r) => r.tag_name?.startsWith('mobile-') || r.assets?.some((a) => a.name.endsWith('.apk'))
          );
          setGithubReleases(mobileOnly);
        }
      }
    } catch (err) {
      showToast?.('Failed to fetch mobile releases', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    loadReleases();
  }, [loadReleases]);

  const copySha = (sha) => {
    navigator.clipboard.writeText(sha);
    setCopiedSha(sha);
    showToast?.('SHA-256 copied to clipboard', 'success');
    setTimeout(() => setCopiedSha(null), 2000);
  };

  const latest = releases[0];

  return (
    <div className="space-y-6">
      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#0a0a0e] border border-white/10 rounded-2xl p-5">
        <div className="flex items-center gap-3.5">
          <div className="w-11 h-11 rounded-xl bg-[#89ff00]/10 border border-[#89ff00]/30 flex items-center justify-center">
            <Smartphone className="w-5 h-5 text-[#89ff00]" />
          </div>
          <div>
            <h2 className="text-lg font-semibold text-white tracking-tight">
              Nexus Mobile Releases (Android APK)
            </h2>
            <p className="text-xs font-mono text-neutral-400">
              Rust-Core + Material 3 • Self-hosted OTA &amp; GitHub Actions CI
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <a
            href="https://github.com/Md-Faizal/Nexus-Ecosystem/actions/workflows/mobile-ci.yml"
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-mono text-neutral-200 transition-all"
          >
            <ExternalLink className="w-3.5 h-3.5 text-[#89ff00]" />
            CI Builds
          </a>
          <button
            onClick={loadReleases}
            disabled={loading}
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-[#89ff00] hover:bg-[#9dff2e] text-black text-xs font-mono font-bold transition-all cursor-pointer disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* Latest Release Hero */}
      {latest && (
        <div className="bg-gradient-to-br from-[#0e1318] to-[#08090c] border border-[#89ff00]/30 rounded-2xl p-6 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <span className="px-2.5 py-1 rounded-full bg-[#89ff00]/15 border border-[#89ff00]/40 text-[#89ff00] text-[11px] font-mono font-bold uppercase">
                Latest Release
              </span>
              <span className="text-xl font-bold text-white font-mono">
                v{latest.version_name}
              </span>
              <span className="text-xs font-mono text-neutral-400">
                (code {latest.version_code})
              </span>
            </div>

            {latest.sha256 && latest.sha256 !== 'pending-first-ci-build' ? (
              <a
                href={latest.apk_url}
                download
                className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#89ff00] hover:bg-[#9dff2e] text-black text-xs font-mono font-bold transition-all"
              >
                <Download className="w-4 h-4" />
                Download APK
              </a>
            ) : (
              <a
                href="https://github.com/Md-Faizal/Nexus-Ecosystem/actions/workflows/mobile-ci.yml"
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-amber-500/15 border border-amber-500/40 text-amber-300 text-xs font-mono font-semibold"
              >
                <AlertTriangle className="w-4 h-4" />
                Grab First Build from CI Actions
              </a>
            )}
          </div>

          <p className="text-sm text-neutral-300 leading-relaxed">
            {latest.release_notes}
          </p>

          <div className="flex flex-wrap items-center gap-4 pt-2 border-t border-white/10 text-xs font-mono text-neutral-400">
            <span className="flex items-center gap-1.5">
              <Clock className="w-3.5 h-3.5 text-neutral-500" />
              {latest.published_at}
            </span>
            <button
              onClick={() => copySha(latest.sha256)}
              className="flex items-center gap-1.5 hover:text-white transition-colors cursor-pointer"
              title="Copy SHA-256"
            >
              <ShieldCheck className="w-3.5 h-3.5 text-[#89ff00]" />
              <span>SHA-256: {latest.sha256?.slice(0, 18)}...</span>
              {copiedSha === latest.sha256 ? (
                <CheckCircle2 className="w-3.5 h-3.5 text-[#89ff00]" />
              ) : (
                <Copy className="w-3.5 h-3.5" />
              )}
            </button>
          </div>
        </div>
      )}

      {/* GitHub Releases (Tagged APK Releases) */}
      {githubReleases.length > 0 && (
        <div className="bg-[#0a0a0e] border border-white/10 rounded-2xl p-5 space-y-3">
          <h3 className="text-xs font-mono font-bold uppercase tracking-wider text-neutral-400">
            GitHub Tagged APK Releases
          </h3>
          <div className="space-y-3">
            {githubReleases.map((rel) => {
              const apkAsset = rel.assets?.find((a) => a.name.endsWith('.apk'));
              return (
                <div
                  key={rel.id}
                  className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-xl bg-white/[0.03] border border-white/10"
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <Tag className="w-3.5 h-3.5 text-[#89ff00]" />
                      <span className="font-mono text-sm font-bold text-white">
                        {rel.name || rel.tag_name}
                      </span>
                      <span className="text-[11px] font-mono text-neutral-500">
                        {new Date(rel.published_at).toLocaleString()}
                      </span>
                    </div>
                    {rel.body && (
                      <p className="text-xs text-neutral-400 whitespace-pre-line line-clamp-3">
                        {rel.body}
                      </p>
                    )}
                  </div>
                  {apkAsset && (
                    <a
                      href={apkAsset.browser_download_url}
                      className="shrink-0 flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white/10 hover:bg-[#89ff00] hover:text-black text-white text-xs font-mono font-semibold transition-all"
                    >
                      <Download className="w-3.5 h-3.5" />
                      {apkAsset.name}
                    </a>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Self-Hosted Release Changelog */}
      <div className="bg-[#0a0a0e] border border-white/10 rounded-2xl p-5 space-y-3">
        <h3 className="text-xs font-mono font-bold uppercase tracking-wider text-neutral-400">
          Self-Hosted Release History (/releases/mobile-releases.json)
        </h3>
        <div className="space-y-2.5">
          {releases.map((item) => (
            <div
              key={`${item.version_code}-${item.published_at}`}
              className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-xl bg-white/[0.02] border border-white/10"
            >
              <div className="space-y-1">
                <div className="flex items-center gap-2.5">
                  <span className="font-mono text-sm font-bold text-white">
                    v{item.version_name}
                  </span>
                  <span className="px-2 py-0.5 rounded bg-white/5 text-[10px] font-mono text-neutral-400 uppercase">
                    code {item.version_code} • {item.build_type || 'release'}
                  </span>
                  <span className="text-[11px] font-mono text-neutral-500">
                    {item.published_at}
                  </span>
                </div>
                <p className="text-xs text-neutral-300">{item.release_notes}</p>
              </div>
              {item.sha256 && item.sha256 !== 'pending-first-ci-build' && (
                <a
                  href={item.apk_url}
                  download
                  className="shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-mono text-neutral-200 transition-all"
                >
                  <Download className="w-3.5 h-3.5 text-[#89ff00]" />
                  APK
                </a>
              )}
            </div>
          ))}
          {!loading && releases.length === 0 && (
            <p className="text-xs font-mono text-neutral-500 py-4 text-center">
              No self-hosted mobile releases recorded yet.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
