import { useState, useEffect, useCallback } from 'react';
import {
    Alert,
    Button,
    Card,
    DatePicker,
    Form,
    Input,
    InputNumber,
    Modal,
    Popconfirm,
    Select,
    Space,
    Table,
    Tabs,
    Tag,
    Upload,
    message,
} from 'antd';
import { DownloadOutlined, InboxOutlined, PlusOutlined } from '@ant-design/icons';
import { Pencil, Trash2 } from 'lucide-react';
import dayjs from 'dayjs';
import api from '../api/axios';

const formatCurrency = (val) =>
    new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' }).format(val || 0);

const matchTypeOptions = [
    { label: 'Contains', value: 'CONTAINS' },
    { label: 'Starts with', value: 'STARTS_WITH' },
    { label: 'Exact', value: 'EXACT' },
    { label: 'Regex', value: 'REGEX' },
];

const typeOptions = [
    { label: 'Income', value: 'IN' },
    { label: 'Expense', value: 'OUT' },
];

const Transactions = () => {
    const [transactions, setTransactions] = useState([]);
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [modalOpen, setModalOpen] = useState(false);
    const [editingTxn, setEditingTxn] = useState(null);
    const [saving, setSaving] = useState(false);
    const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
    const [filters, setFilters] = useState({ category: null, startDate: null, endDate: null });
    const [csvContent, setCsvContent] = useState('');
    const [csvPreview, setCsvPreview] = useState(null);
    const [importing, setImporting] = useState(false);
    const [nlDraft, setNlDraft] = useState(null);
    const [nlLoading, setNlLoading] = useState(false);
    const [rules, setRules] = useState([]);
    const [rulesLoading, setRulesLoading] = useState(false);
    const [ruleHelpOpen, setRuleHelpOpen] = useState(false);
    const [templates, setTemplates] = useState([]);
    const [templatesLoading, setTemplatesLoading] = useState(false);
    const [recurring, setRecurring] = useState([]);
    const [recurringLoading, setRecurringLoading] = useState(false);
    const [selectedAttachmentTransaction, setSelectedAttachmentTransaction] = useState(null);
    const [attachments, setAttachments] = useState([]);
    const [attachmentsLoading, setAttachmentsLoading] = useState(false);
    const [form] = Form.useForm();
    const [csvForm] = Form.useForm();
    const [nlForm] = Form.useForm();
    const [nlDraftForm] = Form.useForm();
    const [ruleForm] = Form.useForm();
    const [templateForm] = Form.useForm();
    const [recurringForm] = Form.useForm();

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

    const loadRules = async () => {
        setRulesLoading(true);
        try {
            const response = await api.get('/category-rules');
            setRules(response.data || []);
        } catch {
            message.error('Failed to load category rules');
        } finally {
            setRulesLoading(false);
        }
    };

    const loadTemplates = async () => {
        setTemplatesLoading(true);
        try {
            const response = await api.get('/transaction-templates');
            setTemplates(response.data || []);
        } catch {
            message.error('Failed to load templates');
        } finally {
            setTemplatesLoading(false);
        }
    };

    const loadRecurring = async () => {
        setRecurringLoading(true);
        try {
            const response = await api.get('/recurring-transactions');
            setRecurring(response.data || []);
        } catch {
            message.error('Failed to load recurring transactions');
        } finally {
            setRecurringLoading(false);
        }
    };

    const loadAttachments = async (transactionId) => {
        if (!transactionId) {
            setAttachments([]);
            return;
        }
        setAttachmentsLoading(true);
        try {
            const response = await api.get(`/transactions/${transactionId}/attachments`);
            setAttachments(response.data || []);
        } catch {
            message.error('Failed to load receipts');
        } finally {
            setAttachmentsLoading(false);
        }
    };

    useEffect(() => {
        loadTransactions();
        loadCategories();
        loadRules();
        loadTemplates();
        loadRecurring();
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
            if (typeof errors === 'object') Object.values(errors).forEach((msg) => message.error(msg));
            else message.error('Failed to save transaction');
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

    const csvPayload = () => ({
        fileName: csvForm.getFieldValue('fileName') || 'transactions.csv',
        content: csvContent,
        mapping: csvForm.getFieldValue('mapping'),
    });

    const previewCsv = async () => {
        if (!csvContent) {
            message.warning('Upload or paste CSV content first');
            return;
        }
        setImporting(true);
        try {
            const response = await api.post('/imports/csv/preview', csvPayload());
            setCsvPreview(response.data);
            message.success('CSV preview ready');
        } catch {
            message.error('CSV preview failed');
        } finally {
            setImporting(false);
        }
    };

    const commitCsv = async () => {
        setImporting(true);
        try {
            const response = await api.post('/imports/csv/commit', csvPayload());
            message.success(`Imported ${response.data.importedRows} transactions`);
            setCsvPreview(null);
            setCsvContent('');
            loadTransactions(0, pagination.pageSize);
        } catch {
            message.error('CSV import failed');
        } finally {
            setImporting(false);
        }
    };

    const parseNaturalLanguage = async (values) => {
        setNlLoading(true);
        try {
            const response = await api.post('/transactions/natural-language/parse', values);
            const draft = response.data;
            setNlDraft(draft);
            nlDraftForm.setFieldsValue({
                type: draft.type,
                category: draft.category,
                amount: draft.amount,
                description: draft.description,
                transactionDate: draft.transactionDate ? dayjs(draft.transactionDate) : dayjs(),
            });
        } catch {
            message.error('Could not parse text');
        } finally {
            setNlLoading(false);
        }
    };

    const confirmNaturalLanguage = async (values) => {
        if (!nlDraft) return;
        setNlLoading(true);
        try {
            await api.post('/transactions/natural-language/confirm', {
                originalText: nlDraft.originalText,
                language: nlDraft.language,
                confidence: nlDraft.confidence,
                transaction: {
                    category: values.category,
                    amount: values.amount,
                    type: values.type,
                    description: values.description || '',
                    transactionDate: values.transactionDate ? values.transactionDate.format('YYYY-MM-DDTHH:mm:ss') : null,
                },
            });
            message.success('Transaction created from text');
            setNlDraft(null);
            nlForm.resetFields();
            nlDraftForm.resetFields();
            loadTransactions(0, pagination.pageSize);
        } catch {
            message.error('Failed to create transaction');
        } finally {
            setNlLoading(false);
        }
    };

    const createRule = async (values) => {
        try {
            await api.post('/category-rules', values);
            message.success('Category rule created');
            ruleForm.resetFields();
            loadRules();
        } catch {
            message.error('Failed to create category rule');
        }
    };

    const deleteRule = async (id) => {
        try {
            await api.delete(`/category-rules/${id}`);
            message.success('Category rule deleted');
            loadRules();
        } catch {
            message.error('Failed to delete category rule');
        }
    };

    const createTemplate = async (values) => {
        try {
            await api.post('/transaction-templates', { ...values, currency: 'GBP', active: true });
            message.success('Template created');
            templateForm.resetFields();
            loadTemplates();
        } catch {
            message.error('Failed to create template');
        }
    };

    const useTemplate = async (id) => {
        try {
            await api.post(`/transaction-templates/${id}/create-transaction`);
            message.success('Transaction created from template');
            loadTransactions(0, pagination.pageSize);
        } catch {
            message.error('Failed to use template');
        }
    };

    const deleteTemplate = async (id) => {
        try {
            await api.delete(`/transaction-templates/${id}`);
            message.success('Template deleted');
            loadTemplates();
            loadRecurring();
        } catch {
            message.error('Failed to delete template');
        }
    };

    const createRecurring = async (values) => {
        try {
            await api.post('/recurring-transactions', {
                templateId: values.templateId,
                frequency: values.frequency,
                nextRunDate: values.nextRunDate.format('YYYY-MM-DD'),
                active: true,
            });
            message.success('Recurring transaction created');
            recurringForm.resetFields();
            loadRecurring();
        } catch {
            message.error('Failed to create recurring transaction');
        }
    };

    const deleteRecurring = async (id) => {
        try {
            await api.delete(`/recurring-transactions/${id}`);
            message.success('Recurring transaction deleted');
            loadRecurring();
        } catch {
            message.error('Failed to delete recurring transaction');
        }
    };

    const uploadReceipt = async ({ file, onSuccess, onError }) => {
        if (!selectedAttachmentTransaction) {
            message.warning('Select a transaction first');
            onError?.(new Error('No transaction selected'));
            return;
        }
        const formData = new FormData();
        formData.append('file', file);
        try {
            await api.post(`/transactions/${selectedAttachmentTransaction}/attachments`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' },
            });
            message.success('Receipt uploaded');
            onSuccess?.();
            loadAttachments(selectedAttachmentTransaction);
        } catch (error) {
            message.error('Receipt upload failed');
            onError?.(error);
        }
    };

    const downloadAttachment = async (attachment) => {
        try {
            const response = await api.get(`/attachments/${attachment.id}`, { responseType: 'blob' });
            const url = window.URL.createObjectURL(new Blob([response.data], { type: attachment.contentType }));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', attachment.fileName);
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);
        } catch {
            message.error('Receipt download failed');
        }
    };

    const deleteAttachment = async (id) => {
        try {
            await api.delete(`/attachments/${id}`);
            message.success('Receipt deleted');
            loadAttachments(selectedAttachmentTransaction);
        } catch {
            message.error('Failed to delete receipt');
        }
    };

    const columns = [
        { title: 'Date', dataIndex: 'transactionDate', key: 'date', width: 100, render: (date) => dayjs(date).format('DD MMM') },
        { title: 'Category', dataIndex: 'category', key: 'category', render: (cat) => <Tag color={cat?.type === 'IN' ? 'green' : 'default'}>{cat?.name || '-'}</Tag> },
        { title: 'Description', dataIndex: 'description', key: 'description', ellipsis: true, render: (text) => text || '-' },
        { title: 'Source', dataIndex: 'source', key: 'source', width: 150, render: (source) => <Tag>{source || 'MANUAL_FORM'}</Tag> },
        { title: 'Type', dataIndex: 'type', key: 'type', width: 80, render: (type) => <Tag color={type === 'IN' ? 'green' : 'red'}>{type === 'IN' ? 'Income' : 'Expense'}</Tag> },
        {
            title: 'Amount',
            dataIndex: 'amount',
            key: 'amount',
            align: 'right',
            width: 140,
            render: (amount, record) => (
                <span style={{ fontWeight: 600, color: record.type === 'IN' ? 'var(--color-income)' : 'var(--color-expense)' }}>
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

    const csvColumns = [
        { title: 'Row', dataIndex: 'rowNumber', width: 70 },
        { title: 'Date', dataIndex: 'transactionDate', render: (date) => date ? dayjs(date).format('YYYY-MM-DD') : '-' },
        { title: 'Description', dataIndex: 'description', ellipsis: true },
        { title: 'Type', dataIndex: 'type', render: (type) => <Tag color={type === 'IN' ? 'green' : 'red'}>{type}</Tag> },
        { title: 'Category', dataIndex: 'category' },
        { title: 'Amount', dataIndex: 'amount', align: 'right' },
        { title: 'Status', render: (_, row) => row.valid ? (row.duplicate ? <Tag color="orange">Duplicate</Tag> : <Tag color="green">Valid</Tag>) : <Tag color="red">Invalid</Tag> },
        { title: 'Errors', dataIndex: 'errors', render: (errors) => errors?.join(', ') || '-' },
    ];

    const ruleColumns = [
        { title: 'Keyword', dataIndex: 'keyword' },
        { title: 'Match', dataIndex: 'matchType', render: (value) => <Tag>{value}</Tag> },
        { title: 'Category', dataIndex: 'category', render: (category) => category?.name },
        { title: 'Type', dataIndex: 'transactionType', render: (value) => value ? <Tag>{value}</Tag> : 'Any' },
        { title: 'Priority', dataIndex: 'priority', width: 90 },
        { title: 'Active', dataIndex: 'active', render: (active) => <Tag color={active ? 'green' : 'default'}>{active ? 'Active' : 'Off'}</Tag> },
        { title: '', width: 80, render: (_, rule) => <Popconfirm title="Delete this rule?" onConfirm={() => deleteRule(rule.id)}><Button danger size="small">Delete</Button></Popconfirm> },
    ];

    const templateColumns = [
        { title: 'Name', dataIndex: 'name' },
        { title: 'Category', dataIndex: 'category', render: (category) => category?.name },
        { title: 'Type', dataIndex: 'type', render: (value) => <Tag color={value === 'IN' ? 'green' : 'red'}>{value}</Tag> },
        { title: 'Amount', dataIndex: 'amount', align: 'right', render: (amount) => formatCurrency(amount) },
        { title: 'Description', dataIndex: 'description', ellipsis: true, render: (text) => text || '-' },
        {
            title: '',
            width: 170,
            render: (_, template) => (
                <Space>
                    <Button size="small" type="primary" ghost onClick={() => useTemplate(template.id)}>Use</Button>
                    <Popconfirm title="Delete this template?" onConfirm={() => deleteTemplate(template.id)}>
                        <Button danger size="small">Delete</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    const recurringColumns = [
        { title: 'Template', dataIndex: 'template', render: (template) => template?.name },
        { title: 'Frequency', dataIndex: 'frequency', render: (value) => <Tag>{value}</Tag> },
        { title: 'Next run', dataIndex: 'nextRunDate', render: (date) => dayjs(date).format('YYYY-MM-DD') },
        { title: 'Last run', dataIndex: 'lastRunDate', render: (date) => date ? dayjs(date).format('YYYY-MM-DD') : '-' },
        { title: 'Active', dataIndex: 'active', render: (active) => <Tag color={active ? 'green' : 'default'}>{active ? 'Active' : 'Off'}</Tag> },
        { title: '', width: 80, render: (_, item) => <Popconfirm title="Delete this recurring transaction?" onConfirm={() => deleteRecurring(item.id)}><Button danger size="small">Delete</Button></Popconfirm> },
    ];

    const attachmentColumns = [
        { title: 'File', dataIndex: 'fileName', ellipsis: true },
        { title: 'Type', dataIndex: 'contentType', width: 160 },
        { title: 'Size', dataIndex: 'fileSize', width: 110, render: (size) => `${Math.round((size || 0) / 1024)} KB` },
        { title: 'Uploaded', dataIndex: 'createdAt', width: 150, render: (date) => dayjs(date).format('DD MMM YYYY') },
        {
            title: '',
            width: 180,
            render: (_, attachment) => (
                <Space>
                    <Button size="small" onClick={() => downloadAttachment(attachment)}>Download</Button>
                    <Popconfirm title="Delete this receipt?" onConfirm={() => deleteAttachment(attachment.id)}>
                        <Button danger size="small">Delete</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    const transactionForm = (
        <Modal
            title={editingTxn ? 'Edit Transaction' : 'Add Transaction'}
            open={modalOpen}
            onCancel={() => { setModalOpen(false); setEditingTxn(null); form.resetFields(); }}
            footer={null}
        >
            <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ marginTop: 16 }}>
                <Form.Item name="type" label="Type" rules={[{ required: true }]}>
                    <Select options={typeOptions} />
                </Form.Item>
                <Form.Item name="category" label="Category" rules={[{ required: true, message: 'Category is required' }]}>
                    <Input placeholder="e.g. Food, Salary, Rent" />
                </Form.Item>
                <Form.Item name="amount" label="Amount" rules={[{ required: true, message: 'Amount is required' }]}>
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
    );

    return (
        <div>
            <div className="page-header">
                <h1>Transactions</h1>
                <Space>
                    <Button icon={<DownloadOutlined />} onClick={handleExport}>Export CSV</Button>
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>Add Transaction</Button>
                </Space>
            </div>

            <Tabs
                items={[
                    {
                        key: 'list',
                        label: 'Transactions',
                        children: (
                            <>
                                <Card className="filter-card">
                                    <Space wrap>
                                        <Select placeholder="All Categories" allowClear style={{ width: 180 }} onChange={(val) => setFilters((f) => ({ ...f, category: val }))} options={categories.map((c) => ({ label: c.name, value: c.name }))} />
                                        <DatePicker.RangePicker onChange={(dates) => setFilters((f) => ({ ...f, startDate: dates?.[0]?.startOf('day').format('YYYY-MM-DDTHH:mm:ss') || null, endDate: dates?.[1]?.endOf('day').format('YYYY-MM-DDTHH:mm:ss') || null }))} />
                                        <Button type="primary" ghost onClick={() => loadTransactions(0, pagination.pageSize)}>Apply</Button>
                                    </Space>
                                </Card>
                                <Card styles={{ body: { padding: '0' } }}>
                                    <Table columns={columns} dataSource={transactions} rowKey="id" loading={loading} scroll={{ x: 'max-content' }} pagination={{ ...pagination, showSizeChanger: true, showTotal: (total) => `${total} transactions`, onChange: (page, size) => loadTransactions(page - 1, size) }} />
                                </Card>
                            </>
                        ),
                    },
                    {
                        key: 'import',
                        label: 'CSV Import',
                        children: (
                            <Card>
                                <Form form={csvForm} layout="vertical" initialValues={{ fileName: 'transactions.csv', mapping: { date: 'date', description: 'description', amount: 'amount', type: 'type', category: 'category', currency: 'currency' } }}>
                                    <Upload.Dragger accept=".csv,text/csv" showUploadList={false} beforeUpload={(file) => {
                                        const reader = new FileReader();
                                        reader.onload = (event) => {
                                            setCsvContent(event.target.result);
                                            csvForm.setFieldValue('fileName', file.name);
                                        };
                                        reader.readAsText(file);
                                        return false;
                                    }}>
                                        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
                                        <p className="ant-upload-text">Drop CSV here or click to upload</p>
                                    </Upload.Dragger>
                                    <Form.Item name="fileName" label="File name" style={{ marginTop: 16 }}>
                                        <Input />
                                    </Form.Item>
                                    <Form.Item label="CSV content">
                                        <Input.TextArea rows={5} value={csvContent} onChange={(e) => setCsvContent(e.target.value)} placeholder={'date,description,amount,type,category\n2026-06-01,Lunch,-12.5,,Food'} />
                                    </Form.Item>
                                    <Space wrap>
                                        {['date', 'description', 'amount', 'type', 'category', 'currency'].map((field) => (
                                            <Form.Item key={field} name={['mapping', field]} label={field} style={{ marginBottom: 8 }}>
                                                <Input style={{ width: 150 }} />
                                            </Form.Item>
                                        ))}
                                    </Space>
                                    <div>
                                        <Space>
                                            <Button type="primary" onClick={previewCsv} loading={importing}>Preview</Button>
                                            <Button onClick={commitCsv} loading={importing} disabled={!csvPreview}>Commit Import</Button>
                                        </Space>
                                    </div>
                                </Form>
                                {csvPreview && (
                                    <Card style={{ marginTop: 16 }} title={`Preview: ${csvPreview.validRows} valid, ${csvPreview.duplicateRows} duplicate, ${csvPreview.invalidRows} invalid`}>
                                        <Table columns={csvColumns} dataSource={csvPreview.rows || []} rowKey="rowNumber" size="small" scroll={{ x: 'max-content' }} pagination={{ pageSize: 8 }} />
                                    </Card>
                                )}
                            </Card>
                        ),
                    },
                    {
                        key: 'natural',
                        label: 'Natural Language',
                        children: (
                            <Card>
                                <Form form={nlForm} layout="vertical" onFinish={parseNaturalLanguage} initialValues={{ language: 'en' }}>
                                    <Alert
                                        type="info"
                                        showIcon
                                        style={{ marginBottom: 16 }}
                                        message="English input only for now"
                                        description="Use amounts in the app currency, for example: lunch 12 today, coffee 4.5 yesterday, salary 2500 on the 25th."
                                    />
                                    <Form.Item name="language" hidden>
                                        <Input />
                                    </Form.Item>
                                    <Form.Item name="text" label="Input" rules={[{ required: true }]}>
                                        <Input.TextArea rows={3} placeholder="lunch 12 today / coffee 4.5 yesterday / salary 2500 on the 25th" />
                                    </Form.Item>
                                    <Button type="primary" htmlType="submit" loading={nlLoading}>Parse</Button>
                                </Form>
                                {nlDraft && (
                                    <Card style={{ marginTop: 16 }} title={`Draft confidence: ${nlDraft.confidence}`}>
                                        {nlDraft.warnings?.length > 0 && <Alert type="warning" showIcon message={nlDraft.warnings.join(', ')} style={{ marginBottom: 16 }} />}
                                        <Form form={nlDraftForm} layout="vertical" onFinish={confirmNaturalLanguage}>
                                            <Form.Item name="type" label="Type" rules={[{ required: true }]}><Select options={typeOptions} /></Form.Item>
                                            <Form.Item name="category" label="Category" rules={[{ required: true }]}><Input /></Form.Item>
                                            <Form.Item name="amount" label="Amount" rules={[{ required: true }]}><InputNumber step={0.01} prefix="£" style={{ width: '100%' }} /></Form.Item>
                                            <Form.Item name="description" label="Description"><Input /></Form.Item>
                                            <Form.Item name="transactionDate" label="Date"><DatePicker style={{ width: '100%' }} /></Form.Item>
                                            <Button type="primary" htmlType="submit" loading={nlLoading}>Confirm Transaction</Button>
                                        </Form>
                                    </Card>
                                )}
                            </Card>
                        ),
                    },
                    {
                        key: 'rules',
                        label: 'Category Rules',
                        children: (
                            <Card>
                                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start', marginBottom: 16 }}>
                                    <div>
                                        <h3 style={{ margin: 0, color: 'var(--color-text-primary)' }}>Category Rules</h3>
                                        <p style={{ margin: '4px 0 0', color: 'var(--color-text-secondary)' }}>
                                            Teach SpendWiser how to categorize repeated merchants and descriptions automatically.
                                        </p>
                                    </div>
                                    <Button onClick={() => setRuleHelpOpen(true)}>How it works</Button>
                                </div>
                                <Form form={ruleForm} layout="vertical" onFinish={createRule} initialValues={{ matchType: 'CONTAINS', priority: 100, active: true }}>
                                    <Space wrap align="end">
                                        <Form.Item name="keyword" label="Keyword" rules={[{ required: true }]}><Input placeholder="grab" /></Form.Item>
                                        <Form.Item name="matchType" label="Match" rules={[{ required: true }]}><Select style={{ width: 150 }} options={matchTypeOptions} /></Form.Item>
                                        <Form.Item name="category" label="Category" rules={[{ required: true }]}><Input placeholder="Transport" /></Form.Item>
                                        <Form.Item name="transactionType" label="Type"><Select allowClear style={{ width: 130 }} options={typeOptions} /></Form.Item>
                                        <Form.Item name="priority" label="Priority"><InputNumber style={{ width: 100 }} /></Form.Item>
                                        <Form.Item><Button type="primary" htmlType="submit">Add Rule</Button></Form.Item>
                                    </Space>
                                </Form>
                                <Table columns={ruleColumns} dataSource={rules} rowKey="id" loading={rulesLoading} scroll={{ x: 'max-content' }} />
                            </Card>
                        ),
                    },
                    {
                        key: 'templates',
                        label: 'Templates',
                        children: (
                            <Card>
                                <Alert
                                    type="info"
                                    showIcon
                                    style={{ marginBottom: 16 }}
                                    message="Quick templates create one transaction from a saved pattern."
                                    description="Use this for repeated manual entries such as rent, salary, subscriptions, or a regular commute."
                                />
                                <Form form={templateForm} layout="vertical" onFinish={createTemplate} initialValues={{ type: 'OUT' }}>
                                    <Space wrap align="end">
                                        <Form.Item name="name" label="Name" rules={[{ required: true }]}><Input placeholder="Monthly rent" /></Form.Item>
                                        <Form.Item name="type" label="Type" rules={[{ required: true }]}><Select style={{ width: 130 }} options={typeOptions} /></Form.Item>
                                        <Form.Item name="category" label="Category" rules={[{ required: true }]}><Input placeholder="Rent" /></Form.Item>
                                        <Form.Item name="amount" label="Amount" rules={[{ required: true }]}><InputNumber step={0.01} prefix="GBP" style={{ width: 140 }} /></Form.Item>
                                        <Form.Item name="description" label="Description"><Input placeholder="Optional note" /></Form.Item>
                                        <Form.Item><Button type="primary" htmlType="submit">Add Template</Button></Form.Item>
                                    </Space>
                                </Form>
                                <Table columns={templateColumns} dataSource={templates} rowKey="id" loading={templatesLoading} scroll={{ x: 'max-content' }} />
                            </Card>
                        ),
                    },
                    {
                        key: 'receipts',
                        label: 'Receipts',
                        children: (
                            <Card>
                                <Alert
                                    type="info"
                                    showIcon
                                    style={{ marginBottom: 16 }}
                                    message="Attach receipt or bill files to an existing transaction."
                                    description="Files are stored by the backend and can be downloaded later from this transaction."
                                />
                                <Form layout="vertical">
                                    <Form.Item label="Transaction">
                                        <Select
                                            showSearch
                                            allowClear
                                            placeholder="Select a transaction"
                                            value={selectedAttachmentTransaction}
                                            style={{ width: '100%', maxWidth: 520 }}
                                            optionFilterProp="label"
                                            onChange={(value) => {
                                                setSelectedAttachmentTransaction(value);
                                                loadAttachments(value);
                                            }}
                                            options={transactions.map((txn) => ({
                                                value: txn.id,
                                                label: `${dayjs(txn.transactionDate).format('YYYY-MM-DD')} - ${txn.description || txn.category?.name || 'Transaction'} - ${formatCurrency(txn.amount)}`,
                                            }))}
                                        />
                                    </Form.Item>
                                </Form>
                                <Upload.Dragger
                                    name="file"
                                    multiple={false}
                                    showUploadList={false}
                                    customRequest={uploadReceipt}
                                    disabled={!selectedAttachmentTransaction}
                                >
                                    <p className="ant-upload-drag-icon"><InboxOutlined /></p>
                                    <p className="ant-upload-text">Drop receipt here or click to upload</p>
                                </Upload.Dragger>
                                <Table
                                    columns={attachmentColumns}
                                    dataSource={attachments}
                                    rowKey="id"
                                    loading={attachmentsLoading}
                                    style={{ marginTop: 16 }}
                                    scroll={{ x: 'max-content' }}
                                />
                            </Card>
                        ),
                    },
                    {
                        key: 'recurring',
                        label: 'Recurring',
                        children: (
                            <Card>
                                <Alert
                                    type="info"
                                    showIcon
                                    style={{ marginBottom: 16 }}
                                    message="Recurring transactions run from a template."
                                    description="Create a template first, then choose how often the backend should generate the next transaction."
                                />
                                <Form form={recurringForm} layout="vertical" onFinish={createRecurring} initialValues={{ frequency: 'MONTHLY' }}>
                                    <Space wrap align="end">
                                        <Form.Item name="templateId" label="Template" rules={[{ required: true }]}>
                                            <Select style={{ width: 220 }} options={templates.map((template) => ({ value: template.id, label: template.name }))} />
                                        </Form.Item>
                                        <Form.Item name="frequency" label="Frequency" rules={[{ required: true }]}>
                                            <Select
                                                style={{ width: 150 }}
                                                options={[
                                                    { label: 'Daily', value: 'DAILY' },
                                                    { label: 'Weekly', value: 'WEEKLY' },
                                                    { label: 'Monthly', value: 'MONTHLY' },
                                                    { label: 'Yearly', value: 'YEARLY' },
                                                ]}
                                            />
                                        </Form.Item>
                                        <Form.Item name="nextRunDate" label="Next run date" rules={[{ required: true }]}>
                                            <DatePicker style={{ width: 160 }} />
                                        </Form.Item>
                                        <Form.Item><Button type="primary" htmlType="submit">Add Recurring</Button></Form.Item>
                                    </Space>
                                </Form>
                                <Table columns={recurringColumns} dataSource={recurring} rowKey="id" loading={recurringLoading} scroll={{ x: 'max-content' }} />
                            </Card>
                        ),
                    },
                ]}
            />

            {transactionForm}
            <Modal
                title="How Category Rules Work"
                open={ruleHelpOpen}
                onCancel={() => setRuleHelpOpen(false)}
                footer={<Button type="primary" onClick={() => setRuleHelpOpen(false)}>Got it</Button>}
            >
                <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    <Alert
                        type="info"
                        showIcon
                        message="Category rules auto-fill categories for imported or parsed transactions."
                        description="They are useful when the same merchant or phrase appears many times, such as Grab, Starbucks, Netflix, rent, or salary."
                    />
                    <div>
                        <h4>1. Keyword</h4>
                        <p>Text the app should look for in the transaction description.</p>
                        <p><strong>Example:</strong> keyword <code>grab</code> can match descriptions like <code>grab ride</code> or <code>morning grab taxi</code>.</p>
                    </div>
                    <div>
                        <h4>2. Match Type</h4>
                        <Table
                            size="small"
                            pagination={false}
                            columns={[
                                { title: 'Match', dataIndex: 'match', width: 130 },
                                { title: 'Meaning', dataIndex: 'meaning' },
                                { title: 'Example', dataIndex: 'example' },
                            ]}
                            dataSource={[
                                { key: 'contains', match: 'CONTAINS', meaning: 'Description contains the keyword anywhere.', example: 'grab matches "morning grab ride"' },
                                { key: 'starts', match: 'STARTS_WITH', meaning: 'Description starts with the keyword.', example: 'grab matches "grab taxi"' },
                                { key: 'exact', match: 'EXACT', meaning: 'Description equals the keyword exactly.', example: 'salary matches only "salary"' },
                                { key: 'regex', match: 'REGEX', meaning: 'Advanced pattern matching.', example: 'uber|grab|taxi matches any of them' },
                            ]}
                        />
                    </div>
                    <div>
                        <h4>3. Type</h4>
                        <p>Optional. Choose Income or Expense if the rule should only apply to one direction. Leave it empty to apply to both.</p>
                    </div>
                    <div>
                        <h4>4. Priority</h4>
                        <p>If multiple rules match, the smaller priority number wins first.</p>
                        <p><strong>Example:</strong> priority <code>10</code> for <code>grabfood</code> can beat priority <code>100</code> for <code>grab</code>.</p>
                    </div>
                    <Alert
                        type="success"
                        showIcon
                        message="Simple recommended setup"
                        description="Start with CONTAINS rules for common merchants. Use REGEX only when you need one rule to match several keywords."
                    />
                </Space>
            </Modal>
        </div>
    );
};

export default Transactions;
