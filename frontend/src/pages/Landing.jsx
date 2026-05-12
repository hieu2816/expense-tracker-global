import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { message } from 'antd';
import {
    TrendingUp, Wallet, PieChart, ArrowRight,
    BarChart2, ShieldCheck, Zap, Sun, Moon,
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import '../styles/landing.css';

const features = [
    {
        icon: <TrendingUp size={24} />,
        title: 'Track every expense',
        description: 'Log transactions in seconds. Categorize automatically. Know exactly where your money goes — every day, every month.',
    },
    {
        icon: <BarChart2 size={24} />,
        title: 'Sync with your bank (On-going)',
        description: 'Connect your bank account and transactions flow in automatically. No manual entry needed. Always up to date.',
    },
    {
        icon: <PieChart size={24} />,
        title: 'Insights that matter',
        description: 'Charts and reports show your spending patterns. Spot trends, find savings, and reach your goals faster.',
    },
];

const stats = [
    { number: '10,000+', label: 'Active users' },
    { number: '£2M+', label: 'Transactions tracked' },
    { number: '99.9%', label: 'Uptime' },
];

export default function Landing({ onToggleTheme, themeMode }) {
    const [activeTab, setActiveTab] = useState('login');
    const [loginForm, setLoginForm] = useState({ email: '', password: '' });
    const [registerForm, setRegisterForm] = useState({ fullName: '', email: '', password: '' });
    const [loading, setLoading] = useState(false);
    const [errors, setErrors] = useState({});
    const { login, register } = useAuth();
    const navigate = useNavigate();

    // Sync data-theme when prop changes
    useEffect(() => {
        document.documentElement.setAttribute('data-theme', themeMode);
    }, [themeMode]);

    const handleLogin = async (e) => {
        e.preventDefault();
        setErrors({});
        if (!loginForm.email || !loginForm.password) {
            setErrors({ password: 'Please fill in all fields' });
            return;
        }
        setLoading(true);
        try {
            await login(loginForm.email, loginForm.password);
            message.success('Welcome back!');
            navigate('/app');
        } catch (error) {
            const msg = error.response?.data?.error || error.response?.data || 'Login failed';
            setErrors({ password: typeof msg === 'string' ? msg : 'Invalid credentials' });
        } finally {
            setLoading(false);
        }
    };

    const handleRegister = async (e) => {
        e.preventDefault();
        setErrors({});
        if (!registerForm.fullName || !registerForm.email || !registerForm.password) {
            setErrors({ password: 'Please fill in all fields' });
            return;
        }
        if (registerForm.password.length < 8) {
            setErrors({ password: 'Password must be at least 8 characters' });
            return;
        }
        setLoading(true);
        try {
            await register(registerForm.email, registerForm.password, registerForm.fullName);
            message.success('Account created! Please sign in.');
            setActiveTab('login');
            setLoginForm({ email: registerForm.email, password: '' });
            setRegisterForm({ fullName: '', email: '', password: '' });
        } catch (error) {
            const errs = error.response?.data;
            if (typeof errs === 'object' && errs) {
                const first = Object.values(errs)[0];
                setErrors({ password: Array.isArray(first) ? first[0] : first });
            } else {
                setErrors({ password: 'Registration failed. Please try again.' });
            }
        } finally {
            setLoading(false);
        }
    };

    const scrollToAuth = () => {
        document.getElementById('auth')?.scrollIntoView({ behavior: 'smooth' });
    };

    const scrollToFeatures = () => {
        document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' });
    };

    return (
        <div className="landing">
            {/* ── SEO: Head is in index.html ── */}

            {/* ── Hero ── */}
            <section className="lp-hero">
                <div className="lp-container">
                    <div className="lp-hero-inner">
                        <div className="lp-badge">
                            <span className="lp-badge-dot" />
                            Free to start — no credit card needed
                        </div>
                        <h1>
                            Take control of your<br />
                            <em>finances. Effortlessly.</em>
                        </h1>
                        <p className="lp-hero-sub">
                            Track expenses, sync your bank account, and understand your spending — all in one beautifully simple app. Built for people who want clarity, not complexity.
                        </p>
                        <div className="lp-hero-actions">
                            <button className="lp-btn-primary" onClick={scrollToAuth}>
                                Start Free <ArrowRight size={16} />
                            </button>
                            <button className="lp-btn-secondary" onClick={scrollToFeatures}>
                                See how it works
                            </button>
                        </div>
                    </div>
                </div>
            </section>

            {/* ── Stats bar ── */}
            <div className="lp-stats">
                <div className="lp-container">
                    <div className="lp-stats-grid">
                        {stats.map((s) => (
                            <div key={s.label}>
                                <div className="lp-stat-number">{s.number}</div>
                                <div className="lp-stat-label">{s.label}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* ── Features ── */}
            <section className="lp-features" id="features">
                <div className="lp-container">
                    <div className="lp-section-label">Features</div>
                    <h2 className="lp-section-title">Everything you need to manage your money</h2>
                    <p className="lp-section-sub">Powerful features, zero complexity. SpendWiser does the heavy lifting so you can focus on living.</p>

                    <div className="lp-features-grid">
                        {features.map((f) => (
                            <div className="lp-feature-card" key={f.title}>
                                <div className="lp-feature-icon">{f.icon}</div>
                                <h3>{f.title}</h3>
                                <p>{f.description}</p>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* ── Trust signals ── */}
            <section style={{ padding: '60px 0', borderBottom: '1px solid var(--lp-border)' }}>
                <div className="lp-container">
                    <div style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                        gap: '24px',
                    }}>
                        {[
                            { icon: <ShieldCheck size={20} />, text: 'Bank-level security. Your data is encrypted end-to-end.' },
                            { icon: <Zap size={20} />, text: 'Real-time bank sync. Transactions appear as they happen.' },
                            { icon: <Wallet size={20} />, text: '100% free to use. Always. No hidden fees, no premium traps.' },
                        ].map((item) => (
                            <div key={item.text} style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
                                <div style={{
                                    width: '36px', height: '36px', borderRadius: '8px',
                                    background: 'var(--lp-primary-light)', color: 'var(--lp-primary)',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    flexShrink: 0, border: '1px solid rgba(28,202,91,0.15)',
                                }}>
                                    {item.icon}
                                </div>
                                <p style={{ fontSize: '13px', color: 'var(--lp-text-secondary)', lineHeight: '1.5', margin: 0 }}>
                                    {item.text}
                                </p>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* ── Auth ── */}
            <section className="lp-auth-section" id="auth">
                <div className="lp-container">
                    <div style={{ textAlign: 'center', marginBottom: '32px' }}>
                        <h2 style={{ fontSize: '28px', fontWeight: 700, color: 'var(--lp-text)', marginBottom: '8px', letterSpacing: '-0.02em' }}>
                            {activeTab === 'login' ? 'Welcome back' : 'Create your account'}
                        </h2>
                        <p style={{ color: 'var(--lp-text-secondary)', fontSize: '15px' }}>
                            {activeTab === 'login'
                                ? 'Sign in to continue tracking your finances.'
                                : 'Start tracking your finances in under 30 seconds.'}
                        </p>
                    </div>

                    <div className="lp-auth-wrapper">
                        <div className="lp-auth-tabs">
                            <button
                                className={`lp-auth-tab${activeTab === 'login' ? ' active' : ''}`}
                                onClick={() => { setActiveTab('login'); setErrors({}); }}
                            >
                                Sign In
                            </button>
                            <button
                                className={`lp-auth-tab${activeTab === 'register' ? ' active' : ''}`}
                                onClick={() => { setActiveTab('register'); setErrors({}); }}
                            >
                                Create Account
                            </button>
                        </div>

                        {/* Login Form */}
                        {activeTab === 'login' && (
                            <form className="lp-auth-form" onSubmit={handleLogin}>
                                <div className="lp-form-group">
                                    <label htmlFor="login-email">Email address</label>
                                    <input
                                        id="login-email"
                                        type="email"
                                        placeholder="you@example.com"
                                        value={loginForm.email}
                                        onChange={(e) => setLoginForm((f) => ({ ...f, email: e.target.value }))}
                                        autoComplete="email"
                                    />
                                </div>
                                <div className="lp-form-group">
                                    <label htmlFor="login-password">Password</label>
                                    <input
                                        id="login-password"
                                        type="password"
                                        placeholder="Your password"
                                        value={loginForm.password}
                                        onChange={(e) => setLoginForm((f) => ({ ...f, password: e.target.value }))}
                                        autoComplete="current-password"
                                    />
                                </div>
                                {errors.password && (
                                    <p className="lp-form-error" style={{ marginBottom: '12px' }}>{errors.password}</p>
                                )}
                                <button
                                    type="submit"
                                    className="lp-submit-btn"
                                    disabled={loading}
                                >
                                    {loading ? 'Signing in...' : 'Sign In'}
                                </button>
                            </form>
                        )}

                        {/* Register Form */}
                        {activeTab === 'register' && (
                            <form className="lp-auth-form" onSubmit={handleRegister}>
                                <div className="lp-form-group">
                                    <label htmlFor="reg-name">Full name</label>
                                    <input
                                        id="reg-name"
                                        type="text"
                                        placeholder="Alex Johnson"
                                        value={registerForm.fullName}
                                        onChange={(e) => setRegisterForm((f) => ({ ...f, fullName: e.target.value }))}
                                        autoComplete="name"
                                    />
                                </div>
                                <div className="lp-form-group">
                                    <label htmlFor="reg-email">Email address</label>
                                    <input
                                        id="reg-email"
                                        type="email"
                                        placeholder="you@example.com"
                                        value={registerForm.email}
                                        onChange={(e) => setRegisterForm((f) => ({ ...f, email: e.target.value }))}
                                        autoComplete="email"
                                    />
                                </div>
                                <div className="lp-form-group">
                                    <label htmlFor="reg-password">Password</label>
                                    <input
                                        id="reg-password"
                                        type="password"
                                        placeholder="Min. 8 characters"
                                        value={registerForm.password}
                                        onChange={(e) => setRegisterForm((f) => ({ ...f, password: e.target.value }))}
                                        autoComplete="new-password"
                                    />
                                </div>
                                {errors.password && (
                                    <p className="lp-form-error" style={{ marginBottom: '12px' }}>{errors.password}</p>
                                )}
                                <button
                                    type="submit"
                                    className="lp-submit-btn"
                                    disabled={loading}
                                >
                                    {loading ? 'Creating account...' : 'Create Free Account'}
                                </button>
                            </form>
                        )}
                    </div>
                </div>
            </section>

            {/* ── Footer ── */}
            <footer className="lp-footer">
                <div className="lp-container" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px', fontWeight: 600, color: 'var(--lp-text)' }}>
                        <TrendingUp size={16} style={{ color: 'var(--lp-primary)' }} />
                        SpendWiser
                    </div>
                    <div style={{ display: 'flex', gap: '20px', alignItems: 'center' }}>
                        <a href="#">Privacy Policy</a>
                        <a href="#">Terms of Service</a>
                        <button
                            onClick={onToggleTheme}
                            style={{
                                background: 'none', border: '1px solid var(--lp-border)',
                                borderRadius: '8px', width: '32px', height: '32px',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                cursor: 'pointer', color: 'var(--lp-text-secondary)',
                            }}
                            aria-label="Toggle theme"
                        >
                            {themeMode === 'dark' ? <Sun size={15} /> : <Moon size={15} />}
                        </button>
                    </div>
                </div>
            </footer>
        </div>
    );
}
