import { Suspense, useEffect, useMemo, useState } from 'react';
import { Avatar, Button, Drawer, Dropdown, Grid, Layout, Menu, type MenuProps } from 'antd';
import { ApiOutlined, KeyOutlined, LogoutOutlined, MenuOutlined } from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ChangePasswordModal } from '../components/ChangePasswordModal';
import { NotificationBell } from '../components/NotificationBell';
import { FullPageSpinner, StatusTag } from '../components/common';
import { menuFor, selectedMenuKey } from '../routes/registry';
import { layoutSizes } from '../theme';

const { Sider, Header, Content } = Layout;

/*
 * Sidebar labels come from each feature's *.routes.tsx, in this order (see routes/menuOrder.ts):
 * "Dashboard", "Students", "Teachers", "Academics", "Attendance", "Exams & Marks", "Results", "Fees",
 * "My Attendance", "My Results", "My Fees", "Notices", "User Accounts", "Audit Log".
 */

function Brand() {
  return (
    <Link to="/" className="brand">
      <span className="brand-mark">G</span>
      <span className="brand-name">Greenfield School</span>
    </Link>
  );
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '')).toUpperCase();
}

export function AppLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const screens = Grid.useBreakpoint();
  // useBreakpoint is empty on the very first render, so treat "unknown" as desktop.
  const isDesktop = screens.lg ?? true;
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);

  // Close the phone drawer after navigating.
  useEffect(() => setDrawerOpen(false), [location.pathname]);

  const menuRoutes = useMemo(() => menuFor(user?.role), [user?.role]);
  const menuItems: MenuProps['items'] = menuRoutes.map((route) => ({
    key: route.path,
    icon: route.menu?.icon,
    label: <Link to={route.path}>{route.menu?.label}</Link>,
  }));
  const selectedKey = selectedMenuKey(
    location.pathname,
    menuRoutes.map((route) => route.path),
  );

  if (!user) return null; // RequireRole around the layout already handles this.

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const userMenu: MenuProps = {
    items: [
      { key: 'password', icon: <KeyOutlined />, label: 'Change password', onClick: () => setPasswordOpen(true) },
      {
        key: 'api',
        icon: <ApiOutlined />,
        label: (
          <a href="/swagger-ui.html" target="_blank" rel="noopener noreferrer">
            API documentation
          </a>
        ),
      },
      { type: 'divider' },
      { key: 'logout', icon: <LogoutOutlined />, label: 'Log out', danger: true, onClick: handleLogout },
    ],
  };

  const navigation = (
    <Menu mode="inline" items={menuItems} selectedKeys={selectedKey ? [selectedKey] : []} className="side-menu" />
  );

  return (
    <Layout className="app-shell">
      {isDesktop ? (
        <Sider width={layoutSizes.siderWidth} className="app-sider">
          <Brand />
          {navigation}
        </Sider>
      ) : (
        <Drawer
          placement="left"
          width={layoutSizes.siderWidth}
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          closable={false}
          styles={{ body: { padding: 0 } }}
          className="app-drawer"
        >
          <Brand />
          {navigation}
        </Drawer>
      )}

      <Layout>
        <Header className="app-header">
          <div className="app-header-left">
            {!isDesktop && (
              <Button
                type="text"
                icon={<MenuOutlined />}
                aria-label="Open menu"
                title="Open menu"
                onClick={() => setDrawerOpen(true)}
              />
            )}
            {!isDesktop && <Brand />}
          </div>
          <div className="app-header-right">
            <NotificationBell />
            <Dropdown menu={userMenu} trigger={['click']} placement="bottomRight">
              <button type="button" className="user-button">
                <Avatar size={32} style={{ background: 'var(--primary)' }}>
                  {initials(user.fullName || user.username)}
                </Avatar>
                <span className="user-name">{user.fullName || user.username}</span>
                <span className="user-role">
                  <StatusTag value={user.role} />
                </span>
              </button>
            </Dropdown>
          </div>
        </Header>

        <Content className="app-content">
          <div className="app-content-inner">
            <Suspense fallback={<FullPageSpinner />}>
              <Outlet />
            </Suspense>
          </div>
        </Content>
      </Layout>

      <ChangePasswordModal
        open={passwordOpen || user.mustChangePassword}
        forced={user.mustChangePassword}
        onClose={() => setPasswordOpen(false)}
      />
    </Layout>
  );
}

/** Shown for any unknown address inside the app. */
export function NotFoundPage() {
  return (
    <div className="full-page-center">
      <div style={{ textAlign: 'center' }}>
        <h2 style={{ marginBottom: 8 }}>Page not found</h2>
        <p style={{ color: 'var(--slate)', marginBottom: 20 }}>The address you opened does not exist.</p>
        <Link to="/">
          <Button type="primary">Back to dashboard</Button>
        </Link>
      </div>
    </div>
  );
}
