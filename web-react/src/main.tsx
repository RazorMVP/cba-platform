// web-react/src/main.tsx
import '@/styles/globals.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 1000 * 60 * 5, retry: 1 },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <div style={{ padding: 32, color: 'var(--color-text)' }}>CBA Backoffice — loading…</div>
    </QueryClientProvider>
  </StrictMode>,
)
