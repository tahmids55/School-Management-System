import { useState } from 'react';
import { Alert, Button, Card, DatePicker, Empty, Form, Input, Modal, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import {
  academicKeys,
  activateAcademicYear,
  createAcademicYear,
  deleteAcademicYear,
  updateAcademicYear,
  type AcademicYear,
  type AcademicYearRequest,
} from '../../api/academics';
import { classKeys } from '../../api/classes';
import { errorMessage } from '../../api/client';
import { useYears } from '../../hooks/useYears';
import { feedback } from '../../utils/feedback';
import { formatDate, toIsoDate } from '../../utils/format';

const { Text } = Typography;

interface YearForm {
  name: string;
  range: [Dayjs, Dayjs];
}

/** Academic years tab: newest first, create/edit modal with a date range, activate and delete confirmations. */
export default function AcademicYearsTab() {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<YearForm>();
  // undefined = modal closed, null = creating, a year = editing that year
  const [editing, setEditing] = useState<AcademicYear | null | undefined>(undefined);

  const years = useYears();
  const { currentYear } = years;

  // Class lists show the year's name and default to the current year, so refresh them too.
  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: academicKeys.years }),
      queryClient.invalidateQueries({ queryKey: classKeys.all }),
    ]);

  const save = useMutation({
    mutationFn: (request: AcademicYearRequest) =>
      editing ? updateAcademicYear(editing.id, request) : createAcademicYear(request),
    onSuccess: (saved) => {
      feedback.message.success(`${saved.name} saved`);
      setEditing(undefined);
      void refresh();
    },
    onError: (error) => feedback.message.error(errorMessage(error)),
  });

  const activate = useMutation({
    mutationFn: (year: AcademicYear) => activateAcademicYear(year.id),
    onSuccess: (year) => {
      feedback.message.success(`${year.name} is now the current year`);
      void refresh();
    },
    onError: (error) => feedback.message.error(errorMessage(error)),
  });

  const remove = useMutation({
    mutationFn: (year: AcademicYear) => deleteAcademicYear(year.id),
    onSuccess: (_, year) => {
      feedback.message.success(`${year.name} deleted`);
      void refresh();
    },
    onError: (error) => feedback.message.error(errorMessage(error)),
  });

  function openModal(year: AcademicYear | null) {
    setEditing(year);
    form.resetFields();
    if (year) form.setFieldsValue({ name: year.name, range: [dayjs(year.startDate), dayjs(year.endDate)] });
  }

  function submit(values: YearForm) {
    save.mutate({
      name: values.name.trim(),
      startDate: toIsoDate(values.range[0])!,
      endDate: toIsoDate(values.range[1])!,
    });
  }

  const columns: ColumnsType<AcademicYear> = [
    {
      title: 'Year',
      dataIndex: 'name',
      render: (name: string, year) => (
        <Space size="small">
          <Text strong={year.current}>{name}</Text>
          {year.current && (
            <Tag color="green" bordered={false}>
              Current
            </Tag>
          )}
        </Space>
      ),
    },
    { title: 'Starts', dataIndex: 'startDate', render: (value: string) => formatDate(value) },
    { title: 'Ends', dataIndex: 'endDate', render: (value: string) => formatDate(value) },
    {
      key: 'actions',
      align: 'right',
      render: (_, year) => (
        <Space size={0} wrap>
          {!year.current && (
            <Popconfirm
              title={`Make ${year.name} the current year?`}
              onConfirm={() => activate.mutateAsync(year).catch(() => undefined)}
            >
              <Button type="link" size="small">
                Make current
              </Button>
            </Popconfirm>
          )}
          <Button type="link" size="small" onClick={() => openModal(year)}>
            Edit
          </Button>
          {!year.current && (
            <Popconfirm
              title={`Delete ${year.name}?`}
              okButtonProps={{ danger: true }}
              onConfirm={() => remove.mutateAsync(year).catch(() => undefined)}
            >
              <Button type="link" size="small" danger>
                Delete
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={
        <Space size="small" wrap>
          <span>Current year</span>
          {currentYear ? <Tag color="green">{currentYear.name}</Tag> : <Text type="secondary">Not set</Text>}
        </Space>
      }
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal(null)}>
          New academic year
        </Button>
      }
    >
      {years.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(years.error)}
          action={<Button onClick={() => void years.refetch()}>Try again</Button>}
        />
      ) : (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={years.data ?? []}
          loading={years.isLoading}
          pagination={false}
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: <Empty description="No academic years yet" /> }}
        />
      )}

      <Modal
        title={editing ? 'Edit' : 'New academic year'}
        open={editing !== undefined}
        okText={editing ? 'Save' : 'Create'}
        confirmLoading={save.isPending}
        onOk={() => form.submit()}
        onCancel={() => setEditing(undefined)}
        forceRender
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item name="name" label="Academic year" rules={[{ required: true, whitespace: true }, { max: 20 }]}>
            <Input placeholder="2026" />
          </Form.Item>
          <Form.Item
            name="range"
            label="Start and end"
            rules={[
              { required: true },
              {
                validator: (_, range?: [Dayjs, Dayjs]) =>
                  !range || range[1].isAfter(range[0], 'day')
                    ? Promise.resolve()
                    : Promise.reject(new Error('End date must be after start date')),
              },
            ]}
          >
            <DatePicker.RangePicker style={{ width: '100%' }} format="DD MMM YYYY" inputReadOnly />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
