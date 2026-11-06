import DarkModeIcon from '@mui/icons-material/DarkMode';
import DashboardIcon from '@mui/icons-material/Dashboard';
import GitHubIcon from '@mui/icons-material/GitHub';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import InventoryIcon from '@mui/icons-material/Inventory2';
import LightModeIcon from '@mui/icons-material/LightMode';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import MenuIcon from '@mui/icons-material/Menu';
import TuneIcon from '@mui/icons-material/Tune';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import {
  AppBar,
  Box,
  Chip,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router';
import { useColorMode } from '../colorMode';

const DRAWER_WIDTH = 220;
const REPO_URL = 'https://github.com/vladimirmilivojevic-creator/roastbench';

const NAV: { to: string; label: string; icon: ReactNode; match: (path: string) => boolean }[] = [
  { to: '/', label: 'Dashboard', icon: <DashboardIcon />, match: (p) => p === '/' },
  { to: '/roasts', label: 'Roasts', icon: <LocalFireDepartmentIcon />, match: (p) => p.startsWith('/roasts') || p.startsWith('/compare') },
  { to: '/import', label: 'Import', icon: <UploadFileIcon />, match: (p) => p.startsWith('/import') },
  { to: '/profiles', label: 'Profiles', icon: <TuneIcon />, match: (p) => p.startsWith('/profiles') },
  { to: '/inventory', label: 'Green coffee', icon: <InventoryIcon />, match: (p) => p.startsWith('/inventory') },
  { to: '/about', label: 'About', icon: <InfoOutlinedIcon />, match: (p) => p.startsWith('/about') },
];

export default function Layout({ children }: { children: ReactNode }) {
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up('md'));
  const [open, setOpen] = useState(false);
  const { pathname } = useLocation();
  const { mode, toggle } = useColorMode();

  const nav = (
    <Box sx={{ pt: { xs: 1, md: 0 } }}>
      <Toolbar sx={{ display: { md: 'block', xs: 'none' } }} />
      <List component="nav" aria-label="Main navigation" sx={{ px: 1 }}>
        {NAV.map((item) => (
          <ListItemButton
            key={item.to}
            component={Link}
            to={item.to}
            selected={item.match(pathname)}
            onClick={() => setOpen(false)}
            sx={{ borderRadius: 2, mb: 0.5 }}
          >
            <ListItemIcon sx={{ minWidth: 38 }}>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} slotProps={{ primary: { sx: { fontWeight: 600 } } }} />
          </ListItemButton>
        ))}
      </List>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar position="fixed" color="inherit" elevation={0} sx={{ zIndex: theme.zIndex.drawer + 1, borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar>
          {!desktop && (
            <IconButton edge="start" aria-label="Open navigation" onClick={() => setOpen(true)} sx={{ mr: 1 }}>
              <MenuIcon />
            </IconButton>
          )}
          <Box component="img" src={`${import.meta.env.BASE_URL}favicon.svg`} alt="" sx={{ width: 30, height: 30, mr: 1.25 }} />
          <Typography variant="h6" component="div" sx={{ fontWeight: 800, letterSpacing: -0.3 }}>
            RoastBench
          </Typography>
          <Chip
            size="small"
            label="Synthetic demo data"
            color="secondary"
            variant="outlined"
            sx={{ ml: 2, display: { xs: 'none', sm: 'inline-flex' } }}
          />
          <Box sx={{ flexGrow: 1 }} />
          <Tooltip title={mode === 'dark' ? 'Light theme' : 'Dark theme'}>
            <IconButton aria-label="Toggle colour theme" onClick={toggle}>
              {mode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
            </IconButton>
          </Tooltip>
          <Tooltip title="Source code on GitHub">
            <IconButton aria-label="Source code on GitHub" component="a" href={REPO_URL} target="_blank" rel="noreferrer">
              <GitHubIcon />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Drawer
        variant={desktop ? 'permanent' : 'temporary'}
        open={desktop || open}
        onClose={() => setOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{
          width: desktop ? DRAWER_WIDTH : 0,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box', borderRight: 1, borderColor: 'divider' },
        }}
      >
        {nav}
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: { xs: 2, md: 3.5 } }}>
        <Toolbar />
        <Box sx={{ maxWidth: 1400, mx: 'auto' }}>{children}</Box>
      </Box>
    </Box>
  );
}
