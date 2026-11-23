import { useState } from 'react';
import { Alert, Button, Form, Input, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { safeReturnPath } from '../auth/RequireRole';
import { FullPageSpinner } from '../components/common';
import './LoginPage.css';

const { Title, Text } = Typography;

interface LoginForm {
  username: string;
  password: string;
}

// Decorative squares in the brand column: pink, yellow, teal, orange, purple.
const squares = ['#ff64c8', '#ffd43b', '#2a9d99', '#dd5b00', '#7b3ff2'];

export default function LoginPage() {
  const { user, ready, login } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // RequireRole stored the page the user wanted in location.state.from.
  const returnTo = safeReturnPath((location.state as { from?: unknown } | null)?.from);

  // Still checking the refresh cookie: don't flash the form.
  if (!ready) return <FullPageSpinner />;
  // Already signed in: nothing to do here.
  if (user) return <Navigate to={returnTo} replace />;

  const onFinish = async (values: LoginForm) => {
    setSubmitting(true);
    setError(null);
    try {
      // Trim the username (copy-paste spaces), never the password (spaces may be part of it).
      await login({ username: values.username.trim(), password: values.password });
      navigate(returnTo, { replace: true });
    } catch (e) {
      setError(errorMessage(e)); // e.g. the server's "Invalid username or password"
      setSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <aside className="login-brand">
        <div className="login-squares" aria-hidden="true">
          {squares.map((color) => (
            <span key={color} style={{ background: color }} />
          ))}
        </div>
        <div className="login-brand-name">
          <span className="login-mark">G</span>
          <span>Greenfield High School</span>
        </div>
        <div>
          <h1 className="login-headline">Everything your school runs on, in one place.</h1>
          <p className="login-text">
            Admissions, classes, attendance, exams, results and fees: connected, secure and always up to date.
          </p>
        </div>
        <div className="login-footer">CSE 3220 Software Engineering Lab</div>
      </aside>

      <main className="login-form-column">
        <div className="login-form">
          {/* On phones the brand column is hidden, so show the name here. */}
          <div className="login-compact-brand">
            <span className="login-mark">G</span>
            <span>Greenfield High School</span>
          </div>

          <Title level={2} style={{ marginBottom: 4 }}>
            Sign in
          </Title>
          <Text type="secondary">Use your school username or email.</Text>

          {error && <Alert type="error" showIcon message={error} style={{ marginTop: 24 }} />}

          <Form<LoginForm>
            layout="vertical"
            requiredMark={false}
            onFinish={onFinish}
            disabled={submitting}
            style={{ marginTop: 24 }}
          >
            <Form.Item
              label="Username or email"
              name="username"
              rules={[{ required: true, whitespace: true, message: 'Enter your username' }]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="Enter your username"
                autoComplete="username"
                autoFocus
                maxLength={120}
              />
            </Form.Item>
            <Form.Item label="Password" name="password" rules={[{ required: true, message: 'Enter your password' }]}>
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="Enter your password"
                autoComplete="current-password"
                maxLength={100}
              />
            </Form.Item>
            <Button type="primary" htmlType="submit" block size="large" loading={submitting}>
              Sign in
            </Button>
          </Form>

          <Text type="secondary" className="login-help">
            Forgot your password? Ask the school office to reset it. Accounts lock for 15 minutes after 5 wrong
            attempts.
          </Text>
        </div>
      </main>
    </div>
  );
}
