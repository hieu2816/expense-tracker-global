import { useState } from 'react';
import { Layout, Avatar, Dropdown, Button } from 'antd';
import {
    UserOutlined,
    LogoutOutlined,
    PlusOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
} from '@ant-design/icons';
import {
    LayoutDashboard,
    Receipt,
    Tags,
    Landmark,
    TrendingUp,
    Sun,
    Moon,
} from 'lucide-react';
import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const { Sider, Header, Content } = Layout;

const NAV_ITEMS = [
    { key: '/app', label: 'Dashboard', icon: <LayoutDashboard size={18} /> },
    { key: '/app/transactions', label: 'Transactions', icon: <Receipt size={18} /> },
    { key: '/app/categories', label: 'Categories', icon: <Tags size={18} /> },
    { key: '/app/banks', label: 'Banks', icon: <Landmark size={18} /> },
    { key: '/app/profile', label: 'Profile', icon: <UserOutlined /> },
];

const PAGE_TITLES = {
    '/app': 'Dashboard',
    '/app/transactions': 'Transactions',
    '/app/categories': 'Categories',
    '/app/banks': 'Banks',
    '/app/profile': 'Profile',
};

export default function AppLayout({ onToggleTheme, themeMode }) {
    const [collapsed, setCollapsed] = useState(false);
    const navigate = useNavigate();
    const location = useLocation();
    const { user, logout } = useAuth();

    const currentTitle = PAGE_TITLES[location.pathname] || 'Dashboard';

    const userMenuItems = [
        {
            key: 'profile',
            icon: <UserOutlined />,
            label: 'Profile',
            onClick: () => navigate('/app/profile'),
        },
        { type: 'divider' },
        {
            key: 'logout',
            icon: <LogoutOutlined />,
            label: 'Logout',
            danger: true,
            onClick: () => {
                logout();
                navigate('/');
            },
        },
    ];

    return (
        <Layout className="app-layout">
            {/* Sidebar */}
            <Sider
                trigger={null}
                collapsible
                collapsed={collapsed}
                width={260}
                style={{
                    background: 'var(--color-bg-card)',
                    position: 'fixed',
                    height: '100vh',
                    left: 0,
                    top: 0,
                    zIndex: 100,
                    overflow: 'auto',
                    borderRight: '1px solid var(--color-border)',
                }}
            >
                {/* Logo */}
                <div className="sidebar-logo" style={{ justifyContent: collapsed ? 'center' : 'flex-start', padding: collapsed ? '0' : '0 20px', gap: '10px' }}>
                    <span style={{ color: 'var(--color-primary)', display: 'flex', alignItems: 'center', flexShrink: 0 }}>
                        <TrendingUp size={20} />
                    </span>
                    {!collapsed && (
                        <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--color-primary)', margin: 0, whiteSpace: 'nowrap' }}>
                            ExpenseTracker
                        </h2>
                    )}
                </div>

                {/* Navigation */}
                <nav>
                    {NAV_ITEMS.map((item) => {
                        const isActive = location.pathname === item.key;
                        return (
                            <div
                                key={item.key}
                                onClick={() => navigate(item.key)}
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '12px',
                                    padding: collapsed ? '10px 0' : '10px 20px',
                                    justifyContent: collapsed ? 'center' : 'flex-start',
                                    margin: '2px 8px',
                                    borderRadius: 'var(--radius-md)',
                                    cursor: 'pointer',
                                    color: isActive ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                                    background: isActive ? 'var(--color-primary-light)' : 'transparent',
                                    fontSize: '14px',
                                    fontWeight: isActive ? 600 : 400,
                                    transition: 'background 0.15s, color 0.15s',
                                    userSelect: 'none',
                                    border: 'none',
                                }}
                                onMouseEnter={(e) => {
                                    if (!isActive) {
                                        e.currentTarget.style.background = 'var(--color-bg-hover)';
                                        e.currentTarget.style.color = 'var(--color-primary)';
                                    }
                                }}
                                onMouseLeave={(e) => {
                                    if (!isActive) {
                                        e.currentTarget.style.background = 'transparent';
                                        e.currentTarget.style.color = 'var(--color-text-secondary)';
                                    }
                                }}
                            >
                                <span style={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>{item.icon}</span>
                                {!collapsed && item.label}
                            </div>
                        );
                    })}
                </nav>

                {/* User footer */}
                {!collapsed && (
                    <div style={{
                        position: 'absolute',
                        bottom: 0,
                        left: 0,
                        right: 0,
                        padding: '16px',
                        borderTop: '1px solid var(--color-border)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px',
                    }}>
                        <Avatar
                            size={32}
                            style={{ backgroundColor: 'var(--color-primary)', flexShrink: 0, fontSize: '13px', fontWeight: 600 }}
                        >
                            {user?.fullName?.[0]?.toUpperCase() || 'U'}
                        </Avatar>
                        <div style={{ overflow: 'hidden', flex: 1 }}>
                            <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--color-text-primary)', lineHeight: 1.3, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                {user?.fullName || 'User'}
                            </div>
                            <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                {user?.email || ''}
                            </div>
                        </div>
                    </div>
                )}
            </Sider>

            {/* Main Area */}
            <Layout style={{
                marginLeft: collapsed ? 80 : 260,
                transition: 'margin-left 0.2s',
                minHeight: '100vh',
                background: 'var(--color-bg-page)',
            }}>
                {/* Header */}
                <Header style={{
                    background: 'var(--color-bg-card)',
                    borderBottom: '1px solid var(--color-border)',
                    padding: '0 24px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    position: 'sticky',
                    top: 0,
                    zIndex: 99,
                    height: 'var(--header-height)',
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                        <Button
                            type="text"
                            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                            onClick={() => setCollapsed(!collapsed)}
                        />
                        <h1 style={{ fontSize: '18px', fontWeight: 700, color: 'var(--color-text-primary)', margin: 0 }}>
                            {currentTitle}
                        </h1>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <button
                            className="theme-toggle-btn"
                            onClick={onToggleTheme}
                            aria-label="Toggle theme"
                            type="button"
                        >
                            {themeMode === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
                        </button>
                        <Button
                            type="primary"
                            icon={<PlusOutlined />}
                            onClick={() => navigate('/app/transactions')}
                        >
                            Add Transaction
                        </Button>
                        <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" trigger={['click']}>
                            <Avatar
                                style={{ backgroundColor: 'var(--color-primary)', cursor: 'pointer', fontSize: '13px', fontWeight: 600 }}
                            >
                                {user?.fullName?.[0]?.toUpperCase() || 'U'}
                            </Avatar>
                        </Dropdown>
                    </div>
                </Header>

                {/* Content */}
                <Content
                    className="app-content"
                    style={{ maxWidth: '1200px', margin: '0 auto', width: '100%' }}
                >
                    <Outlet />
                </Content>
            </Layout>
        </Layout>
    );
}
