import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AppShell } from '../../components/AppShell'
import {
  exportPartners,
  exportProducts,
  exportWarehouses,
  useExportStore,
} from './exportStore'
import type {
  DeliveryMethod,
  ExportInput,
  ExportLine,
  ExportShortage,
  ExportStatus,
  ExportType,
} from './exportStore'
import './ExportPage.css'

type ExportPageProps = {
  mode: 'list' | 'form' | 'detail'
}

const statusNames: Record<ExportStatus, string> = {
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  EXPORTED: 'Đã xuất',
  CANCELLED: 'Đã hủy',
}

const typeNames: Record<ExportType, string> = {
  SALE: 'Bán hàng',
  DAMAGED: 'Hàng hỏng',
  WARRANTY: 'Bảo hành',
  RETURN_SUPPLIER: 'Trả nhà cung cấp',
  OTHER: 'Khác',
}

const paymentNames = {
  UNPAID: 'Chưa thanh toán',
  PARTIAL: 'Thanh toán một phần',
  PAID: 'Đã thanh toán',
} as const

const money = (value: number) => `${value.toLocaleString('vi-VN')} đ`

function dateTime(value: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })
}

function makeLine(productId: number, warehouseId: number): ExportLine {
  const product = exportProducts.find((item) => item.id === productId) ?? exportProducts[0]
  return {
    productId: product.id,
    productCode: product.code,
    productName: product.name,
    unit: product.unit,
    hasSerial: product.hasSerial,
    qty: 1,
    unitPrice: product.defaultPrice,
    serials: [],
    available: product.availableByWarehouse[warehouseId] ?? 0,
  }
}

function getShortages(lines: ExportLine[], warehouseId: number): ExportShortage[] {
  return lines.flatMap((line) => {
    const shortage = Math.max(0, line.qty - line.available)
    if (!shortage) return []
    return [{
      productId: line.productId,
      productName: line.productName,
      requested: line.qty,
      available: line.available,
      shortage,
      otherWarehouses: exportWarehouses
        .filter((warehouse) => warehouse.id !== warehouseId)
        .map((warehouse) => {
          const product = exportProducts.find((item) => item.id === line.productId)
          return {
            warehouseId: warehouse.id,
            warehouseName: warehouse.name,
            available: product?.availableByWarehouse[warehouse.id] ?? 0,
          }
        })
        .filter((warehouse) => warehouse.available > 0),
    }]
  })
}

function ExportPage({ mode }: ExportPageProps) {
  const { id } = useParams()
  const slips = useExportStore((state) => state.slips)
  const slip = id ? slips.find((item) => item.id === Number(id)) : undefined

  if (mode === 'list') return <ExportListPage />
  if (mode === 'form') {
    if (id && !slip) {
      return (
        <AppShell activePath="/exports">
          <div className="export-page">
            <section className="export-empty">
              <h1>Không tìm thấy phiếu xuất</h1>
              <p>Phiếu xuất này không tồn tại hoặc dữ liệu mẫu đã được làm mới.</p>
              <Link className="export-button export-button-primary" to="/exports">Quay lại danh sách</Link>
            </section>
          </div>
        </AppShell>
      )
    }
    if (slip && (slip.status === 'EXPORTED' || slip.status === 'CANCELLED')) {
      return (
        <AppShell activePath="/exports">
          <div className="export-page">
            <section className="export-empty">
              <h1>Không thể sửa phiếu xuất</h1>
              <p>Phiếu đã xuất hoặc đã hủy nên không còn được chỉnh sửa.</p>
              <Link className="export-button export-button-primary" to={`/exports/${slip.id}`}>Xem chi tiết</Link>
            </section>
          </div>
        </AppShell>
      )
    }
    return <ExportFormPage key={slip?.id ?? 'new'} slip={slip} />
  }

  if (!slip) {
    return (
      <AppShell activePath="/exports">
        <div className="export-page">
          <section className="export-empty">
            <h1>Không tìm thấy phiếu xuất</h1>
            <p>Phiếu xuất này không tồn tại hoặc dữ liệu mẫu đã được làm mới.</p>
            <Link className="export-button export-button-primary" to="/exports">Quay lại danh sách</Link>
          </section>
        </div>
      </AppShell>
    )
  }
  return <ExportDetailPage slip={slip} />
}

