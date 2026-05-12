import { useState, useEffect, useCallback } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, Tag, Card, Space, message, Popconfirm, DatePicker, Select } from 'antd';
import { PlusOutlined, DownloadOutlined } from '@ant-design/icons';
import { Pencil, Trash2 } from 'lucide-react';
import dayjs from 'dayjs';
import api from '../api/axios';

const formatCurrency = (val) =>
    new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' }).format(val || 0);

const Transactions = () => {
    const [transactions, setTransactions] = useState([]);
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [modalOpen, setModalOpen] = useState(false);
    const [editingTxn, setEditingTxn] = useState(null);
    const [saving, setSaving] = useState(false);
    const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
    const [filters, setFilters] = useState({ category: null, startDate: null, endDate: null });
    const [form] = Form.useForm();

    const loadTransactions = useCallback(async (page = 0, size = 10) => {
        setLoading(true);
        try {
            const params = { page, size };
            if (filters.category) params.category = filters.category;
            if (filters.startDate) params.startDate = filters.startDate;
            if (filters.endDate) params.endDate = filters.endDate;
            const response = await api.get('/transactions', { params });
            setTransactions(response.data?.content || []);
            setPagination({
                current: (response.data?.number || 0) + 1,
                pageSize: response.data?.size || 10,
                total: response.data?.totalElements || 0,
            });
        } catch {
            message.error('Failed to load transactions');
        } finally {
            setLoading(false);
        }
    }, [filters]);

    const loadCategories = async () => {
        try {
            const response = await api.get('/categories');
            setCategories(response.data || []);
        } catch { /* silent */ }
    };

    useEffect(() => {
        loadTransactions();
        loadCategories();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const handleSubmit = async (values) => {
        setSaving(true);
        try {
            const payload = {
                category: values.category,
                amount: values.amount,
                type: values.type,
                description: values.description || '',
                transactionDate: values.transactionDate ? values.transactionDate.format('YYYY-MM-DDTHH:mm:ss') : null,
            };
            if (editingTxn) {
                await api.put(`/transactions/${editingTxn.id}`, payload);
                message.success('Transaction updated');
            } else {
                await api.post('/transactions', payload);
                message.success('Transaction created');
            }
            setModalOpen(false);
            setEditingTxn(null);
            form.resetFields();
            loadTransactions(pagination.current - 1, pagination.pageSize);
        } catch (error) {
            const errors = error.response?.data;
            if (typeof errors === 'object') {
                Object.values(errors).forEach((msg) => message.error(msg));
            } else {
                message.error('Failed to save transaction');
            }
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async (id) => {
        try {
            await api.delete(`/transactions/${id}`);
            message.success('Transaction deleted');
            loadTransactions(pagination.current - 1, pagination.pageSize);
        } catch {
            message.error('Failed to delete transaction');
        }
    };

    const handleExport = async () => {
        try {
            const response = await api.get('/transactions/export', { responseType: 'blob' });
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', 'transactions.csv');
            document.body.appendChild(link);
            link.click();
            link.remove();
            message.success('CSV exported');
        } catch {
            message.error('Export failed');
        }
    };

    const openEdit = (txn) => {
        setEditingTxn(txn);
        form.setFieldsValue({
            category: txn.category?.name,
            amount: txn.amount,
            type: txn.type,
            description: txn.description,
            transactionDate: txn.transactionDate ? dayjs(txn.transactionDate) : null,
        });
        setModalOpen(true);
    };

    const openCreate = () => {
        setEditingTxn(null);
        form.resetFields();
        form.setFieldsValue({ type: 'OUT' });
        setModalOpen(true);
    };

    const columns = [
        {
            title: 'Date',
            dataIndex: 'transactionDate',
            key: 'date',
            width: 100,
            render: (date) => dayjs(date).format('DD MMM'),
        },
        {
            title: 'Category',
            dataIndex: 'category',
            key: 'category',
            render: (cat) => <Tag color={cat?.type === 'IN' ? 'green' : 'default'}>{cat?.name || '—'}</Tag>,
        },
        {
            title: 'Description',
            dataIndex: 'description',
            key: 'description',
            ellipsis: true,
            render: (text) => text || '—',
        },
        {
            title: 'Type',
            dataIndex: 'type',
            key: 'type',
            width: 80,
            render: (type) => <Tag color={type === 'IN' ? 'green' : 'red'}>{type === 'IN' ? 'Income' : 'Expense'}</Tag>,
        },
        {
            title: 'Amount',
            dataIndex: 'amount',
            key: 'amount',
            align: 'right',
            width: 140,
            render: (amount, record) => (
                <span style={{
                    fontWeight: 600,
                    color: record.type === 'IN' ? 'var(--color-income)' : 'var(--color-expense)',
                }}>
                    {record.type === 'IN' ? '+' : '-'}{formatCurrency(amount)}
                </span>
            ),
        },
        {
            title: '',
            key: 'actions',
            width: 80,
            render: (_, record) => (
                <Space>
                    <Button type="text" size="small" icon={<Pencil size={14} />} onClick={() => openEdit(record)} style={{ color: 'var(--color-text-secondary)' }} />
                    <Popconfirm title="Delete this transaction?" onConfirm={() => handleDelete(record.id)}>
                        <Button type="text" size="small" icon={<Trash2 size={14} />} danger />
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <div>
            <div className="page-header">
                <h1>Transactions</h1>
                <Space>
                    <Button icon={<DownloadOutlined />} onClick={handleExport}>Export CSV</Button>
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>Add Transaction</Button>
                </Space>
            </div>

            <Card className="filter-card">
                <Space wrap>
                    <Select
                        placeholder="All Categories"
                        allowClear
                        style={{ width: 180 }}
                        onChange={(val) => setFilters((f) => ({ ...f, category: val }))}
                        options={categories.map((c) => ({ label: c.name, value: c.name }))}
                    />
                    <DatePicker.RangePicker
                        onChange={(dates) => {
                            setFilters((f) => ({
                                ...f,
                                startDate: dates?.[0]?.startOf('day').format('YYYY-MM-DDTHH:mm:ss') || null,
                                endDate: dates?.[1]?.endOf('day').format('YYYY-MM-DDTHH:mm:ss') || null,
                            }));
                        }}
                    />
                    <Button type="primary" ghost onClick={() => loadTransactions(0, pagination.pageSize)}>Apply</Button>
                </Space>
            </Card>

            <Card styles={{ body: { padding: '0' } }}>
                <Table
                    columns={columns}
                    dataSource={transactions}
                    rowKey="id"
                    loading={loading}
                    scroll={{ x: 'max-content' }}
                    pagination={{
                        ...pagination,
                        showSizeChanger: true,
                        showTotal: (total) => `${total} transactions`,
                        onChange: (page, size) => loadTransactions(page - 1, size),
                    }}
                />
            </Card>

            <Modal
                title={editingTxn ? 'Edit Transaction' : 'Add Transaction'}
                open={modalOpen}
                onCancel={() => { setModalOpen(false); setEditingTxn(null); form.resetFields(); }}
                footer={null}
            >
                <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ marginTop: 16 }}>
                    <Form.Item name="type" label="Type" rules={[{ required: true }]}>
                        <Select options={[{ label: 'Income', value: 'IN' }, { label: 'Expense', value: 'OUT' }]} />
                    </Form.Item>
                    <Form.Item name="category" label="Category" rules={[{ required: true, message: 'Category is required' }]}>
                        <Input placeholder="e.g. Food, Salary, Rent" />
                    </Form.Item>
                    <Form.Item
                        name="amount"
                        label="Amount"
                        rules={[
                            { required: true, message: 'Amount is required' },
                            { validator: (_, value) => value && value > 0 ? Promise.resolve() : Promise.reject(new Error('Amount must be greater than 0')), },
                        ]}>
                        <InputNumber step={0.01} prefix="£" style={{ width: '100%' }} placeholder="0.00" />
                    </Form.Item>
                    <Form.Item name="description" label="Description">
                        <Input.TextArea rows={2} placeholder="Optional description" />
                    </Form.Item>
                    <Form.Item name="transactionDate" label="Date">
                        <DatePicker style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item style={{ marginBottom: 0 }}>
                        <Button type="primary" htmlType="submit" loading={saving} block>
                            {editingTxn ? 'Update' : 'Create'} Transaction
                        </Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default Transactions;