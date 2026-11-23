/**
 * Agreed sidebar order. A feature's *.routes.tsx uses the matching entry, e.g.
 * export const menu = { label: 'Students', icon: <TeamOutlined />, order: MENU_ORDER.Students };
 * Entries appear only once their feature adds its routes file.
 * (Kept in its own file without imports, so route files can import it without a cycle.)
 */
export const MENU_ORDER = {
  Dashboard: 10,
  Students: 20,
  Teachers: 30,
  Academics: 40,
  Attendance: 50,
  'Exams & Marks': 60,
  Results: 70,
  Fees: 80,
  'My Attendance': 90,
  'My Results': 100,
  'My Fees': 110,
  Notices: 120,
  'User Accounts': 130,
  'Audit Log': 140,
} as const;
