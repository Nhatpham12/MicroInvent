import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { AppShell } from '../../components/AppShell'
import './UsersPage.css'

type UserRole = 'ADMIN' | 'MANAGER' | 'ACCOUNTANT' | 'STOREKEEPER' | 'SALES'
type WarehouseAccess = { code: string; access: boolean; write: boolean }
type User = {
  id: number
  username: string
  fullName: string
  email: string
  role: UserRole
  defaultWarehouse: string
  active: boolean
  warehouses: WarehouseAccess[]
}

const roleLabels: Record<UserRole, string> = {
  ADMIN: 'Admin',
  MANAGER: 'Quản lý',
  ACCOUNTANT: 'Kế toán',
  STOREKEEPER: 'Thủ kho',
  SALES: 'Bán hàng',
}
const roles = Object.keys(roleLabels) as UserRole[]
const warehouseCodes = ['HN01', 'HCM01', 'DN01']
const initialUsers: User[] = [
  { id: 1, username: 'admin', fullName: 'Nguyễn Văn An', email: 'an@example.com', role: 'ADMIN', defaultWarehouse: 'HN01', active: true, warehouses: warehouseCodes.map((code) => ({ code, access: true, write: true })) },
  { id: 2, username: 'thukho.hn', fullName: 'Lê Quốc Cường', email: 'cuong@example.com', role: 'STOREKEEPER', defaultWarehouse: 'HN01', active: true, warehouses: [{ code: 'HN01', access: true, write: true }, { code: 'HCM01', access: false, write: false }, { code: 'DN01', access: false, write: false }] },
  { id: 3, username: 'manager.hcm', fullName: 'Trần Thị Bình', email: 'binh@example.com', role: 'MANAGER', defaultWarehouse: 'HCM01', active: true, warehouses: [{ code: 'HN01', access: true, write: false }, { code: 'HCM01', access: true, write: true }, { code: 'DN01', access: false, write: false }] },
  { id: 4, username: 'accounting', fullName: 'Phạm Minh Hà', email: 'ha@example.com', role: 'ACCOUNTANT', defaultWarehouse: 'HN01', active: true, warehouses: [{ code: 'HN01', access: true, write: false }, { code: 'HCM01', access: true, write: false }, { code: 'DN01', access: false, write: false }] },
  { id: 5, username: 'sales01', fullName: 'Đỗ Thanh Mai', email: 'mai@example.com', role: 'SALES', defaultWarehouse: 'HCM01', active: false, warehouses: [{ code: 'HN01', access: false, write: false }, { code: 'HCM01', access: true, write: true }, { code: 'DN01', access: false, write: false }] },
]

function emptyUser(id: number): User {
  return {
    id,
    username: '',
    fullName: '',
    email: '',
    role: 'STOREKEEPER',
    defaultWarehouse: '',
    active: true,
    warehouses: warehouseCodes.map((code) => ({ code, access: false, write: false })),
  }
}

