import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Spin,
  Switch,
  Tag,
  Typography,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, PushpinFilled } from '@ant-design/icons';
import {
  createNotice,
  deleteNotice,
  listNotices,
  updateNotice,
  type NoticeAudience,
  type NoticeRequest,
  type NoticeResponse,
} from '../../api/notices';
import { errorMessage, fieldErrors } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader } from '../../components/common';
import { feedback } from '../../utils/feedback';
import { formatDateTime } from '../../utils/format';

const { Paragraph, Text, Title } = Typography;

const PAGE_SIZE = 10;

const AUDIENCE_META: Record<NoticeAudience, { label: string; color: string }> = {
  ALL: { label: 'Everyone', color: 'blue' },
  STUDENTS: { label: 'Students', color: 'geekblue' },
  TEACHERS: { label: 'Teachers', color: 'cyan' },
};

const AUDIENCE_OPTIONS: { value: NoticeAudience; label: string }[] = [
  { value: 'ALL', label: 'Everyone' },
  { value: 'STUDENTS', label: 'Students' },
  { value: 'TEACHERS', label: 'Teachers' },
];

export default function NoticesPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const queryClient = useQueryClient();

  const [page, setPage] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<NoticeResponse | null>(null);
  const [form] = Form.useForm<NoticeRequest>();

  const noticesQuery = useQuery({
    queryKey: ['notices', page, PAGE_SIZE],
    queryFn: () => listNotices(page, PAGE_SIZE),
  });

  const saveMutation = useMutation({
    mutationFn: (values: NoticeRequest) =>
      editing ? updateNotice(editing.id, values) : createNotice(values),
    onSuccess: () => {
      feedback.message.success(editing ? 'Notice updated' : 'Notice published');
      setModalOpen(false);
      setEditing(null);
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['notices'] });
    },
    onError: (err) => {
      const validKeys: ReadonlySet<string> = new Set(['title', 'body', 'audience', 'pinned']);
      const fields = fieldErrors(err)
        .filter((f): f is { name: keyof NoticeRequest; errors: string[] } => validKeys.has(f.name));
      if (fields.length > 0) {
        form.setFields(fields);
      } else {
        feedback.message.error(errorMessage(err));
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteNotice(id),
    onSuccess: () => {
      feedback.message.success('Notice deleted');
      if ((noticesQuery.data?.content.length ?? 0) <= 1 && page > 0) {
        setPage((current) => Math.max(0, current - 1));
      }
      queryClient.invalidateQueries({ queryKey: ['notices'] });
    },
    onError: (err) => {
      feedback.message.error(errorMessage(err));
    },
  });

  const openCreateModal = () => {
    setEditing(null);
    form.setFieldsValue({
      title: '',
      body: '',
      audience: 'ALL',
      pinned: false,
    });
    setModalOpen(true);
  };

  const openEditModal = (notice: NoticeResponse) => {
    setEditing(notice);
    form.setFieldsValue({
      title: notice.title,
      body: notice.body,
      audience: notice.audience,
      pinned: notice.pinned,
    });
    setModalOpen(true);
  };

  const closeModal = () => {
    if (saveMutation.isPending) return;
    setModalOpen(false);
    setEditing(null);
    form.resetFields();
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    saveMutation.mutate(values);
  };

  const notices = noticesQuery.data?.content ?? [];
  const totalElements = noticesQuery.data?.totalElements ?? 0;

  return (
    <div>
      <PageHeader
        title="Notices"
        subtitle="Announcements for students and staff"
        actions={
          isAdmin ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
              Publish notice
            </Button>
          ) : undefined
        }
      />

      {noticesQuery.isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      ) : noticesQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(noticesQuery.error)}
          action={
            <Button size="small" onClick={() => noticesQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : notices.length === 0 ? (
        <Card>
          <Empty description="No notices yet" />
        </Card>
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          {notices.map((notice) => {
            const audienceInfo = AUDIENCE_META[notice.audience];
            return (
              <Card key={notice.id}>
                <div
                  style={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    gap: 12,
                    marginBottom: 8,
                  }}
                >
                  <Space wrap size={[8, 6]} align="center">
                    {notice.pinned && (
                      <Tag color="gold" bordered={false} icon={<PushpinFilled />}>
                        Pinned
                      </Tag>
                    )}
                    <Title level={5} style={{ margin: 0 }}>
                      {notice.title}
                    </Title>
                    <Tag color={audienceInfo.color} bordered={false}>
                      {audienceInfo.label}
                    </Tag>
                  </Space>

                  {isAdmin && (
                    <Space wrap size={8}>
                      <Button
                        size="small"
                        icon={<EditOutlined />}
                        onClick={() => openEditModal(notice)}
                      >
                        Edit
                      </Button>
                      <Popconfirm
                        title="Delete this notice?"
                        okText="Delete"
                        okButtonProps={{ danger: true }}
                        onConfirm={() => deleteMutation.mutate(notice.id)}
                      >
                        <Button
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          loading={deleteMutation.isPending && deleteMutation.variables === notice.id}
                        >
                          Delete
                        </Button>
                      </Popconfirm>
                    </Space>
                  )}
                </div>

                <Paragraph
                  style={{
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    marginBottom: 12,
                  }}
                >
                  {notice.body}
                </Paragraph>

                <Text type="secondary" style={{ fontSize: 12 }}>
                  {formatDateTime(notice.publishedAt)}
                  {notice.author ? ` · ${notice.author}` : ''}
                </Text>
              </Card>
            );
          })}

          {totalElements > PAGE_SIZE && (
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Pagination
                current={page + 1}
                pageSize={PAGE_SIZE}
                total={totalElements}
                onChange={(nextPage) => setPage(nextPage - 1)}
                showSizeChanger={false}
              />
            </div>
          )}
        </Space>
      )}

      <Modal
        title={editing ? 'Edit' : 'Publish notice'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={closeModal}
        okText={editing ? 'Edit' : 'Publish notice'}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
        style={{ maxWidth: 'calc(100vw - 32px)' }}
      >
        <Form<NoticeRequest> form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="title"
            label="Title"
            rules={[
              { required: true, whitespace: true, message: 'Title is required' },
              { max: 150, message: 'Title must be at most 150 characters' },
            ]}
          >
            <Input maxLength={150} showCount />
          </Form.Item>

          <Form.Item
            name="body"
            label="Message"
            rules={[
              { required: true, whitespace: true, message: 'Message is required' },
              { max: 5000, message: 'Message must be at most 5000 characters' },
            ]}
          >
            <Input.TextArea rows={5} maxLength={5000} showCount />
          </Form.Item>

          <Form.Item
            name="audience"
            label="Audience"
            rules={[{ required: true, message: 'Audience is required' }]}
          >
            <Select options={AUDIENCE_OPTIONS} />
          </Form.Item>

          <Form.Item name="pinned" label="Pin to top" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
