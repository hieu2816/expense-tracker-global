import { useState } from 'react';
import { Form, Input, Button, message } from 'antd';
import { MailOutlined, LockOutlined } from '@ant-design/icons';
import { Wallet } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const Login = () => {
    const [loading, setLoading] = useState(false);
    const { login } = useAuth();
    const navigate = useNavigate();

    const onFinish = async (values) => {
        setLoading(true);
        try {
            await login(values.email, values.password);
            message.success('Welcome back!');
            navigate('/');
        } catch (error) {
            const msg = error.response?.data?.error || error.response?.data || 'Login failed';
            message.error(typeof msg === 'string' ? msg : 'Invalid credentials');
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
                        <p>Sign in to manage your finances</p>
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
                        rules={[{ required: true, message: 'Please enter your password' }]}
                    >
                        <Input.Password prefix={<LockOutlined style={{ color: 'var(--color-text-muted)' }} />} placeholder="Password" />
                    </Form.Item>

                    <Form.Item style={{ marginBottom: 0 }}>
                        <Button
                            type="primary"
                            htmlType="submit"
                            loading={loading}
                            block
                        >
                            Sign In
                        </Button>
                    </Form.Item>
                </Form>

                <div className="auth-footer">
                    Don't have an account?{' '}
                    <Link to="/register">Create one</Link>
                </div>
            </div>
        </div>
    );
};

export default Login;