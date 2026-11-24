import type { ThemeConfig } from 'antd';

// One place for every colour, so screens never hard-code hex values.
export const colors = {
  primary: '#5645d4',
  primaryPressed: '#4534b3',
  ink: '#1a1a1a',
  charcoal: '#37352f',
  slate: '#5d5b54',
  steel: '#787671',
  surface: '#f6f5f4',
  surfaceSoft: '#fafaf9',
  hairline: '#e5e3df',
  success: '#1aae39',
  warning: '#dd5b00',
  error: '#e03131',
  tintSky: '#dcecfa',
  tintMint: '#d9f3e1',
  tintPeach: '#ffe8d4',
  tintLavender: '#e6e0f5',
  tintRose: '#fde0ec',
  tintYellow: '#fef7d6',
} as const;

export const chartPalette = ['#5645d4', '#2a9d99', '#dd5b00', '#ff64c8', '#0075de', '#1aae39', '#7b3ff2'];

export const layoutSizes = {
  siderWidth: 248,
  headerHeight: 60,
  contentMaxWidth: 1320,
} as const;

export const theme: ThemeConfig = {
  token: {
    colorPrimary: colors.primary,
    colorSuccess: colors.success,
    colorWarning: colors.warning,
    colorError: colors.error,
    colorInfo: colors.primary,
    colorLink: colors.primary,
    colorTextBase: colors.ink,
    colorText: colors.ink,
    colorTextSecondary: colors.slate,
    colorTextTertiary: colors.steel,
    colorBorder: colors.hairline,
    colorBorderSecondary: colors.hairline,
    colorBgLayout: '#ffffff',
    colorBgContainer: '#ffffff',
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
    fontSize: 14,
    borderRadius: 8,
    controlHeight: 36,
  },
  components: {
    Button: {
      primaryShadow: 'none',
      defaultShadow: 'none',
      dangerShadow: 'none',
      fontWeight: 500,
    },
    Card: { borderRadiusLG: 12 },
    Layout: {
      bodyBg: '#ffffff',
      headerBg: '#ffffff',
      siderBg: colors.surface,
      headerHeight: layoutSizes.headerHeight,
      headerPadding: '0 24px',
    },
    Menu: {
      itemBg: 'transparent',
      itemColor: colors.charcoal,
      itemHoverBg: colors.hairline,
      itemSelectedBg: '#ffffff',
      itemSelectedColor: colors.ink,
      itemBorderRadius: 8,
      activeBarBorderWidth: 0,
    },
  },
};
