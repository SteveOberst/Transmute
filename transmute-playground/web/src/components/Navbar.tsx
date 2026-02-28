'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useState } from 'react'
import { fetchHealth } from '@/lib/api'
import { useTheme } from '@/hooks/useTheme'

const navItems = [
  { href: '/', label: 'Transform' },
  { href: '/inspect', label: 'Inspect' },
  { href: '/formats', label: 'Formats' },
  { href: '/plugins', label: 'Plugins' },
]

export default function Navbar() {
  const pathname = usePathname()
  const [healthy, setHealthy] = useState<boolean | null>(null)
  const { isDark, toggleTheme } = useTheme()

  useEffect(() => {
    fetchHealth()
      .then(() => setHealthy(true))
      .catch(() => setHealthy(false))
  }, [])

  return (
    <nav className="
      sticky top-0 z-50
      border-b border-[var(--surface-3)]/60
      bg-[var(--surface-0)]/80 backdrop-blur-xl
    ">
      <div className="relative max-w-7xl mx-auto px-6 h-14 flex items-center">
        {/* Brand — left */}
        <Link href="/" className="flex items-center gap-3 group shrink-0">
          <div className="
            w-8 h-8 rounded-lg bg-[var(--accent)] flex items-center justify-center
            text-white font-bold text-sm font-display
            group-hover:glow-accent-sm transition-shadow duration-300
          ">
            T
          </div>
          <span className="font-display text-lg text-white tracking-tight">
            Transmute
          </span>
          <span className="
            text-[10px] font-mono text-[#555566] uppercase tracking-widest
            hidden sm:inline
          ">
            Playground
          </span>
        </Link>

        {/* Navigation — absolutely centered */}
        <div className="absolute left-1/2 -translate-x-1/2 flex items-center gap-1">
          {navItems.map((item) => {
            const active = item.href === '/'
              ? pathname === '/'
              : pathname.startsWith(item.href)
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`
                  px-3 py-1.5 rounded-lg text-sm transition-all duration-200
                  ${
                    active
                      ? 'text-[var(--accent)] bg-[var(--accent)]/8 font-medium'
                      : 'text-[#888898] hover:text-white hover:bg-[var(--surface-2)]'
                  }
                `}
              >
                {item.label}
              </Link>
            )
          })}
        </div>

        {/* Right side — status + GitHub */}
        <div className="ml-auto flex items-center gap-4 shrink-0">
          {/* Status */}
          <div className="flex items-center gap-2">
            <div className={`
              w-2 h-2 rounded-full
              ${healthy === null ? 'bg-yellow-500 animate-pulse' : healthy ? 'bg-emerald-500' : 'bg-red-500'}
            `} />
            <span className="text-[10px] font-mono text-[#555566] hidden sm:inline">
              {healthy === null ? 'connecting' : healthy ? 'connected' : 'offline'}
            </span>
          </div>

          {/* Theme toggle */}
          <button
            onClick={toggleTheme}
            aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
            className="text-[#666680] hover:text-[var(--accent)] transition-colors duration-200"
          >
            {isDark ? (
              <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                <circle cx="12" cy="12" r="4" />
                <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
              </svg>
            ) : (
              <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
              </svg>
            )}
          </button>

          {/* GitHub */}
          <a
            href="https://github.com/SteveOberst/Transmute"
            target="_blank"
            rel="noopener noreferrer"
            aria-label="View on GitHub"
            className="text-[#666680] hover:text-white transition-colors duration-200"
          >
            <svg viewBox="0 0 24 24" className="w-5 h-5" fill="currentColor" aria-hidden>
              <path d="M12 2C6.477 2 2 6.484 2 12.021c0 4.428 2.865 8.184 6.839 9.504.5.092.682-.217.682-.483 0-.237-.009-.868-.014-1.703-2.782.605-3.369-1.342-3.369-1.342-.454-1.154-1.11-1.462-1.11-1.462-.908-.62.069-.608.069-.608 1.003.07 1.531 1.031 1.531 1.031.892 1.529 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.254-.446-1.272.098-2.65 0 0 .84-.269 2.75 1.025A9.578 9.578 0 0 1 12 6.836a9.59 9.59 0 0 1 2.504.337c1.909-1.294 2.747-1.025 2.747-1.025.546 1.378.202 2.396.1 2.65.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482C19.138 20.2 22 16.447 22 12.021 22 6.484 17.522 2 12 2z"/>
            </svg>
          </a>
        </div>
      </div>
    </nav>
  )
}
