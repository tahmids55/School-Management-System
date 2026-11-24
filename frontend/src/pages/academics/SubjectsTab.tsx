import { useState } from 'react';
import { Alert, Button, Card, Empty, Form, Input, InputNumber, Modal, Popconfirm, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  academicKeys,
  createSubject,
  deleteSubject,
  updateSubject,
  type Subject,
  type SubjectRequest,
} from '../../api/academics';
import { errorMessage, fieldErrors } from '../../api/client';
import { useSubjects } from '../../hooks/useSubjects';
import { feedback } from '../../utils/feedback';

/** Subjects tab: table ordered by code, create/edit modal, delete confirmation. */
export default function SubjectsTab() {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<SubjectRequest>();
  // undefined = modal closed, null = creating, a subject = editing that subject
  const [editing, setEditing] = useState<Subject | null | undefined>(undefined);

  const subjects = useSubjects();

  const save = useMutation({
    mutationFn: (values: SubjectRequest) => (editing ? updateSubject(editing.id, values) : createSubject(values)),
    onSuccess: (saved) => {
      feedback.message.success(`${saved.code} saved`);
      setEditing(undefined);
      void queryClient.invalidateQueries({ queryKey: academicKeys.subjects });
    },
    onError: (error) => {
      // Server field names (code, name, fullMarks, passMarks) match the form's field names.
      form.setFields(fieldErrors(error) as Parameters<typeof form.setFields>[0]);
      feedback.message.error(errorMessage(error));
    },
  });

  const remove = useMutation({
    mutationFn: (subject: Subject) => deleteSubject(subject.id),
    onSuccess: (_, subject) => {
      feedback.message.success(`${subject.code} deleted`);
      void queryClient.invalidateQueries({ queryKey: academicKeys.subjects });
    },
    onError: (error) => feedback.message.error(errorMessage(error)),
  });

  function openModal(subject: Subject | null) {
    setEditing(subject);
    form.resetFields();
    form.setFieldsValue(subject ?? { fullMarks: 100, passMarks: 33 });
  }

  const columns: ColumnsType<Subject> = [
    { title: 'Code', dataIndex: 'code', width: 120 },
    { title: 'Name', dataIndex: 'name' },
    { title: 'Full marks', dataIndex: 'fullMarks', align: 'right', width: 110 },
    { title: 'Pass marks', dataIndex: 'passMarks', align: 'right', width: 110 },
    {
      key: 'actions',
      align: 'right',
      render: (_, subject) => (
        <Space size={0} wrap>
          <Button type="link" size="small" onClick={() => openModal(subject)}>
            Edit
          </Button>
          <Popconfirm
            title={`Delete ${subject.code}?`}
            okButtonProps={{ danger: true }}
            onConfirm={() => remove.mutateAsync(subject).catch(() => undefined)}
          >
            <Button type="link" size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="Subjects"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal(null)}>
          New subject
        </Button>
      }
    >
      {subjects.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(subjects.error)}
          action={<Button onClick={() => void subjects.refetch()}>Try again</Button>}
        />
      ) : (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={subjects.data ?? []}
          loading={subjects.isLoading}
          pagination={false}
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: <Empty description="No subjects yet" /> }}
        />
      )}

      <Modal
        title={editing ? 'Edit' : 'New subject'}
        open={editing !== undefined}
        okText={editing ? 'Save' : 'Create'}
        confirmLoading={save.isPending}
        onOk={() => form.submit()}
        onCancel={() => setEditing(undefined)}
        forceRender
      >
        <Form form={form} layout="vertical" onFinish={(values) => save.mutate(values)}>
          <Form.Item name="code" label="Code" rules={[{ required: true, whitespace: true }, { max: 20 }]}>
            <Input placeholder="PHY101" />
          </Form.Item>
          <Form.Item name="name" label="Name" rules={[{ required: true, whitespace: true }, { max: 100 }]}>
            <Input />
          </Form.Item>
          <Space size="middle" wrap>
            <Form.Item name="fullMarks" label="Full marks" rules={[{ required: true }]}>
              <InputNumber min={1} max={1000} precision={0} />
            </Form.Item>
            <Form.Item
              name="passMarks"
              label="Pass marks"
              dependencies={['fullMarks']}
              rules={[
                { required: true },
                ({ getFieldValue }) => ({
                  validator: (_, value: number | null) =>
                    value == null || value <= getFieldValue('fullMarks')
                      ? Promise.resolve()
                      : Promise.reject(new Error('Pass marks cannot be greater than full marks')),
                }),
              ]}
            >
              <InputNumber min={0} max={1000} precision={0} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  );
}
