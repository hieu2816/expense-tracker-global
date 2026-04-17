import { useState } from 'react';
import { Card, Form, Input, Button, message, Row, Col } from 'antd';
import { UserOutlined, MailOutlined, LockOutlined } from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';
import api from '../api/axios';

const Profile = () => {
    const { user, loadProfile } = useAuth();
    const [savingProfile, setSavingProfile] = useState(false);
    const [savingPassword, setSavingPassword] = useState(false);
    const [profileForm] = Form.useForm();
    const [passwordForm] = Form.useForm();

    const handleUpdateProfile = async (values) => {
        setSavingProfile(true);
        try {
            await api.put('/user/profile', { fullName: values.fullName });
            message.success('Profile updated');
            loadProfile();
        } catch {
            message.error('Failed to update profile');
        } finally {
            setSavingProfile(false);
        }
    };

    const handleChangePassword = async (values) => {
        setSavingPassword(true);
        try {
            await api.put('/user/change-password', {
                currentPassword: values.currentPassword,
                newPassword: values.newPassword,
            });
            message.success('Password changed successfully');
            passwordForm.resetFields();
        } catch (error) {
            const msg = error.response?.data?.error || 'Failed to change password';
            message.error(msg);
        } finally {
            setSavingPassword(false);
        }
    };

    return (
        <div>
            <div className="page-header">
                <h1>Profile</h1>
            </div>

            <Row gutter={[20, 20]}>
                {/* Profile Info */}
                <Col xs={24} md={12}>
                    <Card title="Profile Information">
                        <Form
                            form={profileForm}
                            layout="vertical"
                            onFinish={handleUpdateProfile}
                            initialValues={{ fullName: user?.fullName, email: user?.email }}
                        >
                            <Form.Item label="Email">
                                <Input
                                    prefix={<MailOutlined style={{ color: 'var(--color-text-muted)' }} />}
                                    value={user?.email}
                                    disabled
                                    style={{ background: 'var(--color-bg-page)' }}
                                />
                            </Form.Item>
                            <Form.Item
                                name="fullName"
                                label="Full Name"
                                rules={[{ required: true, message: 'Name is required' }]}
                            >
                                <Input prefix={<UserOutlined style={{ color: 'var(--color-text-muted)' }} />} placeholder="Your name" />
                            </Form.Item>
                            <Form.Item style={{ marginBottom: 0 }}>
                                <Button
                                    type="primary"
                                    htmlType="submit"
                                    loading={savingProfile}
                                >
                                    Save Changes
                                </Button>
                            </Form.Item>
                        </Form>
                    </Card>
                </Col>

                {/* Change Password */}
                <Col xs={24} md={12}>
                    <Card title="Change Password">
                        <Form form={passwordForm} layout="vertical" onFinish={handleChangePassword}>
                            <Form.Item
                                name="currentPassword"
                                label="Current Password"
                                rules={[{ required: true, message: 'Current password is required' }]}
                            >
                                <Input.Password prefix={<LockOutlined style={{ color: 'var(--color-text-muted)' }} />} placeholder="Current password" />
                            </Form.Item>
                            <Form.Item
                                name="newPassword"
                                label="New Password"
                                rules={[
                                    { required: true, message: 'New password is required' },
                                    { min: 8, message: 'Password must be at least 8 characters' },
                                ]}
                            >
                                <Input.Password prefix={<LockOutlined style={{ color: 'var(--color-text-muted)' }} />} placeholder="New password" />
                            </Form.Item>
                            <Form.Item
                                name="confirmPassword"
                                label="Confirm New Password"
                                dependencies={['newPassword']}
                                rules={[
                                    { required: true, message: 'Please confirm your password' },
                                    ({ getFieldValue }) => ({
                                        validator(_, value) {
                                            if (!value || getFieldValue('newPassword') === value) return Promise.resolve();
                                            return Promise.reject(new Error('Passwords do not match'));
                                        },
                                    }),
                                ]}
                            >
                                <Input.Password prefix={<LockOutlined style={{ color: 'var(--color-text-muted)' }} />} placeholder="Confirm new password" />
                            </Form.Item>
                            <Form.Item style={{ marginBottom: 0 }}>
                                <Button
                                    type="primary"
                                    htmlType="submit"
                                    loading={savingPassword}
                                >
                                    Change Password
                                </Button>
                            </Form.Item>
                        </Form>
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default Profile;