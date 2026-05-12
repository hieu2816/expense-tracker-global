import { useState } from 'react';
import { Form, Input, Button, message } from 'antd';
import { MailOutlined, LockOutlined, UserOutlined } from '@ant-design/icons';
import { Wallet } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const Register = () => {
    const [loading, setLoading] = useState(false);
    const { register } = useAuth();
    const navigate = useNavigate();

    const onFinish = async (values) => {
        setLoading(true);
        try {
            await register(values.email, values.password, values.fullName);
            message.success('Account created! Please sign in.');
            navigate('/login');
        } catch (error) {
            const errors = error.response?.data;
            if (typeof errors === 'object') {
                Object.values(errors).forEach((msg) => message.error(msg));
            } else {
                message.error(typeof errors === 'string' ? errors : 'Registration failed');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-page">
            <div className="auth-card">
                <div className="auth-logo">
                    <div className="logo-icon">
                        <Wallet size={28} />
                    </div>
                    <div>
                        <h1>SpendWiser</h1>
                        <p>Create your account to get started</p>
                    </div>
                </div>

                <Form
                    layout="vertical"
                    onFinish={onFinish}
                    size="large"
                    className="auth-form"
                    requiredMark={false}
                >
                    <Form.Item
                        name="fullName"
                        rules={[{ required: true, message: 'Please enter your name' }]}
                    >
                        <Input prefix={<UserOutlined style={{ color: 'var(--color-text-muted)' }} />} placeholder="Full name" />
                    </Form.Item>

                    <Form.Item
                        name="email"
                        rules={[
                            { required: true, message: 'Please enter your email' },
                            { type: 'email', message: 'Invalid email format' },
                        ]}
                    >
                        <Input prefix={<MailOutlined style={{ color: 'var(--color-text-muted)' }} />} placeholder="Email address" />
                    </Form.Item>

                    <Form.Item
                        name="password"
                        rules={[
                            { required: true, message: 'Please enter a password' },
                            { min: 8, message: 'Password must be at least 8 characters' },
                        ]}
                    >
                        <Input.Password prefix={<LockOutlined style={{ color: 'var(--color-text-muted)' }} />} placeholder="Password (min 8 chars)" />
                    </Form.Item>

                    <Form.Item
                        name="confirmPassword"
                        dependencies={['password']}
                        rules={[
                            { required: true, message: 'Please confirm your password' },
                            ({ getFieldValue }) => ({
                                validator(_, value) {
                                    if (!value || getFieldValue('password') === value) return Promise.resolve();
                                    return Promise.reject(new Error('Passwords do not match'));
                                },
                            }),
                        ]}
                    >
                        <Input.Password prefix={<LockOutlined style={{ color: 'var(--color-text-muted)' }} />} placeholder="Confirm password" />
                    </Form.Item>

                    <Form.Item style={{ marginBottom: 0 }}>
                        <Button
                            type="primary"
                            htmlType="submit"
                            loading={loading}
                            block
                        >
                            Create Account
                        </Button>
                    </Form.Item>
                </Form>

                <div className="auth-footer">
                    Already have an account?{' '}
                    <Link to="/login">Sign in</Link>
                </div>
            </div>
        </div>
    );
};

export default Register;