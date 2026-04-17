import { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider, App as AntApp } from 'antd';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import Landing from './pages/Landing';
import AppLayout from './components/AppLayout';
import Dashboard from './pages/Dashboard';
import Transactions from './pages/Transactions';
import Categories from './pages/Categories';
import BankAccounts from './pages/BankAccounts';
import Profile from './pages/Profile';
import { lightTheme, darkTheme } from './theme/theme';
import './index.css';

const getPreferredTheme = () => {
    const stored = localStorage.getItem('theme');
    if (stored) return stored;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
};

function App() {
    const [themeMode, setThemeMode] = useState('light');

    useEffect(() => {
        const initial = getPreferredTheme();
        setThemeMode(initial);
        document.documentElement.setAttribute('data-theme', initial);
    }, []);

    const toggleTheme = () => {
        const next = themeMode === 'light' ? 'dark' : 'light';
        setThemeMode(next);
        document.documentElement.setAttribute('data-theme', next);
        localStorage.setItem('theme', next);
    };

    const antTheme = themeMode === 'dark' ? darkTheme : lightTheme;

    return (
        <ConfigProvider theme={antTheme}>
            <AntApp>
                <BrowserRouter>
                    <AuthProvider>
                        <Routes>
                            <Route path="/" element={<Landing onToggleTheme={toggleTheme} themeMode={themeMode} />} />
                            <Route
                                path="/app"
                                element={<AppLayout onToggleTheme={toggleTheme} themeMode={themeMode} />}
                            >
                                <Route index element={<Dashboard />} />
                                <Route path="transactions" element={<Transactions />} />
                                <Route path="categories" element={<Categories />} />
                                <Route path="banks" element={<BankAccounts />} />
                                <Route path="profile" element={<Profile />} />
                            </Route>
                            <Route path="*" element={<Navigate to="/" replace />} />
                        </Routes>
                    </AuthProvider>
                </BrowserRouter>
            </AntApp>
        </ConfigProvider>
    );
}

export default App;
