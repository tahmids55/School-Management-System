import { Link } from 'react-router-dom';
import { Card, Empty, List, Typography } from 'antd';
import { PushpinFilled } from '@ant-design/icons';
import type { NoticeResponse } from '../../api/notices';
import { colors } from '../../theme';
import { formatDate } from '../../utils/format';

const { Text } = Typography;

/** The newest notices the user may see (the server already picks and limits them). */
export default function NoticeList({ notices }: { notices: NoticeResponse[] }) {
  return (
    <Card title="Latest notices" extra={<Link to="/notices">View all</Link>} style={{ height: '100%' }}>
      {notices.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No notices" />
      ) : (
        <List
          dataSource={notices}
          rowKey="id"
          renderItem={(notice) => (
            <List.Item style={{ paddingInline: 0 }}>
              <List.Item.Meta
                title={
                  <span>
                    {notice.pinned && <PushpinFilled style={{ color: colors.primary, marginRight: 6 }} />}
                    {notice.title}
                  </span>
                }
                description={
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {formatDate(notice.publishedAt)}
                    {notice.author ? ` · ${notice.author}` : ''}
                  </Text>
                }
              />
            </List.Item>
          )}
        />
      )}
    </Card>
  );
}
