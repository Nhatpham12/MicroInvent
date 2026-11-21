import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { AppShell } from '../../components/AppShell'
import { catalogConfigs } from './catalogData'
import type {
  CatalogField,
  CatalogKind,
  CatalogRow,
} from './catalogData'
import { useCatalogStore } from './catalogStore'
import './CatalogPage.css'

const pageSize = 7

function isCatalogKind(value: string | undefined): value is CatalogKind {
  return value === 'products' || value === 'groups' || value === 'suppliers' || value === 'customers'
}

function displayValue(key: string, value: string | number | boolean | undefined) {
  if (value === undefined || value === '') return '—'
  if (key === 'defaultPriceIn' || key === 'defaultPriceOut' || key === 'defaultRentPricePerDay') {
    return `${Number(value).toLocaleString('vi-VN')} đ`
  }
  if (key === 'hasSerial') return value ? 'Có' : 'Không'
  if (key === 'customerType') return value === 'BUSINESS' ? 'Doanh nghiệp' : 'Bán lẻ'
  if (key === 'warrantyMonths') return `${value} tháng`
  return String(value)
}

function fieldValue(field: CatalogField, row?: CatalogRow): string | boolean {
  if (!row) return field.type === 'checkbox' ? false : ''
  const value = row[field.key]
  if (field.key === 'customerType') return value === 'BUSINESS' ? 'Doanh nghiệp' : 'Bán lẻ'
  if (typeof value === 'boolean') return value
  return value === undefined ? '' : String(value)
}

function newCode(prefix: string, rows: CatalogRow[]) {
  const nextId = rows.reduce((max, row) => Math.max(max, row.id), 0) + 1
  const width = prefix === 'SP' ? 4 : prefix === 'NSP' ? 2 : 3
  return `${prefix}-${String(nextId).padStart(width, '0')}`
}

