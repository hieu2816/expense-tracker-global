import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Empty, List, Modal, Popconfirm, Spin, Table, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { RefreshCw, History, Unlink, Building2 } from 'lucide-react';
import api from '../api/axios';

const statusColor = {
    ACTIVE: 'green',
    PENDING: 'orange',
    REMOVED: 'red',
    REQUIRES_UPDATE: 'gold',
    EXPIRED: 'red',
    ERROR: 'red',
    LINKED: 'green',
};

export default function BankAccounts() {
    const [accounts, setAccounts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [linking, setLinking] = useState(false);
    const [plaidReady, setPlaidReady] = useState(false);
    const [syncing, setSyncing] = useState({});
    const [historyModalOpen, setHistoryModalOpen] = useState(false);
    const [selectedAccount, setSelectedAccount] = useState(null);
    const [syncHistory, setSyncHistory] = useState([]);
    const [historyLoading, setHistoryLoading] = useState(false);
    useEffect(() => {
        loadAccounts();
    }, []);

    useEffect(() => {
        const scriptId = 'plaid-link-script';
        if (window.Plaid) {
            setPlaidReady(true);
            return undefined;
        }

        if (document.getElementById(scriptId)) {
            return undefined;
        }

        const script = document.createElement('script');
        script.id = scriptId;
        script.src = 'https://cdn.plaid.com/link/v2/stable/link-initialize.js';
        script.async = true;
        script.onload = () => setPlaidReady(true);
        script.onerror = () => message.error('Failed to load Plaid Link');
        document.body.appendChild(script);

        return () => {
            // Keep the script loaded for the full app session.
        };
    }, []);

    const loadAccounts = async () => {
        setLoading(true);
        try {
            const response = await api.get('/banks');
            setAccounts(response.data || []);
        } catch {
            message.error('Failed to load bank accounts');
        } finally {
            setLoading(false);
        }
    };

    const openPlaidLink = async () => {
        setLinking(true);
        try {
            const response = await api.post('/banks/link');
            const linkToken = response.data?.linkToken;
            if (!linkToken) {
                message.error('Missing Plaid link token');
                return;
            }

            if (!window.Plaid) {
                message.error('Plaid Link is still loading. Please try again.');
                return;
            }

            const handler = window.Plaid.create({
                token: linkToken,
                onSuccess: async (publicToken) => {
                    await completePlaidLink(publicToken);
                    handler.exit();
                },
                onExit: () => {
                    message.info('Plaid Link closed');
                },
            });

            handler.open();
        } catch {
            message.error('Failed to start Plaid Link');
        } finally {
            setLinking(false);
        }
    };

    const completePlaidLink = async (publicToken) => {
        try {
            await api.post('/banks/link/complete', { publicToken });
            message.success('Bank linked');
            loadAccounts();
        } catch {
            message.error('Failed to complete bank linking');
        }
    };

    const handleSync = async (id) => {
        setSyncing((s) => ({ ...s, [id]: true }));
        try {
            const response = await api.post(`/banks/${id}/sync`);
            message.success(`Synced! ${response.data?.transactionsNew || 0} new transactions`);
            loadAccounts();
        } catch {
            message.error('Failed to sync bank');
        } finally {
            setSyncing((s) => ({ ...s, [id]: false }));
        }
    };

    const handleUnlink = async (id) => {
        try {
            await api.delete(`/banks/${id}`);
            message.success('Bank unlinked');
            loadAccounts();
        } catch {
            message.error('Failed to unlink bank');
        }
    };

    const showHistory = async (account) => {
        setSelectedAccount(account);
        setHistoryModalOpen(true);
        setHistoryLoading(true);
        try {
            const response = await api.get(`/banks/${account.id}/sync-history`, { params: { page: 0, size: 10 } });
            setSyncHistory(response.data?.content || []);
        } catch {
            message.error('Failed to load sync history');
        } finally {
            setHistoryLoading(false);
        }
    };

    const historyColumns = useMemo(() => ([
        { title: 'Date', dataIndex: 'syncedAt', key: 'date', render: (d) => new Date(d).toLocaleString() },
        { title: 'Status', dataIndex: 'status', key: 'status', render: (s) => <Tag color={s === 'SUCCESS' ? 'green' : 'red'}>{s}</Tag> },
        { title: 'Fetched', dataIndex: 'transactionsFetched', key: 'fetched' },
        { title: 'New', dataIndex: 'transactionsNew', key: 'new' },
        { title: 'Error', dataIndex: 'errorMessage', key: 'error', ellipsis: true, render: (e) => e || '—' },
    ]), []);

    return (
        <div>
            <div className="page-header">
                <h1>Bank Accounts</h1>
                <Button type="primary" icon={<PlusOutlined />} loading={linking} disabled={!plaidReady} onClick={openPlaidLink}>
                    Link Bank
                </Button>
            </div>

            {loading ? (
                <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>
            ) : accounts.length === 0 ? (
                <Card style={{ textAlign: 'center', padding: 40 }}>
                    <Empty description="No bank accounts linked yet">
                        <Button type="primary" onClick={openPlaidLink} loading={linking} disabled={!plaidReady}>
                            Link Your First Bank
                        </Button>
                    </Empty>
                </Card>
            ) : (
                <List
                    grid={{ gutter: 20, xs: 1, sm: 1, md: 2, lg: 2 }}
                    dataSource={accounts}
                    renderItem={(account) => (
                        <List.Item>
                            <Card
                                actions={[
                                    <Button
                                        key="sync"
                                        type="text"
                                        icon={<RefreshCw size={14} className={syncing[account.id] ? 'spin' : ''} />}
                                        onClick={() => handleSync(account.id)}
                                        disabled={account.status === 'REMOVED'}
                                    >
                                        Sync
                                    </Button>,
                                    <Button key="history" type="text" icon={<History size={14} />} onClick={() => showHistory(account)}>
                                        History
                                    </Button>,
                                    <Popconfirm key="unlink" title="Unlink this bank?" description="Transactions will be preserved." onConfirm={() => handleUnlink(account.id)}>
                                        <Button type="text" danger icon={<Unlink size={14} />}>Unlink</Button>
                                    </Popconfirm>,
                                ]}
                            >
                                <Card.Meta
                                    avatar={
                                        account.institutionLogo
                                            ? <img src={account.institutionLogo} alt="" style={{ width: 40, height: 40, borderRadius: 8 }} />
                                            : <div style={{ width: 40, height: 40, borderRadius: 8, background: 'var(--color-primary-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                                <Building2 size={18} color="var(--color-primary)" />
                                            </div>
                                    }
                                    title={<span>{account.institutionName || 'Bank Account'}</span>}
                                    description={
                                        <div>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                                                <Tag color={statusColor[account.status] || 'blue'}>{account.status}</Tag>
                                            </div>
                                            {account.maskedIban && <div>Mask: {account.maskedIban}</div>}
                                            {account.accountName && <div>Account: {account.accountName}</div>}
                                            {account.lastSyncedAt && <div style={{ color: 'var(--color-text-muted)', fontSize: 12, marginTop: 4 }}>Last synced: {new Date(account.lastSyncedAt).toLocaleString()}</div>}
                                        </div>
                                    }
                                />
                            </Card>
                        </List.Item>
                    )}
                />
            )}

            <Modal
                title={`Sync History${selectedAccount?.institutionName ? ` — ${selectedAccount.institutionName}` : ''}`}
                open={historyModalOpen}
                onCancel={() => setHistoryModalOpen(false)}
                footer={null}
                width={700}
            >
                <Table columns={historyColumns} dataSource={syncHistory} rowKey="id" loading={historyLoading} pagination={false} size="small" />
            </Modal>
        </div>
    );
}
