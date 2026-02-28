'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

type Theme = 'dark' | 'light'

interface ThemeContextValue {
  theme: Theme
  toggleTheme: () => void
  isDark: boolean
}

const ThemeContext = createContext<ThemeContextValue>({
  theme: 'dark',
  toggleTheme: () => {},
  isDark: true,
})

const STORAGE_KEY = 'transmute-theme'

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>('dark')
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    const saved = localStorage.getItem(STORAGE_KEY) as Theme | null
    const preferred = window.matchMedia('(prefers-color-scheme: light)').matches
      ? 'light'
      : 'dark'
    const initial = saved ?? preferred
    setTheme(initial)
    applyTheme(initial)
    setMounted(true)
  }, [])

  const toggleTheme = useCallback(() => {
    setTheme((prev) => {
      const next: Theme = prev === 'dark' ? 'light' : 'dark'
      localStorage.setItem(STORAGE_KEY, next)
      applyTheme(next)
      return next
    })
  }, [])

  const value = useMemo(
    () => ({ theme, toggleTheme, isDark: theme === 'dark' }),
    [theme, toggleTheme],
  )

  // Avoid flash of wrong theme by hiding until mounted
  if (!mounted) {
    return (
      <ThemeContext.Provider value={value}>
        <div style={{ visibility: 'hidden' }}>{children}</div>
      </ThemeContext.Provider>
    )
  }

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

function applyTheme(theme: Theme) {
  const root = document.documentElement
  root.setAttribute('data-theme', theme)
  if (theme === 'dark') {
    root.classList.add('dark')
  } else {
    root.classList.remove('dark')
  }
}

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext)
}
