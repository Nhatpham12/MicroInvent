import { NavLink } from 'react-router-dom'
import type { ReactNode } from 'react'
import './AppShell.css'

const navigation = [
  { label: 'Dashboard', icon: '▦', to: '/dashboard', enabled: false },
  { label: 'Sản phẩm', icon: '□', to: '/catalog/products', enabled: true },
  { label: 'Nhóm sản phẩm', icon: '▤', to: '/catalog/groups', enabled: true },
  { label: 'Nhà cung cấp', icon: '⌂', to: '/catalog/suppliers', enabled: true },
  { label: 'Khách hàng', icon: '♙', to: '/catalog/customers', enabled: true },
  { label: 'Danh mục kho', icon: '▧', to: '/warehouses', enabled: false },
  { label: 'Phiếu nhập', icon: '↓', to: '/receipts', enabled: true },
  { label: 'Phiếu xuất', icon: '↑', to: '/exports', enabled: true },
  { label: 'Chuyển kho', icon: '⇄', to: '/transfers', enabled: false },
  { label: 'Thuê / mượn', icon: '◷', to: '/rentals', enabled: false },
  { label: 'Kiểm kê', icon: '☷', to: '/stocktakes', enabled: false },
  { label: 'Báo cáo', icon: '▥', to: '/reports', enabled: false },
  { label: 'Người dùng & quyền', icon: '♙', to: '/admin/users', enabled: true },
]

type AppShellProps = {
  activePath: string
  children: ReactNode
}

export function AppShell({ activePath, children }: AppShellProps) {
  return (
    <div className="app-shell">
      <header className="app-header">
        <NavLink className="header-brand" to="/catalog/products">
          <span className="header-brand-mark" aria-hidden="true">
            MI
          </span>
          <span>MicroInvent</span>
        </NavLink>
        <div className="header-right">
          <span className="mock-indicator">
            <span />
            Dữ liệu mẫu
          </span>
          <button className="header-icon-button" aria-label="Thông báo" type="button">
            ♧
            <span className="notification-dot" />
          </button>
          <span className="header-divider" />
          <button className="profile-button" type="button">
            <span className="profile-avatar">NA</span>
            <span className="profile-name">Nguyễn An</span>
            <span className="profile-chevron">⌄</span>
          </button>
        </div>
      </header>

      <aside className="app-sidebar" aria-label="Điều hướng chính">
        <nav className="sidebar-nav">
          {navigation.map((item) => {
            const active = activePath === item.to || activePath.startsWith(`${item.to}/`)
            const content = (
              <>
                <span className="sidebar-icon" aria-hidden="true">
                  {item.icon}
                </span>
                <span className="sidebar-label">{item.label}</span>
              </>
            )
            return item.enabled ? (
              <NavLink
                aria-current={active ? 'page' : undefined}
                className={`sidebar-link${active ? ' is-active' : ''}`}
                key={item.to}
                to={item.to}
                title={item.label}
              >
                {content}
              </NavLink>
            ) : (
              <span aria-disabled="true" className="sidebar-link is-disabled" key={item.to} title="Chưa triển khai">
                {content}
              </span>
            )
          })}
        </nav>
        <div className="sidebar-footer">
          <span className="sidebar-footer-dot" />
          <span>Kho mặc định: HN01</span>
        </div>
      </aside>

      <main className="app-main">{children}</main>
    </div>
  )
}
