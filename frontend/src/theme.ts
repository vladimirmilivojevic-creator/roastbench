import { createTheme } from '@mui/material/styles';

export type ColorMode = 'light' | 'dark';

export function buildTheme(mode: ColorMode) {
  const dark = mode === 'dark';
  return createTheme({
    palette: {
      mode,
      primary: { main: dark ? '#bcaaa4' : '#5d4037' },
      secondary: { main: '#e65100' },
      background: dark ? { default: '#1c1411', paper: '#261c18' } : { default: '#faf6f3', paper: '#ffffff' },
    },
    shape: { borderRadius: 10 },
    typography: {
      fontFamily: '"Inter", "Segoe UI", Roboto, system-ui, -apple-system, sans-serif',
      h4: { fontWeight: 700 },
      h5: { fontWeight: 700 },
      h6: { fontWeight: 650 },
    },
    components: {
      MuiCard: { defaultProps: { variant: 'outlined' } },
      MuiButton: { styleOverrides: { root: { textTransform: 'none', fontWeight: 600 } } },
      MuiTableCell: { styleOverrides: { head: { fontWeight: 700, whiteSpace: 'nowrap' } } },
    },
  });
}
