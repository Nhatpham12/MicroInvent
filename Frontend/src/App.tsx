import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import CatalogPage from './features/catalog/CatalogPage'
import AuthPage from './features/auth/AuthPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<AuthPage mode="login" />} />
        <Route path="/forgot-password" element={<AuthPage mode="forgot" />} />
        <Route path="/catalog/:kind" element={<CatalogPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
