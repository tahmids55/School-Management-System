import { useEffect } from 'react';
import { Button, Col, DatePicker, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { errorMessage, fieldErrors } from '../../api/client';
import { classKeys } from '../../api/classes';
import {
  createStudent,
  studentKeys,
  updateStudent,
  type Gender,
  type Student,
  type StudentRequest,
} from '../../api/students';
import { showTemporaryPassword } from '../../components/common';
import { useClasses } from '../../hooks/useClasses';
import { feedback } from '../../utils/feedback';
import { toIsoDate } from '../../utils/format';
import { schoolToday } from '../../utils/schoolDate';

const PHONE_RULE = { pattern: /^\+?[0-9]{10,15}$/, message: '10-15 digits, optional +' };

/** What the form holds: like StudentRequest, but dates are dayjs values for the DatePicker. */
interface FormValues extends Omit<StudentRequest, 'dateOfBirth' | 'admissionDate'> {
  dateOfBirth?: Dayjs;
  admissionDate?: Dayjs;
}

interface Props {
  open: boolean;
  /** The student to edit; undefined = admit a new student. */
  student?: Student;
  onClose: () => void;
  onSaved?: (student: Student) => void;
}

/** Blank text means "not given": the API stores null. */
function blankToNull(value?: string | null): string | null {
  return value && value.trim() ? value.trim() : null;
}

export default function StudentFormDrawer({ open, student, onClose, onSaved }: Props) {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FormValues>();
  const classes = useClasses();
  const editing = student !== undefined;
  const classId = Form.useWatch('classId', form);
  const createAccount = Form.useWatch('createAccount', form);

  // Fill the form each time the drawer opens.
  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (student) {
      form.setFieldsValue({
        ...student,
        // undefined (not null) so the Select shows its "Not assigned" placeholder
        classId: student.classId ?? undefined,
        dateOfBirth: dayjs(student.dateOfBirth),
        admissionDate: dayjs(student.admissionDate),
      });
    } else {
      form.setFieldsValue({ admissionDate: dayjs(schoolToday()) });
    }
  }, [open, student, form]);

  const save = useMutation({
    mutationFn: (values: FormValues) => {
      const request: StudentRequest = {
        ...values,
        dateOfBirth: toIsoDate(values.dateOfBirth) ?? '',
        admissionDate: toIsoDate(values.admissionDate) ?? '',
        email: blankToNull(values.email),
        phone: blankToNull(values.phone),
        address: blankToNull(values.address),
        classId: values.classId ?? null,
        rollNo: values.classId ? (values.rollNo ?? null) : null,
      };
      if (editing) return updateStudent(student.id, request);
      return createStudent({ ...request, createAccount: Boolean(values.createAccount) }).then((created) => {
        // The server returns the temporary password only in this response: show it now, once.
        if (created.account) {
          showTemporaryPassword(created.account.username, created.account.temporaryPassword);
        }
        return created.student;
      });
    },
    onSuccess: (saved) => {
      feedback.message.success(editing ? 'Student updated' : `${saved.fullName} admitted`);
      void queryClient.invalidateQueries({ queryKey: studentKeys.all });
      void queryClient.invalidateQueries({ queryKey: classKeys.all });
      onSaved?.(saved);
      onClose();
    },
    onError: (error) => {
      form.setFields(fieldErrors(error) as Parameters<typeof form.setFields>[0]);
      feedback.message.error(errorMessage(error));
    },
  });

  // Classes of the current year. When editing, the student's own class is kept even if it is from another year.
  const classOptions = (classes.data ?? []).map((c) => ({ value: c.id, label: c.displayName }));
  if (student?.classId && !classOptions.some((option) => option.value === student.classId)) {
    classOptions.unshift({ value: student.classId, label: student.className ?? `Class ${student.classId}` });
  }
  const genderOptions: { value: Gender; label: string }[] = [
    { value: 'MALE', label: 'Male' },
    { value: 'FEMALE', label: 'Female' },
    { value: 'OTHER', label: 'Other' },
  ];
  // Birthdays must be in the past and admission dates cannot be in the future (school time zone).
  const today = dayjs(schoolToday());

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={editing ? `Edit ${student.fullName}` : 'Admit student'}
      width="min(720px, 100vw)"
      forceRender
      footer={
        <Space style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
            {editing ? 'Save' : 'Admit student'}
          </Button>
        </Space>
      }
    >
      <Form
        form={form}
        layout="vertical"
        requiredMark="optional"
        onFinish={(values) => save.mutate(values)}
        // A new class means a new roll: clear the old one so the server picks the next free roll.
        onValuesChange={(changed: Partial<FormValues>) => {
          if ('classId' in changed) form.setFieldsValue({ rollNo: undefined });
        }}
      >
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="admissionNo"
              label="Admission no."
              rules={[{ required: true, whitespace: true }, { max: 20 }]}
              extra={editing ? 'The admission number cannot be changed.' : undefined}
            >
              <Input placeholder="ADM-2026-101" disabled={editing} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="admissionDate" label="Admission date" rules={[{ required: true }]}>
              <DatePicker style={{ width: '100%' }} disabledDate={(date) => date.isAfter(today, 'day')} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="firstName" label="First name" rules={[{ required: true, whitespace: true }, { max: 60 }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="lastName" label="Last name" rules={[{ required: true, whitespace: true }, { max: 60 }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="gender" label="Gender" rules={[{ required: true }]}>
              <Select options={genderOptions} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="dateOfBirth" label="Date of birth" rules={[{ required: true }]}>
              <DatePicker style={{ width: '100%' }} disabledDate={(date) => !date.isBefore(today, 'day')} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="classId" label="Class">
              <Select
                allowClear
                placeholder="Not assigned"
                loading={classes.isLoading}
                options={classOptions}
              />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="rollNo"
              label="Roll no."
              extra={classId ? 'Leave empty for the next free roll.' : undefined}
            >
              <InputNumber min={1} max={500} precision={0} style={{ width: '100%' }} disabled={!classId} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="email"
              label="Email"
              dependencies={['createAccount']}
              rules={[
                { required: Boolean(createAccount), whitespace: true, message: 'An email address is required to create a student login' },
                { type: 'email' },
                { max: 120 },
              ]}
            >
              <Input type="email" autoComplete="off" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="phone" label="Phone" rules={[PHONE_RULE]}>
              <Input placeholder="+8801..." inputMode="tel" />
            </Form.Item>
          </Col>
          <Col xs={24}>
            <Form.Item name="address" label="Address" rules={[{ max: 255 }]}>
              <Input.TextArea autoSize={{ minRows: 1, maxRows: 3 }} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="guardianName"
              label="Guardian name"
              rules={[{ required: true, whitespace: true }, { max: 120 }]}
            >
              <Input />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="guardianRelation"
              label="Relation"
              rules={[{ required: true, whitespace: true }, { max: 30 }]}
            >
              <Input placeholder="Mother" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="guardianPhone"
              label="Guardian phone"
              rules={[{ required: true, message: 'Enter the guardian phone' }, PHONE_RULE]}
            >
              <Input placeholder="+8801..." inputMode="tel" />
            </Form.Item>
          </Col>
          {/* Only when admitting: an admin is the only one who can create a student login. */}
          {!editing && (
            <Col xs={24}>
              <Form.Item
                name="createAccount"
                label="Create student login"
                valuePropName="checked"
                extra="Username = admission no. in lower case. The temporary password is shown once."
              >
                <Switch />
              </Form.Item>
            </Col>
          )}
        </Row>
      </Form>
    </Drawer>
  );
}
