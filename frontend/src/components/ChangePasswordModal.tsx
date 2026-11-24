import { useEffect, useState } from 'react';
import { Alert, Button, Form, Input, Modal } from 'antd';
import { useNavigate } from 'react-router-dom';
import * as authApi from '../api/auth';
import { errorMessage, fieldErrors } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { feedback } from '../utils/feedback';

export interface ChangePasswordModalProps {
  /** Whether the modal is visible. */
  open: boolean;
  /** True after a temporary-password login: the user cannot close the modal. */
  forced?: boolean;
  onClose: () => void;
}

interface FormValues {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,100}$/;

export function ChangePasswordModal({ open, forced = false, onClose }: ChangePasswordModalProps) {
  const [form] = Form.useForm<FormValues>();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      form.resetFields();
      setSubmitError(null);
    }
  }, [open, form]);

  const handleFinish = async (values: FormValues) => {
    setSubmitError(null);
    setSubmitting(true);
    try {
      await authApi.changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      feedback.message.success('Password changed. Other devices were signed out.');
      onClose();
      await logout();
      navigate('/login', { replace: true });
    } catch (error) {
      const validFields = new Set<keyof FormValues>(['currentPassword', 'newPassword', 'confirmPassword']);
      const matchedFields = fieldErrors(error)
        .filter((item): item is { name: keyof FormValues; errors: string[] } =>
          validFields.has(item.name as keyof FormValues),
        );
      if (matchedFields.length > 0) {
        form.setFields(matchedFields);
      } else {
        setSubmitError(errorMessage(error));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="Change password"
      open={open}
      onCancel={forced ? undefined : onClose}
      closable={!forced}
      maskClosable={!forced}
      keyboard={!forced}
      footer={null}
      width={440}
      destroyOnHidden
    >
      {forced && (
        <Alert
          type="warning"
          showIcon
          message="You signed in with a temporary password. Please set your own password to continue."
          style={{ marginBottom: 16 }}
        />
      )}
      {submitError && (
        <Alert
          type="error"
          showIcon
          message={submitError}
          style={{ marginBottom: 16 }}
        />
      )}
      <Form form={form} layout="vertical" onFinish={handleFinish} requiredMark={false}>
        <Form.Item
          name="currentPassword"
          label="Current password"
          rules={[{ required: true, message: 'Current password is required' }]}
        >
          <Input.Password autoComplete="current-password" />
        </Form.Item>

        <Form.Item
          name="newPassword"
          label="New password"
          extra="At least 8 characters with a letter and a digit"
          rules={[
            { required: true, message: 'New password is required' },
            {
              pattern: PASSWORD_PATTERN,
              message: 'must be 8-100 characters and contain a letter and a digit',
            },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || value !== getFieldValue('currentPassword')) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('New password must be different from the current password'));
              },
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>

        <Form.Item
          name="confirmPassword"
          label="Confirm new password"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: 'Please confirm your new password' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('Passwords do not match'));
              },
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 24 }}>
          {!forced && (
            <Button onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
          )}
          <Button type="primary" htmlType="submit" loading={submitting}>
            Change password
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
