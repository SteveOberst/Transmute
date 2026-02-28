'use client'

import { HeroUIProvider } from '@heroui/react'
import { useRouter } from 'next/navigation'
import type { ReactNode } from 'react'
import { ThemeProvider } from '@/hooks/useTheme'
import { ToastProvider } from '@/components/Toast'

export function Providers({ children }: { children: ReactNode }) {
  const router = useRouter()
  return (
    <ThemeProvider>
      <ToastProvider>
        <HeroUIProvider navigate={router.push}>
          {children}
        </HeroUIProvider>
      </ToastProvider>
    </ThemeProvider>
  )
}
