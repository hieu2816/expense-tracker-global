// Ant Design theme configuration — light + dark mode support

const baseToken = {
    colorPrimary: '#1cca5b',
    borderRadius: 8,
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
};

export const lightTheme = {
    algorithm: undefined,
    token: {
        ...baseToken,
        colorBgContainer: '#ffffff',
        colorBgElevated: '#ffffff',
        colorBgLayout: '#F8F9FA',
        colorBorder: '#E5E7EB',
        colorText: '#1F1F1F',
        colorTextSecondary: '#6B7280',
        colorBgSpotlight: '#F3F4F6',
        colorTextPlaceholder: '#9CA3AF',
    },
    components: {
        Button: { borderRadius: 8 },
        Card: { borderRadius: 8 },
        Input: { borderRadius: 8 },
        Select: { borderRadius: 8 },
        DatePicker: { borderRadius: 8 },
        InputNumber: { borderRadius: 8 },
        Table: { borderRadius: 8 },
        Modal: { borderRadius: 8 },
        Menu: { borderRadius: 8 },
    },
};

export const darkTheme = {
    algorithm: undefined,
    token: {
        ...baseToken,
        colorBgContainer: '#1E293B',
        colorBgElevated: '#334155',
        colorBgLayout: '#0F172A',
        colorBorder: '#334155',
        colorText: '#F1F5F9',
        colorTextSecondary: '#94A3B8',
        colorTextPlaceholder: '#64748B',
        colorTextQuaternary: '#64748B',
        colorBgSpotlight: '#1E293B',
    },
    components: {
        Button: { borderRadius: 8 },
        Card: { borderRadius: 8 },
        Input: { borderRadius: 8 },
        Select: { borderRadius: 8 },
        DatePicker: { borderRadius: 8 },
        InputNumber: { borderRadius: 8 },
        Table: { borderRadius: 8 },
        Modal: { borderRadius: 8 },
        Menu: { borderRadius: 8 },
    },
};
