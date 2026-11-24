import { App } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import type { ModalStaticFunctions } from 'antd/es/modal/confirm';
import type { NotificationInstance } from 'antd/es/notification/interface';

type ModalApi = Omit<ModalStaticFunctions, 'warn'>;

// AntD's message/modal/notification only pick up our theme when they come from App.useApp().
// The bridge copies those instances here so plain functions (outside components) can use them too.
let messageApi: MessageInstance | null = null;
let modalApi: ModalApi | null = null;
let notificationApi: NotificationInstance | null = null;

/** Rendered once inside <App> in main.tsx. Renders nothing. */
export function FeedbackBridge() {
  const app = App.useApp();
  messageApi = app.message;
  modalApi = app.modal;
  notificationApi = app.notification;
  return null;
}

function notReady(): never {
  throw new Error('FeedbackBridge is not mounted');
}

export const feedback = {
  get message(): MessageInstance {
    return messageApi ?? notReady();
  },
  get modal(): ModalApi {
    return modalApi ?? notReady();
  },
  get notification(): NotificationInstance {
    return notificationApi ?? notReady();
  },
};
