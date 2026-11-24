CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'TEACHER', 'STUDENT')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX idx_users_role ON users (role);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE academic_years (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    current BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT chk_academic_year_dates CHECK (end_date > start_date)
);

CREATE UNIQUE INDEX uq_academic_years_current ON academic_years (current) WHERE current;

CREATE TABLE teachers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE REFERENCES users (id),
    employee_no VARCHAR(20) NOT NULL UNIQUE,
    first_name VARCHAR(60) NOT NULL,
    last_name VARCHAR(60) NOT NULL,
    email VARCHAR(120) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    designation VARCHAR(60) NOT NULL,
    joining_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'RESIGNED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX idx_teachers_name ON teachers (lower(first_name), lower(last_name));

CREATE TABLE school_classes (
    id BIGSERIAL PRIMARY KEY,
    academic_year_id BIGINT NOT NULL REFERENCES academic_years (id),
    name VARCHAR(30) NOT NULL,
    section VARCHAR(10) NOT NULL,
    room VARCHAR(20),
    capacity INT NOT NULL CHECK (capacity > 0),
    class_teacher_id BIGINT REFERENCES teachers (id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT uq_class_per_year UNIQUE (academic_year_id, name, section)
);

CREATE INDEX idx_school_classes_teacher ON school_classes (class_teacher_id);

CREATE TABLE subjects (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    full_marks INT NOT NULL CHECK (full_marks > 0),
    pass_marks INT NOT NULL CHECK (pass_marks >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT chk_pass_le_full CHECK (pass_marks <= full_marks)
);

CREATE TABLE class_subjects (
    id BIGSERIAL PRIMARY KEY,
    school_class_id BIGINT NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES subjects (id),
    teacher_id BIGINT REFERENCES teachers (id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT uq_class_subject UNIQUE (school_class_id, subject_id)
);

CREATE INDEX idx_class_subjects_teacher ON class_subjects (teacher_id);
CREATE INDEX idx_class_subjects_subject ON class_subjects (subject_id);

CREATE TABLE students (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE REFERENCES users (id),
    admission_no VARCHAR(20) NOT NULL UNIQUE,
    first_name VARCHAR(60) NOT NULL,
    last_name VARCHAR(60) NOT NULL,
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    date_of_birth DATE NOT NULL,
    email VARCHAR(120) UNIQUE,
    phone VARCHAR(20),
    address VARCHAR(255),
    guardian_name VARCHAR(120) NOT NULL,
    guardian_phone VARCHAR(20) NOT NULL,
    guardian_relation VARCHAR(30) NOT NULL,
    school_class_id BIGINT REFERENCES school_classes (id),
    roll_no INT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'GRADUATED', 'TRANSFERRED')),
    admission_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT uq_roll_per_class UNIQUE (school_class_id, roll_no)
);

CREATE INDEX idx_students_class ON students (school_class_id);
CREATE INDEX idx_students_status ON students (status);
CREATE INDEX idx_students_name ON students (lower(first_name), lower(last_name));

CREATE TABLE attendance_records (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    school_class_id BIGINT NOT NULL REFERENCES school_classes (id),
    attendance_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
    remarks VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT uq_attendance_per_day UNIQUE (student_id, attendance_date)
);

CREATE INDEX idx_attendance_class_date ON attendance_records (school_class_id, attendance_date);

CREATE TABLE exams (
    id BIGSERIAL PRIMARY KEY,
    academic_year_id BIGINT NOT NULL REFERENCES academic_years (id),
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT chk_exam_dates CHECK (end_date >= start_date)
);

CREATE INDEX idx_exams_year ON exams (academic_year_id);

CREATE TABLE marks (
    id BIGSERIAL PRIMARY KEY,
    exam_id BIGINT NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES subjects (id),
    marks_obtained NUMERIC(5,2) CHECK (marks_obtained >= 0),
    absent BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT uq_mark UNIQUE (exam_id, student_id, subject_id)
);

CREATE INDEX idx_marks_student ON marks (student_id);
CREATE INDEX idx_marks_subject ON marks (subject_id);

CREATE TABLE fee_invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_no VARCHAR(30) NOT NULL UNIQUE,
    student_id BIGINT NOT NULL REFERENCES students (id),
    title VARCHAR(120) NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    discount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
    amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT chk_discount_le_amount CHECK (discount <= amount)
);

CREATE INDEX idx_fee_invoices_student ON fee_invoices (student_id);
CREATE INDEX idx_fee_invoices_status_due ON fee_invoices (status, due_date);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES fee_invoices (id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    method VARCHAR(20) NOT NULL CHECK (method IN ('CASH', 'BKASH', 'BANK_TRANSFER')),
    transaction_ref VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL UNIQUE,
    paid_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX idx_payments_invoice ON payments (invoice_id);
CREATE INDEX idx_payments_paid_at ON payments (paid_at);

CREATE TABLE notices (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    body TEXT NOT NULL,
    audience VARCHAR(20) NOT NULL CHECK (audience IN ('ALL', 'TEACHERS', 'STUDENTS')),
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX idx_notices_published ON notices (published_at DESC);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    link VARCHAR(200),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, read_at);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(50),
    details VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_logs_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
