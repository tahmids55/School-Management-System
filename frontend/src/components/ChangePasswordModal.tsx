export interface ChangePasswordModalProps {
  /** Whether the modal is visible. */
  open: boolean;
  /** True after a temporary-password login: the user cannot close the modal. */
  forced?: boolean;
  onClose: () => void;
}

/**
 * Slot for F10 (accounts and passwords). The layout already opens it from the user menu,
 * and keeps it open while user.mustChangePassword is true. F10 only fills this file.
 */
export function ChangePasswordModal(_props: ChangePasswordModalProps) {
  return null;
}
