import { Button } from '@mui/material';
import { Suspense, lazy } from 'react';
import { Link, Route, Routes } from 'react-router';
import ApiGate from './components/ApiGate';
import { EmptyState, Loading } from './components/common';
import Layout from './components/Layout';
import DashboardPage from './pages/DashboardPage';

// Pages load on demand. The chart library (ECharts) is only needed on roast pages, so it stays out of the first load.
const RoastsPage = lazy(() => import('./pages/RoastsPage'));
const RoastDetailPage = lazy(() => import('./pages/RoastDetailPage'));
const ComparePage = lazy(() => import('./pages/ComparePage'));
const ImportPage = lazy(() => import('./pages/ImportPage'));
const ProfilesPage = lazy(() => import('./pages/ProfilesPage'));
const InventoryPage = lazy(() => import('./pages/InventoryPage'));
const LotDetailPage = lazy(() => import('./pages/LotDetailPage'));
const AboutPage = lazy(() => import('./pages/AboutPage'));

function NotFound() {
  return (
    <EmptyState title="Page not found" action={<Button component={Link} to="/" variant="contained">Back to the dashboard</Button>}>
      There is nothing at this address.
    </EmptyState>
  );
}

export default function App() {
  return (
    <ApiGate>
      <Layout>
        <Suspense fallback={<Loading />}>
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/roasts" element={<RoastsPage />} />
            <Route path="/roasts/:id" element={<RoastDetailPage />} />
            <Route path="/compare" element={<ComparePage />} />
            <Route path="/import" element={<ImportPage />} />
            <Route path="/profiles" element={<ProfilesPage />} />
            <Route path="/inventory" element={<InventoryPage />} />
            <Route path="/inventory/:id" element={<LotDetailPage />} />
            <Route path="/about" element={<AboutPage />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Suspense>
      </Layout>
    </ApiGate>
  );
}
