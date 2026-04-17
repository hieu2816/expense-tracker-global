import { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, Tag, Card, Space, message, Popconfirm } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { FolderOpen, Pencil, Trash2 } from 'lucide-react';
import api from '../api/axios';

const Categories = () => {
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [modalOpen, setModalOpen] = useState(false);
    const [editing, setEditing] = useState(null);
    const [saving, setSaving] = useState(false);
    const [form] = Form.useForm();

    const loadCategories = async () => {
        setLoading(true);
        try {
            const response = await api.get('/categories');
            setCategories(response.data || []);
        } catch {
            message.error('Failed to load categories');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadCategories();
    }, []);

    const handleSubmit = async (values) => {
        setSaving(true);
        try {
            if (editing) {
                await api.put(`/categories/${editing.id}`, values);
                message.success('Category updated');
            } else {
                await api.post('/categories', values);
                message.success('Category created');
            }
            setModalOpen(false);
            setEditing(null);
            form.resetFields();
            loadCategories();
        } catch (error) {
            const msg = error.response?.data?.error || 'Failed to save category';
            message.error(msg);
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async (id) => {
        try {
            await api.delete(`/categories/${id}`);
            message.success('Category deleted');
            loadCategories();
        } catch {
            message.error('Failed to delete category');
        }
    };

    const openEdit = (cat) => {
        setEditing(cat);
        form.setFieldsValue({ name: cat.name, type: cat.type, icon: cat.icon });
        setModalOpen(true);
    };

    const openCreate = () => {
        setEditing(null);
        form.resetFields();
        form.setFieldsValue({ type: 'OUT' });
        setModalOpen(true);
    };

    const columns = [
        {
            title: 'Name',
            dataIndex: 'name',
            key: 'name',
            render: (name, record) => (
                <Space>
                    <div style={{
                        width: 32, height: 32, borderRadius: 'var(--radius-sm)',
                        background: 'var(--color-primary-light)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        color: 'var(--color-primary)',
                    }}>
                        <FolderOpen size={16} />
                    </div>
                    <span style={{ fontWeight: 500 }}>{name}</span>
                </Space>
            ),
        },
        {
            title: 'Type',
            dataIndex: 'type',
            key: 'type',
            width: 120,
            render: (type) => (
                <Tag color={type === 'IN' ? 'green' : 'red'}>
                    {type === 'IN' ? 'Income' : 'Expense'}
                </Tag>
            ),
        },
        {
            title: '',
            key: 'actions',
            width: 80,
            render: (_, record) => (
                <Space>
                    <Button
                        type="text"
                        size="small"
                        icon={<Pencil size={14} />}
                        onClick={() => openEdit(record)}
                        style={{ color: 'var(--color-text-secondary)' }}
                    />
                    <Popconfirm title="Delete this category?" onConfirm={() => handleDelete(record.id)}>
                        <Button
                            type="text"
                            size="small"
                            icon={<Trash2 size={14} />}
                            danger
                        />
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <div>
            <div className="page-header">
                <h1>Categories</h1>
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                    Add Category
                </Button>
            </div>

            <Card>
                <Table
                    columns={columns}
                    dataSource={categories}
                    rowKey="id"
                    loading={loading}
                    pagination={false}
                    locale={{ emptyText: 'No categories yet. They get auto-created when you add transactions!' }}
                />
            </Card>

            <Modal
                title={editing ? 'Edit Category' : 'Add Category'}
                open={modalOpen}
                onCancel={() => { setModalOpen(false); setEditing(null); form.resetFields(); }}
                footer={null}
            >
                <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ marginTop: 16 }}>
                    <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name is required' }]}>
                        <Input placeholder="e.g. Food, Transport, Salary" />
                    </Form.Item>
                    <Form.Item name="type" label="Type" rules={[{ required: true }]}>
                        <Select options={[
                            { label: 'Income', value: 'IN' },
                            { label: 'Expense', value: 'OUT' },
                        ]} />
                    </Form.Item>
                    <Form.Item name="icon" label="Icon (emoji)">
                        <Input placeholder="e.g. 🍔 🚗 💰" />
                    </Form.Item>
                    <Form.Item style={{ marginBottom: 0 }}>
                        <Button type="primary" htmlType="submit" loading={saving} block>
                            {editing ? 'Update' : 'Create'} Category
                        </Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default Categories;