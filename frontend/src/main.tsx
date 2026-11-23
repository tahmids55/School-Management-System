import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App as AntApp, ConfigProvider } from 'antd';
import enGB from 'antd/locale/en_GB';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import '@fontsource/inter/400.css';
import '@fontsource/inter/500.css';
import '@fontsource/inter/600.css';
import './index.css';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import { theme } from './theme';
import { FeedbackBridge } from './utils/feedback';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000, // data younger than 30 s is reused without asking the server again
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider theme={theme} locale={enGB}>
      <AntApp>
        <FeedbackBridge />
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <AuthProvider>
              <App />
            </AuthProvider>
          </BrowserRouter>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  </StrictMode>,
);