function ExportListPage() {
  const slips = useExportStore((state) => state.slips)
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [warehouseId, setWarehouseId] = useState('')
  const [exportType, setExportType] = useState('')
  const [tab, setTab] = useState<'ALL' | ExportStatus>('ALL')

  const rows = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('vi')
    return slips.filter((slip) => {
      const matchesText = !needle ||
        [slip.code, slip.customerName, slip.warehouseCode]
          .some((value) => value.toLocaleLowerCase('vi').includes(needle))
      return matchesText &&
        (!warehouseId || String(slip.warehouseId) === warehouseId) &&
        (!exportType || slip.exportType === exportType) &&
        (tab === 'ALL' || slip.status === tab)
    })
  }, [exportType, query, slips, tab, warehouseId])

  return (
    <AppShell activePath="/exports">
      <div className="export-page">
        <div className="export-title-row">
          <div>
            <p className="export-breadcrumb">Trang chủ / Nghiệp vụ / Phiếu xuất</p>
            <h1>Phiếu xuất</h1>
          </div>
          <button className="export-button export-button-primary" onClick={() => navigate('/exports/new')} type="button">
            <span aria-hidden="true">＋</span> Tạo phiếu xuất
          </button>
        </div>

        <section className="export-filter-card" aria-label="Bộ lọc phiếu xuất">
          <label className="export-search">
            <span aria-hidden="true">⌕</span>
            <input
              aria-label="Tìm mã phiếu hoặc khách hàng"
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Tìm mã phiếu, khách hàng…"
              value={query}
            />
          </label>
          <select aria-label="Kho xuất" onChange={(event) => setWarehouseId(event.target.value)} value={warehouseId}>
            <option value="">Tất cả kho</option>
            {exportWarehouses.map((item) => <option key={item.id} value={item.id}>{item.code} – {item.name}</option>)}
          </select>
          <select aria-label="Loại xuất" onChange={(event) => setExportType(event.target.value)} value={exportType}>
            <option value="">Tất cả loại xuất</option>
            {Object.entries(typeNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
          <button className="export-button export-button-secondary" onClick={() => {
            setQuery('')
            setWarehouseId('')
            setExportType('')
            setTab('ALL')
          }} type="button">Xóa lọc</button>
        </section>

        <section className="export-card">
          <div className="export-tabs" role="tablist" aria-label="Lọc trạng thái">
            {([
              ['ALL', 'Tất cả'],
              ['PENDING', 'Chờ xác nhận'],
              ['CONFIRMED', 'Đã xác nhận'],
              ['EXPORTED', 'Đã xuất'],
              ['CANCELLED', 'Đã hủy'],
            ] as const).map(([value, label]) => (
              <button
                aria-selected={tab === value}
                className={tab === value ? 'is-active' : ''}
                key={value}
                onClick={() => setTab(value)}
                role="tab"
                type="button"
              >
                {label}
              </button>
            ))}
          </div>
          <div className="export-table-scroll">
            <table className="export-table">
              <thead>
                <tr>
                  <th>Mã phiếu</th><th>Khách hàng</th><th>Loại xuất</th><th>Kho xuất</th>
                  <th>Tổng tiền</th><th>Thanh toán</th><th>Trạng thái</th><th>Ngày tạo</th><th />
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id}>
                    <td><Link className="export-code-link" to={`/exports/${row.id}`}>{row.code}</Link></td>
                    <td>{row.customerName || '—'}</td>
                    <td>{typeNames[row.exportType]}</td>
                    <td>{row.warehouseCode}</td>
                    <td>{money(row.totalAmount)}</td>
                    <td>{paymentNames[row.paymentStatus]}</td>
                    <td><span className={`export-status status-${row.status.toLowerCase()}`}>{statusNames[row.status]}</span></td>
                    <td>{dateTime(row.createdAt)}</td>
                    <td><Link className="export-row-action" to={`/exports/${row.id}`}>Chi tiết</Link></td>
                  </tr>
                ))}
                {rows.length === 0 && <tr><td className="export-no-results" colSpan={9}>Không có phiếu xuất phù hợp.</td></tr>}
              </tbody>
            </table>
          </div>
          <div className="export-pagination">
            <span>Hiển thị {rows.length ? 1 : 0}–{rows.length} trong {rows.length}</span>
            <div aria-label="Phân trang" className="export-page-buttons">
              <button aria-current="page" type="button">1</button>
              <button aria-label="Trang tiếp theo" disabled type="button">›</button>
            </div>
          </div>
        </section>
      </div>
    </AppShell>
  )
}