function CatalogPage() {
  const { kind: routeKind } = useParams()
  const kind = isCatalogKind(routeKind) ? routeKind : 'products'
  const config = catalogConfigs[kind]
  const rows = useCatalogStore((state) => state.rowsByKind[kind])
  const addRow = useCatalogStore((state) => state.addRow)
  const updateRow = useCatalogStore((state) => state.updateRow)
  const [query, setQuery] = useState('')
  const [filters, setFilters] = useState<Record<string, string>>({})
  const [activeTab, setActiveTab] = useState('all')
  const [page, setPage] = useState(0)
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [dialog, setDialog] = useState<'create' | 'edit' | 'view' | null>(null)
  const [editingRow, setEditingRow] = useState<CatalogRow | undefined>()
  const [feedback, setFeedback] = useState('')

  const filteredRows = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase('vi')
    let result = rows.filter((row) => {
      const matchesQuery =
        !normalizedQuery ||
        Object.values(row).some((value) =>
          String(value).toLocaleLowerCase('vi').includes(normalizedQuery),
        )
      const matchesGroup =
        kind !== 'products' ||
        !filters.group ||
        filters.group === 'Tất cả nhóm' ||
        row.group === filters.group
      const matchesSerial =
        kind !== 'products' ||
        !filters.serial ||
        filters.serial === 'Tất cả' ||
        (filters.serial === 'Có serial' ? row.hasSerial === true : row.hasSerial === false)
      const matchesCustomerType =
        kind !== 'customers' ||
        (filters.customerType !== 'Bán lẻ' || row.customerType === 'RETAIL') &&
          (filters.customerType !== 'Doanh nghiệp' || row.customerType === 'BUSINESS')
      const matchesTab =
        activeTab === 'all' ||
        (kind === 'products' && activeTab === 'serial' && row.hasSerial === true) ||
        (kind === 'products' &&
          activeTab === 'low-stock' &&
          Number(row.stockAvailable ?? 0) < Number(row.minStock ?? 0)) ||
        (kind === 'customers' && activeTab === row.customerType)

      return matchesQuery && matchesGroup && matchesSerial && matchesCustomerType && matchesTab
    })

    if (kind === 'groups' && filters.sort) {
      result = [...result].sort((a, b) => {
        const order = String(a.name).localeCompare(String(b.name), 'vi')
        return filters.sort === 'Tên Z-A' ? -order : order
      })
    }

    return result
  }, [activeTab, filters, kind, query, rows])

  const pageCount = Math.max(1, Math.ceil(filteredRows.length / pageSize))
  const currentPage = Math.min(page, pageCount - 1)
  const visibleRows = filteredRows.slice(currentPage * pageSize, (currentPage + 1) * pageSize)

  function changeFilter(key: string, value: string) {
    setFilters((current) => ({ ...current, [key]: value }))
    setPage(0)
    setSelectedIds([])
  }

  function selectTab(value: string) {
    setActiveTab(value)
    setPage(0)
    setSelectedIds([])
  }

  function openDialog(mode: 'create' | 'edit' | 'view', row?: CatalogRow) {
    setEditingRow(row)
    setDialog(mode)
    setFeedback('')
  }

  function closeDialog() {
    setDialog(null)
    setEditingRow(undefined)
  }

  function saveRow(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editingRow && dialog !== 'create') return

    const formData = new FormData(event.currentTarget)
    const values: Record<string, string | number | boolean> = {}

    for (const field of config.fields) {
      const rawValue = formData.get(field.key)
      if (field.type === 'checkbox') {
        values[field.key] = rawValue === 'on'
        continue
      }

      const value = String(rawValue ?? '').trim()
      if (field.required && !value) {
        setFeedback(`Vui lòng nhập ${field.label.toLocaleLowerCase('vi')}.`)
        return
      }
      if (field.type === 'email' && value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
        setFeedback('Vui lòng nhập email hợp lệ.')
        return
      }
      values[field.key] =
        field.type === 'number' && value ? Number(value) : value
    }

    if (kind === 'customers') {
      values.customerType = values.customerType === 'Doanh nghiệp' ? 'BUSINESS' : 'RETAIL'
    }

    const isEditing = dialog === 'edit' && editingRow
    if (kind === 'products' && !/^[A-Za-z0-9_.-]+$/.test(String(values.code))) {
      setFeedback('Mã sản phẩm chỉ gồm chữ, số, dấu chấm, gạch dưới và gạch nối.')
      return
    }
    if (
      kind === 'products' &&
      ['minStock', 'warrantyMonths'].some(
        (key) => values[key] !== '' && !Number.isInteger(Number(values[key])),
      )
    ) {
      setFeedback('Tồn tối thiểu và thời hạn bảo hành phải là số nguyên không âm.')
      return
    }
    if (
      kind === 'suppliers' &&
      !/^\d{10}(-\d{3})?$/.test(String(values.taxCode))
    ) {
      setFeedback('Mã số thuế gồm 10 số hoặc 10 số-3 số.')
      return
    }
    if (
      kind === 'customers' &&
      values.customerType === 'BUSINESS' &&
      !/^\d{10}(-\d{3})?$/.test(String(values.taxCode ?? ''))
    ) {
      setFeedback('Khách hàng doanh nghiệp cần mã số thuế hợp lệ.')
      return
    }
    const id = isEditing ? editingRow.id : Math.max(0, ...rows.map((row) => row.id)) + 1
    const nextRow: CatalogRow = {
      ...(isEditing ? editingRow : {}),
      ...values,
      id,
      code: String(values.code || (isEditing ? editingRow.code : newCode(config.codePrefix, rows))),
      name: String(values.name ?? (isEditing ? editingRow.name : '')),
      ...(kind === 'products' && !isEditing ? { stockAvailable: 0 } : {}),
      ...(kind === 'groups' && !isEditing
        ? { productCount: 0, createdAt: new Date().toLocaleDateString('vi-VN') }
        : {}),
    }

    if (isEditing) updateRow(kind, nextRow)
    else addRow(kind, nextRow)
    setPage(0)
    setFeedback('')
    closeDialog()
    setFeedback(isEditing ? 'Đã cập nhật dữ liệu mẫu.' : 'Đã thêm vào dữ liệu mẫu.')
  }

  function toggleRow(id: number) {
    setSelectedIds((current) =>
      current.includes(id) ? current.filter((selected) => selected !== id) : [...current, id],
    )
  }

  function toggleVisibleRows() {
    const ids = visibleRows.map((row) => row.id)
    const allSelected = ids.length > 0 && ids.every((id) => selectedIds.includes(id))
    setSelectedIds((current) =>
      allSelected
        ? current.filter((id) => !ids.includes(id))
        : [...new Set([...current, ...ids])],
    )
  }

  function clearFilters() {
    setQuery('')
    setFilters({})
    setActiveTab('all')
    setPage(0)
    setSelectedIds([])
  }

  return (
    <AppShell activePath={`/catalog/${kind}`}>
      <div className="catalog-page">
        <div className="catalog-title-row">
          <div className="catalog-title-copy">
            <p className="catalog-breadcrumb">{config.breadcrumb}</p>
            <h1>{config.title}</h1>
          </div>
          <button className="catalog-primary-button" onClick={() => openDialog('create')} type="button">
            <span aria-hidden="true">＋</span>
            {config.action.replace(/^\+\s*/, '')}
          </button>
        </div>

        <section className="catalog-filter-card" aria-label="Bộ lọc danh mục">
          <label className="catalog-search">
            <span className="search-icon" aria-hidden="true">⌕</span>
            <input
              aria-label="Tìm kiếm"
              onChange={(event) => {
                setQuery(event.target.value)
                setPage(0)
              }}
              placeholder={config.searchPlaceholder}
              value={query}
            />
          </label>

          {config.filters.map((filter) => (
            <select
              aria-label={filter.label}
              className="catalog-select"
              key={filter.key}
              onChange={(event) => changeFilter(filter.key, event.target.value)}
              value={filters[filter.key] ?? ''}
            >
              <option value="">{filter.label}</option>
              {filter.options.map((option) => (
                <option key={option} value={option}>{option}</option>
              ))}
            </select>
          ))}

          <div className="filter-actions">
            <button className="catalog-filter-button" onClick={() => setPage(0)} type="button">
              <span aria-hidden="true">⌕</span> Lọc
            </button>
            <button className="catalog-clear-button" onClick={clearFilters} type="button">
              Xóa lọc
            </button>
          </div>
        </section>

        <section className="catalog-table-card" aria-label={`Danh sách ${config.title.toLocaleLowerCase('vi')}`}>
          <div className="catalog-tabs" role="tablist" aria-label="Lọc nhanh">
            {config.tabs.map((tab) => (
              <button
                aria-selected={activeTab === tab.value}
                className={`catalog-tab${activeTab === tab.value ? ' is-active' : ''}`}
                key={tab.value}
                onClick={() => selectTab(tab.value)}
                role="tab"
                type="button"
              >
                {tab.label}
              </button>
            ))}
          </div>

          {selectedIds.length > 0 && (
            <div className="selection-bar" role="status">
              Đã chọn {selectedIds.length} bản ghi
              <button className="text-action" onClick={() => setSelectedIds([])} type="button">
                Bỏ chọn
              </button>
            </div>
          )}

          <div className="catalog-table-scroll">
            <table className="catalog-table">
              <colgroup>
                <col className="checkbox-column" />
                {config.columns.map((column) => (
                  <col key={column.key} style={{ width: column.width }} />
                ))}
                <col className="actions-column" />
              </colgroup>
              <thead>
                <tr>
                  <th>
                    <input
                      aria-label="Chọn tất cả trên trang"
                      checked={visibleRows.length > 0 && visibleRows.every((row) => selectedIds.includes(row.id))}
                      onChange={toggleVisibleRows}
                      type="checkbox"
                    />
                  </th>
                  {config.columns.map((column) => <th key={column.key}>{column.label}</th>)}
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {visibleRows.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <input
                        aria-label={`Chọn ${row.name}`}
                        checked={selectedIds.includes(row.id)}
                        onChange={() => toggleRow(row.id)}
                        type="checkbox"
                      />
                    </td>
                    {config.columns.map((column) => (
                      <td key={column.key} title={String(displayValue(column.key, row[column.key]))}>
                        {column.key === 'hasSerial' ? (
                          <span className={`serial-tag${row.hasSerial ? ' is-yes' : ''}`}>
                            {displayValue(column.key, row[column.key])}
                          </span>
                        ) : (
                          displayValue(column.key, row[column.key])
                        )}
                      </td>
                    ))}
                    <td>
                      <div className="row-actions">
                        <button className="text-action" onClick={() => openDialog('view', row)} type="button">
                          Xem
                        </button>
                        <button className="text-action" onClick={() => openDialog('edit', row)} type="button">
                          Sửa
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {visibleRows.length === 0 && (
                  <tr>
                    <td className="empty-cell" colSpan={config.columns.length + 2}>
                      <span className="empty-icon" aria-hidden="true">⌕</span>
                      <strong>Không tìm thấy dữ liệu</strong>
                      <span>Thử thay đổi từ khóa hoặc bộ lọc.</span>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <footer className="catalog-pagination">
            <span>
              {filteredRows.length === 0
                ? 'Không có bản ghi'
                : `Hiển thị ${currentPage * pageSize + 1}–${Math.min((currentPage + 1) * pageSize, filteredRows.length)} trong ${filteredRows.length} bản ghi`}
            </span>
            <div className="pagination-controls" aria-label="Phân trang">
              <button
                aria-label="Trang trước"
                disabled={currentPage === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                type="button"
              >
                ‹
              </button>
              {Array.from({ length: pageCount }, (_, index) => (
                <button
                  aria-current={currentPage === index ? 'page' : undefined}
                  className={currentPage === index ? 'is-current' : ''}
                  key={index}
                  onClick={() => setPage(index)}
                  type="button"
                >
                  {index + 1}
                </button>
              ))}
              <button
                aria-label="Trang sau"
                disabled={currentPage >= pageCount - 1}
                onClick={() => setPage((current) => Math.min(pageCount - 1, current + 1))}
                type="button"
              >
                ›
              </button>
            </div>
          </footer>
        </section>

        {feedback && (
          <div className="catalog-toast" role="status">
            <span aria-hidden="true">✓</span>
            {feedback}
            <button aria-label="Đóng thông báo" onClick={() => setFeedback('')} type="button">×</button>
          </div>
        )}

        {dialog && (
          <CatalogDialog
            config={config}
            feedback={feedback}
            mode={dialog}
            onClose={closeDialog}
            onSave={saveRow}
            row={editingRow}
          />
        )}
      </div>
    </AppShell>
  )
}

function CatalogDialog({
  config,
  feedback,
  mode,
  onClose,
  onSave,
  row,
}: {
  config: (typeof catalogConfigs)[CatalogKind]
  feedback: string
  mode: 'create' | 'edit' | 'view'
  onClose: () => void
  onSave: (event: FormEvent<HTMLFormElement>) => void
  row?: CatalogRow
}) {
  const readonly = mode === 'view'
  const title =
    mode === 'create'
      ? config.kind === 'groups' ? 'Thêm nhóm sản phẩm' : `Thêm ${config.title.toLocaleLowerCase('vi')}`
      : mode === 'edit'
        ? `Sửa ${config.title.toLocaleLowerCase('vi')}`
        : `Chi tiết ${config.title.toLocaleLowerCase('vi')}`

  return (
    <div
      className="dialog-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
      role="presentation"
    >
      <section aria-labelledby="catalog-dialog-title" aria-modal="true" className="catalog-dialog" role="dialog">
        <header className="dialog-header">
          <h2 id="catalog-dialog-title">{title}</h2>
          <button aria-label="Đóng" className="dialog-close" onClick={onClose} type="button">×</button>
        </header>

        <form className="dialog-form" onSubmit={onSave}>
          <div className="dialog-fields">
            {config.fields.map((field) => (
              <label
                className={`dialog-field${field.type === 'checkbox' ? ' checkbox-field' : ''}`}
                key={field.key}
              >
                {field.type === 'checkbox' ? (
                  <>
                    <input
                      defaultChecked={Boolean(fieldValue(field, row))}
                      disabled={readonly}
                      name={field.key}
                      type="checkbox"
                    />
                    <span>{field.label}</span>
                  </>
                ) : (
                  <>
                    <span>{field.label}{field.required && <b> *</b>}</span>
                    {field.type === 'select' ? (
                      <select
                        defaultValue={String(fieldValue(field, row))}
                        disabled={readonly}
                        name={field.key}
                        required={field.required}
                      >
                        <option value="">{field.placeholder}</option>
                        {field.options?.map((option) => <option key={option}>{option}</option>)}
                      </select>
                    ) : (
                      <input
                        defaultValue={String(fieldValue(field, row))}
                        disabled={readonly}
                        name={field.key}
                        min={field.type === 'number' ? '0' : undefined}
                        placeholder={field.placeholder}
                        required={field.required}
                        step={
                          field.type !== 'number'
                            ? undefined
                            : field.key === 'minStock' || field.key === 'warrantyMonths'
                              ? '1'
                              : '0.01'
                        }
                        type={field.type ?? 'text'}
                      />
                    )}
                  </>
                )}
              </label>
            ))}
          </div>

          {feedback && <p className="dialog-feedback" role="alert">{feedback}</p>}

          <footer className="dialog-footer">
            <button className="catalog-clear-button" onClick={onClose} type="button">
              {readonly ? 'Đóng' : 'Hủy'}
            </button>
            {!readonly && (
              <button className="catalog-primary-button" type="submit">
                {mode === 'edit' ? 'Lưu thay đổi' : 'Thêm mới'}
              </button>
            )}
          </footer>
        </form>
      </section>
    </div>
  )
}

export default CatalogPage