function UsersPage() {
  const [users, setUsers] = useState(initialUsers)
  const [query, setQuery] = useState('')
  const [warehouseFilter, setWarehouseFilter] = useState('')
  const [roleFilter, setRoleFilter] = useState<'all' | UserRole>('all')
  const [editing, setEditing] = useState<User | null>(null)
  const [feedback, setFeedback] = useState('')

  const filteredUsers = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('vi')
    return users.filter((user) =>
      (!needle || `${user.username} ${user.fullName} ${user.email}`.toLocaleLowerCase('vi').includes(needle)) &&
      (roleFilter === 'all' || user.role === roleFilter) &&
      (!warehouseFilter || user.warehouses.some((item) => item.code === warehouseFilter && item.access)),
    )
  }, [query, roleFilter, users, warehouseFilter])

  function toggleActive(user: User) {
    setUsers((current) => current.map((item) => item.id === user.id ? { ...item, active: !item.active } : item))
    setFeedback(`Đã ${user.active ? 'tạm ngưng' : 'kích hoạt'} tài khoản ${user.username}.`)
  }

  function saveUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editing) return
    const formData = new FormData(event.currentTarget)
    const username = String(formData.get('username') ?? '').trim()
    const fullName = String(formData.get('fullName') ?? '').trim()
    const email = String(formData.get('email') ?? '').trim()
    const roleValue = String(formData.get('role') ?? '')
    const role = roles.find((item) => item === roleValue)
    const defaultWarehouse = String(formData.get('defaultWarehouse') ?? '')
    if (!username || !fullName || !email || !role) {
      setFeedback('Vui lòng nhập tên đăng nhập, họ tên, email và vai trò.')
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setFeedback('Vui lòng nhập email hợp lệ.')
      return
    }
    if (users.some((item) => item.id !== editing.id && item.username.toLowerCase() === username.toLowerCase())) {
      setFeedback('Tên đăng nhập đã tồn tại.')
      return
    }
    const nextUser: User = { ...editing, username, fullName, email, role, defaultWarehouse }
    setUsers((current) => editing.id < 0
      ? [...current, { ...nextUser, id: Math.max(0, ...current.map((item) => item.id)) + 1 }]
      : current.map((item) => item.id === editing.id ? nextUser : item))
    setEditing(null)
    setFeedback('Đã lưu dữ liệu mẫu người dùng.')
  }

  function updateWarehouse(code: string, field: 'access' | 'write', checked: boolean) {
    if (!editing) return
    setEditing((current) => current && ({
      ...current,
      warehouses: current.warehouses.map((item) => item.code === code
        ? { ...item, [field]: checked, ...(field === 'access' && !checked ? { write: false } : {}) }
        : item),
    }))
  }

  return (
    <AppShell activePath="/admin/users">
      <div className="users-page">
        <div className="users-title-row">
          <div>
            <p className="users-breadcrumb">Trang chủ / Quản trị / Người dùng &amp; phân quyền</p>
            <h1>Người dùng &amp; phân quyền</h1>
          </div>
          <button className="users-primary-button" onClick={() => {
            setFeedback('')
            setEditing(emptyUser(-1))
          }} type="button">＋ Thêm người dùng</button>
        </div>

        <section className="users-filter-card">
          <label className="users-search">
            <span aria-hidden="true">⌕</span>
            <input aria-label="Tìm người dùng" onChange={(event) => setQuery(event.target.value)} placeholder="Tìm tên đăng nhập, họ tên…" value={query} />
          </label>
          <select aria-label="Lọc vai trò" onChange={(event) => setRoleFilter(event.target.value as 'all' | UserRole)} value={roleFilter}>
            <option value="all">Tất cả vai trò</option>
            {roles.map((role) => <option key={role} value={role}>{roleLabels[role]}</option>)}
          </select>
          <select aria-label="Lọc kho" onChange={(event) => setWarehouseFilter(event.target.value)} value={warehouseFilter}>
            <option value="">Tất cả kho</option>
            {warehouseCodes.map((code) => <option key={code} value={code}>{code}</option>)}
          </select>
          <button className="users-secondary-button" onClick={() => {
            setQuery('')
            setRoleFilter('all')
            setWarehouseFilter('')
          }} type="button">Xóa lọc</button>
        </section>

        <section className="users-table-card">
          <div className="users-tabs" role="tablist" aria-label="Vai trò người dùng">
            {(['all', ...roles] as const).map((role) => (
              <button
                aria-selected={roleFilter === role}
                className={roleFilter === role ? 'is-active' : ''}
                key={role}
                onClick={() => setRoleFilter(role)}
                role="tab"
                type="button"
              >
                {role === 'all' ? 'Tất cả' : roleLabels[role]}
              </button>
            ))}
          </div>
          <div className="users-table-scroll">
            <table className="users-table">
              <thead><tr><th>Tên đăng nhập</th><th>Họ tên</th><th>Email</th><th>Vai trò</th><th>Kho mặc định</th><th>Số kho</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
              <tbody>
                {filteredUsers.map((user) => (
                  <tr key={user.id}>
                    <td className="users-username">{user.username}</td>
                    <td>{user.fullName}</td>
                    <td>{user.email}</td>
                    <td>{roleLabels[user.role]}</td>
                    <td>{user.defaultWarehouse || '—'}</td>
                    <td>{user.warehouses.filter((item) => item.access).length}</td>
                    <td><span className={`users-status${user.active ? ' is-active' : ''}`}>{user.active ? 'Hoạt động' : 'Tạm ngưng'}</span></td>
                    <td>
                      <div className="users-row-actions">
                        <button className="users-text-button" onClick={() => {
                          setFeedback('')
                          setEditing({ ...user, warehouses: user.warehouses.map((item) => ({ ...item })) })
                        }} type="button">Sửa</button>
                        <button className="users-text-button" onClick={() => toggleActive(user)} type="button">{user.active ? 'Tạm ngưng' : 'Kích hoạt'}</button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredUsers.length === 0 && <tr><td className="users-empty" colSpan={8}>Không tìm thấy người dùng phù hợp.</td></tr>}
              </tbody>
            </table>
          </div>
          <footer className="users-footer">Hiển thị {filteredUsers.length} trong {users.length} người dùng</footer>
        </section>

        {feedback && !editing && <div className="users-toast" role="status">{feedback}<button aria-label="Đóng thông báo" onClick={() => setFeedback('')} type="button">×</button></div>}

        {editing && (
          <div className="users-modal-backdrop" onMouseDown={(event) => {
            if (event.target === event.currentTarget) setEditing(null)
          }}>
            <section aria-labelledby="users-modal-title" aria-modal="true" className="users-modal" role="dialog">
              <header className="users-modal-header">
                <div><h2 id="users-modal-title">{editing.id < 0 ? 'Thêm người dùng' : 'Cập nhật người dùng'}</h2><p>Thiết lập tài khoản và quyền truy cập kho.</p></div>
                <button aria-label="Đóng" className="users-close-button" onClick={() => setEditing(null)} type="button">×</button>
              </header>
              <form className="users-modal-form" onSubmit={saveUser}>
                <div className="users-modal-grid">
                  <label>Tên đăng nhập <b>*</b><input autoComplete="off" defaultValue={editing.username} name="username" required /></label>
                  <label>Họ tên <b>*</b><input defaultValue={editing.fullName} name="fullName" required /></label>
                  <label>Email <b>*</b><input defaultValue={editing.email} name="email" required type="email" /></label>
                  <label>Vai trò <b>*</b>
                    <select defaultValue={editing.role} name="role" required>
                      {roles.map((role) => <option key={role} value={role}>{roleLabels[role]}</option>)}
                    </select>
                  </label>
                  <label>Kho mặc định
                    <select defaultValue={editing.defaultWarehouse} name="defaultWarehouse">
                      <option value="">Chưa chọn</option>
                      {warehouseCodes.map((code) => <option key={code} value={code}>{code}</option>)}
                    </select>
                  </label>
                  <label className="users-active-field">
                    <input checked={editing.active} onChange={(event) => setEditing((current) => current && ({ ...current, active: event.target.checked }))} type="checkbox" />
                    Đang hoạt động
                  </label>
                </div>
                <div className="users-permissions">
                  <h3>Phân quyền kho</h3>
                  <div className="users-permission-table">
                    <div className="users-permission-head"><strong>Kho</strong><strong>Được truy cập</strong><strong>Được ghi dữ liệu</strong></div>
                    {editing.warehouses.map((item) => (
                      <div className="users-permission-row" key={item.code}>
                        <span>{item.code}</span>
                        <label aria-label={`Quyền truy cập ${item.code}`}><input checked={item.access} onChange={(event) => updateWarehouse(item.code, 'access', event.target.checked)} type="checkbox" /></label>
                        <label aria-label={`Quyền ghi dữ liệu ${item.code}`}><input checked={item.write} disabled={!item.access} onChange={(event) => updateWarehouse(item.code, 'write', event.target.checked)} type="checkbox" /></label>
                      </div>
                    ))}
                  </div>
                </div>
                {feedback && <p className="users-form-feedback" role="alert">{feedback}</p>}
                <footer className="users-modal-actions">
                  <button className="users-secondary-button" onClick={() => setEditing(null)} type="button">Hủy</button>
                  <button className="users-primary-button" type="submit">Lưu người dùng</button>
                </footer>
              </form>
            </section>
          </div>
        )}
      </div>
    </AppShell>
  )
}

export default UsersPage
