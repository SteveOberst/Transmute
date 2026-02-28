import type { Metadata } from 'next'
import { Providers } from './providers'
import './globals.css'

export const metadata: Metadata = {
  title: 'Transmute Playground',
  description:
    'Interactive explorer for the Transmute media-processing library. Upload, inspect, and transform images, audio, and video.',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="dark" suppressHydrationWarning>
      <body className="min-h-dvh bg-[var(--surface-0)] antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
