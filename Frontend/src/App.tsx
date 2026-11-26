import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import CatalogPage from './features/catalog/CatalogPage'
import AuthPage from './features/auth/AuthPage'
import ReceiptPage from './features/receipts/ReceiptPage'
import ExportPage from './features/exports/ExportPage'
import UsersPage from './features/users/UsersPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<AuthPage mode="login" />} />
        <Route path="/forgot-password" element={<AuthPage mode="forgot" />} />
        <Route path="/catalog/:kind" element={<CatalogPage />} />
        <Route path="/receipts" element={<ReceiptPage mode="list" />} />
        <Route path="/receipts/new" element={<ReceiptPage mode="form" />} />
        <Route path="/receipts/:id/edit" element={<ReceiptPage mode="form" />} />
        <Route path="/receipts/:id" element={<ReceiptPage mode="detail" />} />
        <Route path="/exports" element={<ExportPage mode="list" />} />
        <Route path="/exports/new" element={<ExportPage mode="form" />} />
        <Route path="/exports/:id/edit" element={<ExportPage mode="form" />} />
        <Route path="/exports/:id" element={<ExportPage mode="detail" />} />
        <Route path="/admin/users" element={<UsersPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