type ExportFormPageProps = {
  slip?: ReturnType<typeof useExportStore.getState>['slips'][number]
}

function ExportFormPage({ slip }: ExportFormPageProps) {
  const navigate = useNavigate()
  const create = useExportStore((state) => state.create)
  const update = useExportStore((state) => state.update)
  const setStatus = useExportStore((state) => state.setStatus)
  const [warehouseId, setWarehouseId] = useState(slip?.warehouseId ?? exportWarehouses[0].id)
  const [customerId, setCustomerId] = useState(slip?.customerId ? String(slip.customerId) : '')
  const [exportType, setExportType] = useState<ExportType>(slip?.exportType ?? 'SALE')
  const [dueDate, setDueDate] = useState(slip?.dueDate ?? '')
  const [deliveryMethod, setDeliveryMethod] = useState<DeliveryMethod>(slip?.deliveryMethod ?? 'PICKUP')
  const [trackingNo, setTrackingNo] = useState(slip?.trackingNo ?? '')
  const [vatRate, setVatRate] = useState(slip?.vatRate ?? '10')
  const [depositAmount, setDepositAmount] = useState(slip?.depositAmount ?? 0)
  const [note, setNote] = useState(slip?.note ?? '')
  const [lines, setLines] = useState<ExportLine[]>(
    slip?.lines.map((item) => ({ ...item })) ?? [makeLine(exportProducts[0].id, exportWarehouses[0].id)],
  )
  const [error, setError] = useState('')

  const shortages = useMemo(() => getShortages(lines, warehouseId), [lines, warehouseId])
  const subtotal = lines.reduce((sum, item) => sum + item.qty * item.unitPrice, 0)
  const vatAmount = vatRate === 'NONE' ? 0 : Math.round(subtotal * Number(vatRate)) / 100
  const total = subtotal + vatAmount
  const isSale = exportType === 'SALE'

  function changeWarehouse(value: number) {
    setWarehouseId(value)
    setLines((current) => current.map((item) => {
      const product = exportProducts.find((option) => option.id === item.productId)
      return { ...item, available: product?.availableByWarehouse[value] ?? 0 }
    }))
  }

  function changeProduct(index: number, productId: number) {
    const next = makeLine(productId, warehouseId)
    setLines((current) => current.map((item, lineIndex) => lineIndex === index ? next : item))
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    const confirmImmediately = (event.nativeEvent as SubmitEvent).submitter?.getAttribute('value') === 'confirm'
    if (isSale && !customerId) {
      setError('Vui lòng chọn khách hàng cho phiếu bán hàng.')
      return
    }
    if (lines.length === 0 || lines.some((item) => item.qty < 1 || item.unitPrice < 0)) {
      setError('Mỗi dòng sản phẩm cần có số lượng lớn hơn 0 và đơn giá hợp lệ.')
      return
    }
    if (lines.some((item) => item.hasSerial && item.serials.length !== item.qty)) {
      setError('Số serial phải khớp với số lượng của từng sản phẩm quản lý theo serial.')
      return
    }
    if (confirmImmediately && shortages.length > 0) {
      setError('Không thể xác nhận vì kho xuất đang thiếu hàng. Hãy lưu phiếu và xử lý thiếu tồn trước.')
      return
    }
    const warehouse = exportWarehouses.find((item) => item.id === warehouseId)
    if (!warehouse) {
      setError('Vui lòng chọn kho xuất hợp lệ.')
      return
    }
    const customer = exportPartners.find((item) => item.id === Number(customerId))
    const input: ExportInput = {
      exportType,
      warehouseId,
      warehouseCode: warehouse.code,
      warehouseName: warehouse.name,
      customerId: customer?.id ?? null,
      customerName: customer?.name ?? '',
      deliveryMethod,
      trackingNo: deliveryMethod === 'CARRIER' ? trackingNo.trim() : '',
      vatRate,
      depositAmount: Math.max(0, Number(depositAmount) || 0),
      dueDate,
      salesUserId: 1,
      splitFromId: slip?.splitFromId ?? null,
      note: note.trim(),
      lines,
      shortages,
    }
    if (slip) {
      const prompt = confirmImmediately
        ? 'Lưu thay đổi và xác nhận phiếu xuất?'
        : 'Lưu thay đổi phiếu xuất? Phiếu đã xác nhận sẽ trở về trạng thái chờ xác nhận.'
      if (window.confirm(prompt)) {
        update(slip.id, input)
        if (confirmImmediately) setStatus(slip.id, 'CONFIRMED')
        navigate(`/exports/${slip.id}`)
      }
      return
    }
    const created = create(input)
    if (confirmImmediately) setStatus(created.id, 'CONFIRMED')
    navigate(`/exports/${created.id}`)
  }

  return (
    <AppShell activePath="/exports">
      <div className="export-page">
        <div className="export-title-row">
          <div>
            <p className="export-breadcrumb">Trang chủ / Nghiệp vụ / Phiếu xuất / {slip ? 'Chỉnh sửa' : 'Tạo mới'}</p>
            <h1>{slip ? `Chỉnh sửa ${slip.code}` : 'Tạo phiếu xuất'}</h1>
          </div>
          <Link className="export-button export-button-secondary" to="/exports">Quay lại danh sách</Link>
        </div>

        <form className="export-form" onSubmit={submit}>
          <section className="export-card export-form-card">
            <h2>Thông tin chung</h2>
            <div className="export-form-grid">
              <label>
                <span>Kho xuất <b>*</b></span>
                <select onChange={(event) => changeWarehouse(Number(event.target.value))} value={warehouseId}>
                  {exportWarehouses.map((item) => <option key={item.id} value={item.id}>{item.code} – {item.name}</option>)}
                </select>
              </label>
              <label>
                <span>Khách hàng {isSale && <b>*</b>}</span>
                <select onChange={(event) => setCustomerId(event.target.value)} value={customerId}>
                  <option value="">Chọn khách hàng</option>
                  {exportPartners.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
                </select>
              </label>
              <label>
                <span>Loại xuất <b>*</b></span>
                <select onChange={(event) => setExportType(event.target.value as ExportType)} value={exportType}>
                  {Object.entries(typeNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
              <label>
                <span>Hạn thanh toán</span>
                <input onChange={(event) => setDueDate(event.target.value)} type="date" value={dueDate} />
              </label>
              <label>
                <span>Hình thức giao hàng</span>
                <select onChange={(event) => setDeliveryMethod(event.target.value as DeliveryMethod)} value={deliveryMethod}>
                  <option value="PICKUP">Khách nhận tại kho</option>
                  <option value="CARRIER">Đơn vị vận chuyển</option>
                </select>
              </label>
              {deliveryMethod === 'CARRIER' && (
                <label>
                  <span>Mã vận đơn</span>
                  <input onChange={(event) => setTrackingNo(event.target.value)} placeholder="Nhập mã vận đơn" value={trackingNo} />
                </label>
              )}
              <label>
                <span>VAT</span>
                <select onChange={(event) => setVatRate(event.target.value)} value={vatRate}>
                  <option value="NONE">Không áp dụng</option>
                  <option value="5">5%</option>
                  <option value="8">8%</option>
                  <option value="10">10%</option>
                </select>
              </label>
              <label>
                <span>Tiền cọc</span>
                <input min="0" onChange={(event) => setDepositAmount(Number(event.target.value))} type="number" value={depositAmount} />
              </label>
              <label className="export-note-field">
                <span>Ghi chú</span>
                <textarea onChange={(event) => setNote(event.target.value)} placeholder="Nhập ghi chú…" rows={2} value={note} />
              </label>
            </div>
          </section>

          <section className="export-card export-form-card">
            <div className="export-section-heading">
              <h2>Sản phẩm xuất</h2>
              <button className="export-button export-button-secondary" onClick={() => setLines((current) => [...current, makeLine(exportProducts[0].id, warehouseId)])} type="button">
                ＋ Thêm dòng
              </button>
            </div>
            <div className="export-table-scroll">
              <table className="export-table export-lines-table">
                <thead>
                  <tr><th>STT</th><th>Sản phẩm</th><th>Serial</th><th>Tồn khả dụng</th><th>SL</th><th>Đơn giá</th><th>Thành tiền</th><th /></tr>
                </thead>
                <tbody>
                  {lines.map((item, index) => (
                    <tr key={`${index}-${item.productId}`}>
                      <td>{index + 1}</td>
                      <td>
                        <select aria-label={`Sản phẩm dòng ${index + 1}`} onChange={(event) => changeProduct(index, Number(event.target.value))} value={item.productId}>
                          {exportProducts.map((product) => <option key={product.id} value={product.id}>{product.code} – {product.name}</option>)}
                        </select>
                      </td>
                      <td>
                        <input
                          aria-label={`Serial dòng ${index + 1}`}
                          disabled={!item.hasSerial}
                          onChange={(event) => setLines((current) => current.map((line, lineIndex) => lineIndex === index
                            ? { ...line, serials: event.target.value.split(',').map((serial) => serial.trim()).filter(Boolean) }
                            : line))}
                          placeholder={item.hasSerial ? 'Nhập serial, cách nhau bằng dấu phẩy' : 'Không yêu cầu'}
                          value={item.serials.join(', ')}
                        />
                      </td>
                      <td>{item.available}</td>
                      <td><input aria-label={`Số lượng dòng ${index + 1}`} min="1" onChange={(event) => setLines((current) => current.map((line, lineIndex) => lineIndex === index ? { ...line, qty: Number(event.target.value) } : line))} type="number" value={item.qty} /></td>
                      <td><input aria-label={`Đơn giá dòng ${index + 1}`} min="0" onChange={(event) => setLines((current) => current.map((line, lineIndex) => lineIndex === index ? { ...line, unitPrice: Number(event.target.value) } : line))} type="number" value={item.unitPrice} /></td>
                      <td>{money(item.qty * item.unitPrice)}</td>
                      <td><button aria-label={`Xóa dòng ${index + 1}`} className="export-icon-button" disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))} type="button">×</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          {shortages.length > 0 && (
            <section className="export-shortage-card" aria-live="polite">
              <div>
                <h2>Thiếu tồn tại kho xuất</h2>
                <p>Phiếu vẫn được lưu ở trạng thái chờ xác nhận. Cần xử lý thiếu hàng trước khi xác nhận.</p>
              </div>
              <div className="export-table-scroll">
                <table className="export-table">
                  <thead><tr><th>Sản phẩm</th><th>Yêu cầu</th><th>Tồn kho xuất</th><th>Thiếu</th><th>Tồn ở kho khác</th></tr></thead>
                  <tbody>{shortages.map((item) => (
                    <tr key={item.productId}>
                      <td>{item.productName}</td><td>{item.requested}</td><td>{item.available}</td><td>{item.shortage}</td>
                      <td>{item.otherWarehouses.map((warehouse) => `${warehouse.warehouseName}: ${warehouse.available}`).join(' · ') || 'Không có'}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
              <div className="export-shortage-actions">
                <button className="export-button export-button-secondary" disabled title="Chưa kết nối API tách phiếu">Tách phiếu theo kho</button>
                <button className="export-button export-button-secondary" disabled title="Chưa có màn tạo phiếu chuyển kho">Tạo phiếu chuyển kho</button>
              </div>
            </section>
          )}

          <div className="export-form-bottom">
            <section className="export-card export-total-card" aria-label="Tổng tiền">
              <div><span>Tạm tính</span><strong>{money(subtotal)}</strong></div>
              <div><span>VAT ({vatRate === 'NONE' ? '0' : vatRate}%)</span><strong>{money(vatAmount)}</strong></div>
              <div className="export-grand-total"><span>Tổng tiền</span><strong>{money(total)}</strong></div>
            </section>
          </div>
          {error && <p className="export-form-error" role="alert">{error}</p>}
          <div className="export-form-actions">
            <Link className="export-button export-button-secondary" to="/exports">Hủy</Link>
            <button className="export-button export-button-secondary" name="action" type="submit" value="draft">{slip ? 'Lưu thay đổi' : 'Lưu nháp'}</button>
            <button className="export-button export-button-primary" name="action" type="submit" value="confirm">Xác nhận</button>
          </div>
          <p className="export-form-hint">Dữ liệu đang dùng bộ dữ liệu mẫu trong trình duyệt.</p>
        </form>
      </div>
    </AppShell>
  )
}

function ExportDetailPage({ slip }: { slip: ReturnType<typeof useExportStore.getState>['slips'][number] }) {
  const setStatus = useExportStore((state) => state.setStatus)
  const navigate = useNavigate()

  function confirmStatus(status: ExportStatus, prompt: string) {
    if (window.confirm(prompt)) setStatus(slip.id, status)
  }

  const timeline = [
    { label: 'Đã tạo phiếu', at: slip.createdAt, done: true },
    { label: 'Chờ xác nhận', at: slip.status === 'PENDING' ? slip.createdAt : slip.confirmedAt, done: ['PENDING', 'CONFIRMED', 'EXPORTED'].includes(slip.status) },
    { label: 'Đã xác nhận', at: slip.confirmedAt, done: ['CONFIRMED', 'EXPORTED'].includes(slip.status) },
    { label: 'Đã xuất', at: slip.exportedAt, done: slip.status === 'EXPORTED' },
    ...(slip.cancelledAt ? [{ label: 'Đã hủy', at: slip.cancelledAt, done: true }] : []),
  ]

  return (
    <AppShell activePath="/exports">
      <div className="export-page">
        <div className="export-detail-heading">
          <div>
            <p className="export-breadcrumb">Trang chủ / Nghiệp vụ / Phiếu xuất / Chi tiết</p>
            <div className="export-detail-title">
              <h1>{slip.code}</h1>
              <span className={`export-status status-${slip.status.toLowerCase()}`}>{statusNames[slip.status]}</span>
            </div>
          </div>
          <div className="export-detail-actions">
            <button className="export-button export-button-secondary" onClick={() => window.print()} type="button">In PDF</button>
            {slip.status === 'PENDING' && <button className="export-button export-button-secondary" onClick={() => navigate(`/exports/${slip.id}/edit`)} type="button">Sửa phiếu</button>}
            {slip.status === 'PENDING' && <button className="export-button export-button-danger" onClick={() => confirmStatus('CANCELLED', 'Bạn có chắc muốn hủy phiếu xuất này?')} type="button">Hủy phiếu</button>}
            {slip.status === 'PENDING' && <button className="export-button export-button-primary" disabled={!slip.stockReserved} onClick={() => confirmStatus('CONFIRMED', 'Xác nhận phiếu xuất?')} type="button">Xác nhận</button>}
            {slip.status === 'CONFIRMED' && <button className="export-button export-button-primary" onClick={() => confirmStatus('EXPORTED', 'Xác nhận đã xuất hàng khỏi kho?')} type="button">Xuất kho</button>}
          </div>
        </div>

        {!slip.stockReserved && slip.status === 'PENDING' && (
          <section className="export-shortage-card">
            <h2>Phiếu chưa giữ đủ hàng</h2>
            <p>Thiếu tồn tại kho xuất. Phiếu không thể xác nhận cho đến khi được xử lý.</p>
            <div className="export-table-scroll">
              <table className="export-table">
                <thead><tr><th>Sản phẩm</th><th>Yêu cầu</th><th>Tồn kho xuất</th><th>Thiếu</th><th>Tồn ở kho khác</th></tr></thead>
                <tbody>{slip.shortages.map((item) => (
                  <tr key={item.productId}>
                    <td>{item.productName}</td><td>{item.requested}</td><td>{item.available}</td><td>{item.shortage}</td>
                    <td>{item.otherWarehouses.map((warehouse) => `${warehouse.warehouseName}: ${warehouse.available}`).join(' · ') || 'Không có'}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
            <div className="export-shortage-actions">
              <button className="export-button export-button-secondary" disabled title="Chưa kết nối API tách phiếu">Tách phiếu theo kho</button>
              <button className="export-button export-button-secondary" disabled title="Chưa có màn tạo phiếu chuyển kho">Tạo phiếu chuyển kho</button>
            </div>
          </section>
        )}

        <div className="export-detail-grid">
          <div className="export-detail-main">
            <section className="export-card export-detail-card">
              <h2>Thông tin phiếu</h2>
              <dl className="export-info-grid">
                <div><dt>Kho xuất</dt><dd>{slip.warehouseCode} – {slip.warehouseName}</dd></div>
                <div><dt>Khách hàng</dt><dd>{slip.customerName || '—'}</dd></div>
                <div><dt>Loại xuất</dt><dd>{typeNames[slip.exportType]}</dd></div>
                <div><dt>Người bán</dt><dd>{slip.createdBy}</dd></div>
                <div><dt>Ngày tạo</dt><dd>{dateTime(slip.createdAt)}</dd></div>
                <div><dt>Hạn thanh toán</dt><dd>{slip.dueDate || '—'}</dd></div>
                <div><dt>Hình thức giao hàng</dt><dd>{slip.deliveryMethod === 'PICKUP' ? 'Nhận tại kho' : 'Đơn vị vận chuyển'}</dd></div>
                {slip.trackingNo && <div><dt>Mã vận đơn</dt><dd>{slip.trackingNo}</dd></div>}
              </dl>
            </section>

            <section className="export-card export-detail-card">
              <h2>Sản phẩm</h2>
              <div className="export-table-scroll">
                <table className="export-table">
                  <thead><tr><th>STT</th><th>Sản phẩm</th><th>Serial</th><th>SL</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead>
                  <tbody>{slip.lines.map((item, index) => (
                    <tr key={`${item.productId}-${index}`}>
                      <td>{index + 1}</td>
                      <td><strong>{item.productName}</strong><small>{item.productCode}</small></td>
                      <td>{item.serials.length ? item.serials.join(', ') : '—'}</td>
                      <td>{item.qty} {item.unit}</td>
                      <td>{money(item.unitPrice)}</td>
                      <td>{money(item.qty * item.unitPrice)}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            </section>

            <section className="export-card export-detail-card export-payment-card">
              <h2>Thanh toán</h2>
              <div><span>Tạm tính</span><strong>{money(slip.subtotal)}</strong></div>
              <div><span>VAT ({slip.vatRate === 'NONE' ? '0' : slip.vatRate}%)</span><strong>{money(slip.vatAmount)}</strong></div>
              <div><span>Tổng tiền</span><strong>{money(slip.totalAmount)}</strong></div>
              <div><span>Đã thanh toán</span><strong>{money(slip.paidAmount)}</strong></div>
              <div className="export-payment-balance"><span>Còn nợ</span><strong>{money(Math.max(0, slip.totalAmount - slip.paidAmount))}</strong></div>
            </section>
          </div>

          <aside className="export-detail-aside">
            <section className="export-card export-detail-card">
              <h2>Lịch sử trạng thái</h2>
              <ol className="export-timeline">
                {timeline.map((item, index) => (
                  <li className={item.done ? 'is-done' : ''} key={`${item.label}-${index}`}>
                    <span aria-hidden="true" className="export-timeline-dot" />
                    <div><strong>{item.label}</strong><time>{dateTime(item.at)}</time></div>
                  </li>
                ))}
              </ol>
            </section>
            <section className="export-card export-detail-card">
              <h2>Ghi chú</h2>
              <p>{slip.note || 'Không có ghi chú.'}</p>
            </section>
            <section className="export-card export-detail-card">
              <h2>Tóm tắt</h2>
              <dl className="export-summary">
                <div><dt>Thanh toán</dt><dd>{paymentNames[slip.paymentStatus]}</dd></div>
                <div><dt>Tiền cọc</dt><dd>{money(slip.depositAmount)}</dd></div>
                <div><dt>Giữ hàng</dt><dd>{slip.stockReserved ? 'Đã giữ' : 'Chưa giữ đủ'}</dd></div>
              </dl>
            </section>
          </aside>
        </div>
      </div>
    </AppShell>
  )
}

export default ExportPage
