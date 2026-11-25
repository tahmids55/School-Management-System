import { useState } from 'react';
import { Alert, Button, Card, Empty, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { classKeys, createClass, deleteClass, updateClass, type ClassRequest, type SchoolClass } from '../../api/classes';
import { errorMessage, fieldErrors } from '../../api/client';
import { teacherKeys } from '../../api/teachers';
import { useActiveTeachers } from '../../hooks/useActiveTeachers';
import { useClasses } from '../../hooks/useClasses';
import { useYears } from '../../hooks/useYears';
import { feedback } from '../../utils/feedback';
import ClassDetailDrawer from './ClassDetailDrawer';

const { Text } = Typography;

/** Classes tab: year selector, table (a row opens the detail drawer), create/edit modal, delete confirmation. */
export default function ClassesTab() {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ClassRequest>();
  // undefined = modal closed, null = creating, a class = editing that class
  const [editing, setEditing] = useState<SchoolClass | null | undefined>(undefined);
  const [chosenYearId, setChosenYearId] = useState<number>();
  const [openClass, setOpenClass] = useState<SchoolClass>();

  const years = useYears();
  // Until the user picks a year, show the current one.
  const yearId = chosenYearId ?? years.currentYear?.id;
  const classes = useClasses(yearId, yearId !== undefined);
  const teachers = useActiveTeachers();

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: classKeys.all }),
      queryClient.invalidateQueries({ queryKey: teacherKeys.all }),
    ]);

  const save = useMutation({
    mutationFn: (values: ClassRequest) => {
      const payload: ClassRequest = {
        ...values,
        classTeacherId: values.classTeacherId ?? null,
      };
      return editing ? updateClass(editing.id, payload) : createClass(payload);
    },
    onSuccess: (saved) => {
      feedback.message.success(`${saved.displayName} saved`);
      setEditing(undefined);
      void refresh();
    },
    onError: (error) => {
      form.setFields(fieldErrors(error) as Parameters<typeof form.setFields>[0]);
      feedback.message.error(errorMessage(error));
    },
  });

  const remove = useMutation({
    mutationFn: (schoolClass: SchoolClass) => deleteClass(schoolClass.id),
    onSuccess: (_, schoolClass) => {
      feedback.message.success(`${schoolClass.displayName} deleted`);
      void refresh();
    },
    onError: (error) => feedback.message.error(errorMessage(error)),
  });

  function openModal(schoolClass: SchoolClass | null) {
    setEditing(schoolClass);
    form.resetFields();
    form.setFieldsValue(schoolClass ?? { academicYearId: yearId, capacity: 40 });
  }

  const columns: ColumnsType<SchoolClass> = [
    { title: 'Class', dataIndex: 'displayName', render: (name: string) => <Text strong>{name}</Text> },
    { title: 'Room', dataIndex: 'room', render: (room?: string | null) => room || '-' },
    {
      title: 'Class teacher',
      dataIndex: 'classTeacherName',
      render: (name?: string | null) => name || <Text type="secondary">Not set</Text>,
    },
    { title: 'Students', dataIndex: 'studentCount', align: 'right' },
    { title: 'Capacity', dataIndex: 'capacity', align: 'right' },
    {
      key: 'actions',
      align: 'right',
      render: (_, schoolClass) => (
        // Clicks here (and in the popconfirm, which React bubbles through) must not open the drawer.
        <span onClick={(event) => event.stopPropagation()}>
          <Space size={0} wrap>
            <Button type="link" size="small" onClick={() => openModal(schoolClass)}>
              Edit
            </Button>
            <Popconfirm
              title={`Delete ${schoolClass.displayName}?`}
              okButtonProps={{ danger: true }}
              onConfirm={() => remove.mutateAsync(schoolClass).catch(() => undefined)}
            >
              <Button type="link" size="small" danger>
                Delete
              </Button>
            </Popconfirm>
          </Space>
        </span>
      ),
    },
  ];

  const yearOptions = (years.data ?? []).map((year) => ({
    value: year.id,
    label: year.current ? `${year.name} (Current)` : year.name,
  }));
  const teacherOptions = (teachers.data ?? []).map((teacher) => ({
    value: teacher.id,
    label: `${teacher.fullName} (${teacher.employeeNo})`,
  }));
  if (
    editing?.classTeacherId &&
    editing.classTeacherName &&
    !teacherOptions.some((option) => option.value === editing.classTeacherId)
  ) {
    teacherOptions.unshift({
      value: editing.classTeacherId,
      label: editing.classTeacherName,
    });
  }
  const activeOpenClass = openClass
    ? classes.data?.find((item) => item.id === openClass.id) ?? openClass
    : undefined;
  const error = years.error ?? classes.error;

  let body;
  if (error) {
    body = (
      <Alert
        type="error"
        showIcon
        message={errorMessage(error)}
        action={<Button onClick={() => void (years.isError ? years.refetch() : classes.refetch())}>Try again</Button>}
      />
    );
  } else if (!years.isLoading && yearId === undefined) {
    body = <Empty description="Create an academic year first (Academic years tab)." />;
  } else {
    body = (
      <Table
        rowKey="id"
        columns={columns}
        dataSource={classes.data ?? []}
        loading={years.isLoading || classes.isLoading}
        pagination={false}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: <Empty description="No classes in this year yet" /> }}
        onRow={(schoolClass) => ({ onClick: () => setOpenClass(schoolClass), style: { cursor: 'pointer' } })}
      />
    );
  }

  return (
    <Card
      title={
        <Space size="small" wrap>
          <span>Academic year</span>
          <Select
            style={{ minWidth: 140 }}
            loading={years.isLoading}
            options={yearOptions}
            value={yearId}
            onChange={setChosenYearId}
            placeholder="Not set"
          />
        </Space>
      }
      extra={
        <Button type="primary" icon={<PlusOutlined />} disabled={yearId === undefined} onClick={() => openModal(null)}>
          New class
        </Button>
      }
    >
      {body}

      <Modal
        title={editing ? 'Edit' : 'New class'}
        open={editing !== undefined}
        okText={editing ? 'Save' : 'Create'}
        confirmLoading={save.isPending}
        onOk={() => form.submit()}
        onCancel={() => setEditing(undefined)}
        forceRender
      >
        <Form form={form} layout="vertical" onFinish={(values) => save.mutate(values)}>
          <Form.Item name="academicYearId" label="Academic year" rules={[{ required: true }]}>
            <Select options={yearOptions} />
          </Form.Item>
          <Form.Item name="name" label="Class name" rules={[{ required: true, whitespace: true }, { max: 30 }]}>
            <Input placeholder="Class 9" />
          </Form.Item>
          <Space size="middle" wrap align="start">
            <Form.Item name="section" label="Section" rules={[{ required: true, whitespace: true }, { max: 10 }]}>
              <Input placeholder="A" style={{ width: 100 }} />
            </Form.Item>
            <Form.Item name="room" label="Room" rules={[{ max: 20 }]}>
              <Input placeholder="R-201" style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="capacity" label="Capacity" rules={[{ required: true }]}>
              <InputNumber min={1} max={200} precision={0} />
            </Form.Item>
          </Space>
          <Form.Item name="classTeacherId" label="Class teacher">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Not set"
              loading={teachers.isLoading}
              options={teacherOptions}
            />
          </Form.Item>
        </Form>
      </Modal>

      <ClassDetailDrawer schoolClass={activeOpenClass} onClose={() => setOpenClass(undefined)} />
    </Card>
  );
}
