import { CssBaseline, ThemeProvider } from '@mui/material';
import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { buildTheme, type ColorMode } from './theme';

interface ColorModeValue {
  mode: ColorMode;
  toggle: () => void;
}

const ColorModeContext = createContext<ColorModeValue>({ mode: 'light', toggle: () => undefined });

const STORAGE_KEY = 'roastbench-color-mode';

function initialMode(): ColorMode {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch {
    // storage can be unavailable (private mode)
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function ColorModeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ColorMode>(initialMode);
  const value = useMemo<ColorModeValue>(
    () => ({
      mode,
      toggle: () =>
        setMode((current) => {
          const next = current === 'light' ? 'dark' : 'light';
          try {
            localStorage.setItem(STORAGE_KEY, next);
          } catch {
            // ignore
          }
          return next;
        }),
    }),
    [mode],
  );
  const theme = useMemo(() => buildTheme(mode), [mode]);
  return (
    <ColorModeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  );
}

export function useColorMode(): ColorModeValue {
  return useContext(ColorModeContext);
}
