import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import App from './App';
import { ColorModeProvider } from './colorMode';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 15_000 },
  },
});

// "/roastbench/" on GitHub Pages, "/" everywhere else.
const basename = import.meta.env.BASE_URL.replace(/\/$/, '');

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ColorModeProvider>
        <BrowserRouter basename={basename}>
          <App />
        </BrowserRouter>
      </ColorModeProvider>
    </QueryClientProvider>
  </StrictMode>,
);
