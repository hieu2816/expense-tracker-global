import { useState, useEffect } from 'react';
import { Row, Col, Card, Table, Spin } from 'antd';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, BarChart, Bar, XAxis, YAxis, CartesianGrid } from 'recharts';
import { TrendingUp, TrendingDown, Wallet, ArrowRight } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axios';

const COLORS = ['#1cca5b', '#16a34a', '#22c55e', '#4ade80', '#86efac', '#169c4a', '#15803d', '#166534'];

const formatCurrency = (val) =>
    new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' }).format(val || 0);

export default function Dashboard() {
    const [stats, setStats] = useState(null);
    const [recentTxns, setRecentTxns] = useState([]);
    const [categoryData, setCategoryData] = useState([]);
    const [monthlyData, setMonthlyData] = useState([]);
    const [loading, setLoading] = useState(true);
    const navigate = useNavigate();

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setLoading(true);
        try {
            const [dashRes, txnRes, catRes] = await Promise.all([
                api.get('/transactions/dashboard').catch(() => ({ data: null })),
                api.get('/transactions', { params: { page: 0, size: 6 } }).catch(() => ({ data: { content: [] } })),
                api.get('/transactions/category-summary').catch(() => ({ data: [] })),
            ]);
            setStats(dashRes.data);
            setRecentTxns(txnRes.data?.content || []);
            const expenseData = (catRes.data || [])
                .filter((c) => c.type === 'OUT')
                .map((c) => ({ name: c.categoryName, value: c.total }));
            setCategoryData(expenseData);

            // Build monthly bar chart data from category summary
            const monthData = (catRes.data || [])
                .reduce((acc, c) => {
                    const month = new Date().toLocaleString('en-GB', { month: 'short' });
                    const existing = acc.find((a) => a.month === month);
                    if (existing) {
                        if (c.type === 'IN') existing.income += c.total;
                        else existing.expense += c.total;
                    } else {
                        acc.push({ month, income: c.type === 'IN' ? c.total : 0, expense: c.type === 'OUT' ? c.total : 0 });
                    }
                    return acc;
                }, []);
            setMonthlyData(monthData.length ? monthData : [
                { month: 'Jan', income: 0, expense: 0 },
                { month: 'Feb', income: 0, expense: 0 },
                { month: 'Mar', income: 0, expense: 0 },
            ]);
        } catch (error) {
            console.error('Failed to load dashboard:', error);
        } finally {
            setLoading(false);
        }
    };

    const columns = [
        {
            title: '',
            key: 'icon',
            width: 44,
            render: (_, record) => (
                <div style={{
                    width: 36, height: 36, borderRadius: 'var(--radius-md)',
                    background: record.type === 'IN' ? 'rgba(28,202,91,0.1)' : 'rgba(239,68,68,0.1)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: record.type === 'IN' ? 'var(--color-income)' : 'var(--color-expense)',
                    fontSize: '16px',
                }}>
                    {record.type === 'IN' ? '↑' : '↓'}
                </div>
            ),
        },
        {
            title: 'Description',
            dataIndex: 'description',
            key: 'description',
            render: (text, record) => (
                <div>
                    <div style={{ fontWeight: 500, fontSize: '14px' }}>{text || record.category?.name || '—'}</div>
                    <div style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>{record.category?.name}</div>
                </div>
            ),
        },
        {
            title: 'Date',
            dataIndex: 'transactionDate',
            key: 'date',
            width: 90,
            render: (date) => new Date(date).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' }),
        },
        {
            title: 'Amount',
            dataIndex: 'amount',
            key: 'amount',
            align: 'right',
            render: (amount, record) => (
                <span style={{
                    fontWeight: 600,
                    color: record.type === 'IN' ? 'var(--color-income)' : 'var(--color-expense)',
                    fontSize: '14px',
                }}>
                    {record.type === 'IN' ? '+' : '-'}{formatCurrency(amount)}
                </span>
            ),
        },
    ];

    if (loading) {
        return (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '80px' }}>
                <Spin size="large" />
            </div>
        );
    }

    return (
        <div>
            {/* Page Title */}
            <div style={{ marginBottom: '28px' }}>
                <h1 style={{ fontSize: '24px', fontWeight: 800, color: 'var(--color-text-primary)', margin: '0 0 4px' }}>
                    Good {getGreeting()}, {stats?.userName || 'there'} 👋
                </h1>
                <p style={{ fontSize: '14px', color: 'var(--color-text-secondary)', margin: 0 }}>
                    Here's an overview of your finances
                </p>
            </div>

            {/* Stat Cards */}
            <Row gutter={[16, 16]} style={{ marginBottom: '24px' }}>
                <Col xs={24} sm={8}>
                    <div className="stat-card" style={{ cursor: 'default' }}>
                        <div className="stat-header">
                            <div className="stat-icon"><TrendingUp size={18} /></div>
                            <span className="stat-label">Total Income</span>
                        </div>
                        <div className="stat-value income">{formatCurrency(stats?.totalIncome)}</div>
                    </div>
                </Col>
                <Col xs={24} sm={8}>
                    <div className="stat-card">
                        <div className="stat-header">
                            <div className="stat-icon"><TrendingDown size={18} /></div>
                            <span className="stat-label">Total Expense</span>
                        </div>
                        <div className="stat-value expense">{formatCurrency(stats?.totalExpense)}</div>
                    </div>
                </Col>
                <Col xs={24} sm={8}>
                    <div className="stat-card">
                        <div className="stat-header">
                            <div className="stat-icon"><Wallet size={18} /></div>
                            <span className="stat-label">Balance</span>
                        </div>
                        <div className="stat-value">{formatCurrency(stats?.balance)}</div>
                    </div>
                </Col>
            </Row>

            {/* Charts Row */}
            <Row gutter={[16, 16]} style={{ marginBottom: '24px' }}>
                {/* Pie Chart */}
                <Col xs={24} lg={11}>
                    <Card
                        title={<span style={{ fontWeight: 600, fontSize: '15px' }}>Spending by Category</span>}
                        extra={
                            <button
                                onClick={() => navigate('/app/transactions')}
                                style={{
                                    background: 'none', border: 'none', cursor: 'pointer',
                                    color: 'var(--color-primary)', fontSize: '13px', fontWeight: 500,
                                    display: 'flex', alignItems: 'center', gap: '4px',
                                }}
                            >
                                View all <ArrowRight size={13} />
                            </button>
                        }
                        styles={{ body: { padding: '20px' }, header: { borderBottom: '1px solid var(--color-border)', padding: '16px 20px' } }}
                    >
                        {categoryData.length > 0 ? (
                            <ResponsiveContainer width="100%" height={240}>
                                <PieChart>
                                    <Pie
                                        data={categoryData}
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={55}
                                        outerRadius={90}
                                        paddingAngle={3}
                                        dataKey="value"
                                        label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                                        labelLine={{ stroke: 'var(--color-border)', strokeWidth: 1 }}
                                    >
                                        {categoryData.map((_, idx) => (
                                            <Cell key={idx} fill={COLORS[idx % COLORS.length]} strokeWidth={0} />
                                        ))}
                                    </Pie>
                                    <Tooltip formatter={(val) => formatCurrency(val)} contentStyle={{
                                        border: '1px solid var(--color-border)',
                                        borderRadius: '8px',
                                        fontSize: '13px',
                                    }} />
                                </PieChart>
                            </ResponsiveContainer>
                        ) : (
                            <div style={{ textAlign: 'center', padding: '48px', color: 'var(--color-text-muted)', fontSize: '14px' }}>
                                No expense data yet
                            </div>
                        )}
                    </Card>
                </Col>

                {/* Bar Chart */}
                <Col xs={24} lg={13}>
                    <Card
                        title={<span style={{ fontWeight: 600, fontSize: '15px' }}>Income vs Expenses</span>}
                        styles={{ body: { padding: '20px' }, header: { borderBottom: '1px solid var(--color-border)', padding: '16px 20px' } }}
                    >
                        {monthlyData.length > 0 ? (
                            <ResponsiveContainer width="100%" height={240}>
                                <BarChart data={monthlyData} barGap={4}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                                    <XAxis dataKey="month" tick={{ fontSize: 12, fill: 'var(--color-text-muted)' }} axisLine={false} tickLine={false} />
                                    <YAxis tick={{ fontSize: 12, fill: 'var(--color-text-muted)' }} axisLine={false} tickLine={false} tickFormatter={(v) => `£${v}`} />
                                    <Tooltip formatter={(val) => formatCurrency(val)} contentStyle={{
                                        border: '1px solid var(--color-border)',
                                        borderRadius: '8px',
                                        fontSize: '13px',
                                    }} />
                                    <Bar dataKey="income" fill="#1cca5b" radius={[4, 4, 0, 0]} name="Income" />
                                    <Bar dataKey="expense" fill="#ef4444" radius={[4, 4, 0, 0]} name="Expense" />
                                </BarChart>
                            </ResponsiveContainer>
                        ) : (
                            <div style={{ textAlign: 'center', padding: '48px', color: 'var(--color-text-muted)', fontSize: '14px' }}>
                                No data to display yet
                            </div>
                        )}
                    </Card>
                </Col>
            </Row>

            {/* Recent Transactions */}
            <Card
                title={<span style={{ fontWeight: 600, fontSize: '15px' }}>Recent Transactions</span>}
                extra={
                    <button
                        onClick={() => navigate('/app/transactions')}
                        style={{
                            background: 'none', border: 'none', cursor: 'pointer',
                            color: 'var(--color-primary)', fontSize: '13px', fontWeight: 500,
                            display: 'flex', alignItems: 'center', gap: '4px',
                        }}
                    >
                        See all <ArrowRight size={13} />
                    </button>
                }
                styles={{ body: { padding: '0' }, header: { borderBottom: '1px solid var(--color-border)', padding: '16px 20px' } }}
            >
                {recentTxns.length === 0 ? (
                    <div style={{ textAlign: 'center', padding: '40px', color: 'var(--color-text-muted)', fontSize: '14px' }}>
                        No transactions yet — add your first one!
                    </div>
                ) : (
                    <Table
                        columns={columns}
                        dataSource={recentTxns}
                        rowKey="id"
                        pagination={false}
                        size="middle"
                        rowStyle={{ cursor: 'default' }}
                    />
                )}
            </Card>
        </div>
    );
}

function getGreeting() {
    const hour = new Date().getHours();
    if (hour < 12) return 'morning';
    if (hour < 18) return 'afternoon';
    return 'evening';
}