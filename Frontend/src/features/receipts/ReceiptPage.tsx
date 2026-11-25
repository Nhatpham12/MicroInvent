import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AppShell } from '../../components/AppShell'
import {
  receiptPartners,
  receiptProducts,
  receiptWarehouses,
  useReceiptStore,
} from './receiptStore'
import type { Receipt, ReceiptInput, ReceiptLine, ReceiptSourceType } from './receiptStore'
import './ReceiptPage.css'

type ReceiptPageProps = {
  mode: 'list' | 'form' | 'detail'
}

const statusNames = {
  DRAFT: 'Nháp',
  CONFIRMED: 'Đã xác nhận',
  CANCELLED: 'Đã hủy',
} as const

const paymentNames = {
  UNPAID: 'Chưa thanh toán',
  PARTIAL: 'Thanh toán một phần',
  PAID: 'Đã thanh toán',
} as const

function currentLocalDateTime() {
  const now = new Date()
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset())
  return now.toISOString().slice(0, 16)
}

const money = (value: number) => `${value.toLocaleString('vi-VN')} đ`
const dateTime = (value: string | null) => {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })
}

function ReceiptPage({ mode }: ReceiptPageProps) {
  const { id } = useParams()
  const receipts = useReceiptStore((state) => state.receipts)
  const receipt = id ? receipts.find((item) => item.id === Number(id)) : undefined

  if (mode === 'list') return <ReceiptListPage />
  if (mode === 'form') {
    if (id && !receipt) {
      return (
        <AppShell activePath="/receipts">
          <div className="receipt-page">
            <div className="receipt-not-found">
              <h1>Không tìm thấy phiếu nhập</h1>
              <p>Không thể sửa phiếu nhập vì phiếu này không tồn tại.</p>
              <Link className="receipt-button receipt-button-primary" to="/receipts">Quay lại danh sách</Link>
            </div>
          </div>
        </AppShell>
      )
    }
    return <ReceiptFormPage key={receipt?.id ?? 'new'} receipt={receipt} />
  }
  if (!receipt) {
    return (
      <AppShell activePath="/receipts">
        <div className="receipt-page">
          <div className="receipt-not-found">
            <h1>Không tìm thấy phiếu nhập</h1>
            <p>Phiếu có thể không tồn tại hoặc dữ liệu mẫu đã được làm mới.</p>
            <Link className="receipt-button receipt-button-primary" to="/receipts">Quay lại danh sách</Link>
          </div>
        </div>
      </AppShell>
    )
  }
  return <ReceiptDetailPage receipt={receipt} />
}

function ReceiptListPage() {
  const receipts = useReceiptStore((state) => state.receipts)
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [warehouse, setWarehouse] = useState('')
  const [payment, setPayment] = useState('')
  const [tab, setTab] = useState<'all' | 'DRAFT' | 'CONFIRMED' | 'CANCELLED'>('all')

  const rows = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase('vi')
    return receipts.filter((receipt) => {
      const matchQuery = !needle ||
        [receipt.code, receipt.supplierName, receipt.customerName, receipt.contractNo]
          .some((value) => value.toLocaleLowerCase('vi').includes(needle))
      return matchQuery &&
        (!warehouse || String(receipt.warehouseId) === warehouse) &&
        (!payment || receipt.paymentStatus === payment) &&
        (tab === 'all' || receipt.status === tab)
    })
  }, [payment, query, receipts, tab, warehouse])

  return (
    <AppShell activePath="/receipts">
      <div className="receipt-page">
        <div className="receipt-title-row">
          <div>
            <p className="receipt-breadcrumb">Trang chủ / Nghiệp vụ / Phiếu nhập</p>
            <h1>Phiếu nhập</h1>
          </div>
          <button className="receipt-button receipt-button-primary" onClick={() => navigate('/receipts/new')} type="button">
            <span aria-hidden="true">＋</span> Tạo phiếu nhập
          </button>
        </div>

        <section className="receipt-filter-card" aria-label="Bộ lọc phiếu nhập">
          <label className="receipt-search">
            <span aria-hidden="true">⌕</span>
            <input
              aria-label="Tìm mã phiếu hoặc nhà cung cấp"
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Tìm mã phiếu, nhà cung cấp…"
              value={query}
            />
          </label>
          <select aria-label="Kho nhận" onChange={(event) => setWarehouse(event.target.value)} value={warehouse}>
            <option value="">Tất cả kho</option>
            {receiptWarehouses.map((item) => <option key={item.id} value={item.id}>{item.code} – {item.name}</option>)}
          </select>
          <select aria-label="Thanh toán" onChange={(event) => setPayment(event.target.value)} value={payment}>
            <option value="">Mọi trạng thái thanh toán</option>
            <option value="UNPAID">Chưa thanh toán</option>
            <option value="PARTIAL">Thanh toán một phần</option>
            <option value="PAID">Đã thanh toán</option>
          </select>
          <button className="receipt-button receipt-button-secondary" onClick={() => {
            setQuery('')
            setWarehouse('')
            setPayment('')
            setTab('all')
          }} type="button">Xóa lọc</button>
        </section>

        <section className="receipt-card">
          <div className="receipt-tabs" role="tablist" aria-label="Lọc trạng thái">
            {([
              ['all', 'Tất cả'],
              ['DRAFT', 'Nháp'],
              ['CONFIRMED', 'Đã xác nhận'],
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
          <div className="receipt-table-scroll">
            <table className="receipt-table">
              <thead>
                <tr>
                  <th>Mã phiếu</th><th>Nhà cung cấp</th><th>Kho nhận</th><th>Số SP</th>
                  <th>Tổng tiền</th><th>Thanh toán</th><th>Trạng thái</th><th>Ngày nhập</th><th />
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id}>
                    <td><Link className="receipt-code-link" to={`/receipts/${row.id}`}>{row.code}</Link></td>
                    <td>{row.supplierName || row.customerName || 'Khác'}</td>
                    <td>{row.warehouseCode}</td>
                    <td>{row.lines.reduce((sum, line) => sum + line.qty, 0)}</td>
                    <td>{money(row.totalAmount)}</td>
                    <td>{paymentNames[row.paymentStatus]}</td>
                    <td><span className={`receipt-status status-${row.status.toLowerCase()}`}>{statusNames[row.status]}</span></td>
                    <td>{dateTime(row.receiptDate)}</td>
                    <td><Link className="receipt-row-action" to={`/receipts/${row.id}`}>Chi tiết</Link></td>
                  </tr>
                ))}
                {rows.length === 0 && (
                  <tr><td className="receipt-empty-cell" colSpan={9}>Không có phiếu nhập phù hợp với bộ lọc.</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <footer className="receipt-table-footer">
            Hiển thị {rows.length ? 1 : 0}–{rows.length} trong {rows.length} phiếu
          </footer>
        </section>
      </div>
    </AppShell>
  )
}

function ReceiptFormPage({ receipt }: { receipt?: Receipt }) {
  const navigate = useNavigate()
  const create = useReceiptStore((state) => state.create)
  const update = useReceiptStore((state) => state.update)
  const setStatus = useReceiptStore((state) => state.setStatus)
  const [sourceType, setSourceType] = useState<ReceiptSourceType>(receipt?.sourceType ?? 'SUPPLIER')
  const [warehouseId, setWarehouseId] = useState(String(receipt?.warehouseId ?? ''))
  const [supplierId, setSupplierId] = useState(String(receipt?.supplierId ?? ''))
  const [customerId, setCustomerId] = useState(String(receipt?.customerId ?? ''))
  const [receiptDate, setReceiptDate] = useState(receipt?.receiptDate ?? currentLocalDateTime())
  const [contractNo, setContractNo] = useState(receipt?.contractNo ?? '')
  const [trackingNo, setTrackingNo] = useState(receipt?.trackingNo ?? '')
  const [bankPaymentRef, setBankPaymentRef] = useState(receipt?.bankPaymentRef ?? '')
  const [vatRate, setVatRate] = useState(receipt?.vatRate ?? 'NONE')
  const [note, setNote] = useState(receipt?.note ?? '')
  const [lines, setLines] = useState<ReceiptLine[]>(receipt?.lines ?? [createEmptyLine()])
  const [feedback, setFeedback] = useState('')
  const editable = !receipt || receipt.status !== 'CANCELLED'
  const subtotal = lines.reduce((sum, line) => sum + (Number(line.qty) || 0) * (Number(line.unitPrice) || 0), 0)
  const vatAmount = Math.round(subtotal * (vatRate === 'NONE' ? 0 : Number(vatRate))) / 100

  function updateLine(index: number, patch: Partial<ReceiptLine>) {
    setLines((current) => current.map((line, lineIndex) =>
      lineIndex === index ? { ...line, ...patch } : line,
    ))
  }

  function selectProduct(index: number, productId: string) {
    const product = receiptProducts.find((item) => item.id === Number(productId))
    if (!product) return
    updateLine(index, {
      productId: product.id,
      productName: product.name,
      productCode: product.code,
      unit: product.unit,
      unitPrice: product.defaultPrice,
      serials: [],
    })
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editable) return
    const submitter = event.nativeEvent instanceof SubmitEvent
      ? event.nativeEvent.submitter
      : null
    const confirmAfterSave = submitter instanceof HTMLButtonElement && submitter.value === 'confirm'
    if (!receiptDate || Number.isNaN(new Date(receiptDate).getTime())) {
      setFeedback('Vui lòng nhập ngày nhập hợp lệ.')
      return
    }
    if (!warehouseId) {
      setFeedback('Vui lòng chọn kho nhận.')
      return
    }
    if (sourceType === 'SUPPLIER' && !supplierId) {
      setFeedback('Vui lòng chọn nhà cung cấp.')
      return
    }
    if (sourceType === 'RETAIL_CUSTOMER' && !customerId) {
      setFeedback('Vui lòng chọn khách hàng.')
      return
    }
    if (
      lines.length === 0 ||
      lines.some((line) =>
        !line.productId ||
        !Number.isInteger(Number(line.qty)) ||
        line.qty < 1 ||
        !Number.isFinite(Number(line.unitPrice)) ||
        line.unitPrice < 0 ||
        !/^\d{1,16}(\.\d{1,2})?$/.test(String(line.unitPrice)),
      )
    ) {
      setFeedback('Vui lòng chọn sản phẩm và nhập số lượng, đơn giá hợp lệ cho từng dòng.')
      return
    }
    const serialSet = new Set<string>()
    for (const line of lines) {
      const product = receiptProducts.find((item) => item.id === line.productId)
      const serials = line.serials.map((serial) => serial.trim()).filter(Boolean)
      if (product?.hasSerial && serials.length !== line.qty) {
        setFeedback(`${line.productName}: cần nhập đủ ${line.qty} serial.`)
        return
      }
      if (!product?.hasSerial && serials.length > 0) {
        setFeedback(`${line.productName}: sản phẩm không quản lý serial.`)
        return
      }
      for (const serial of serials) {
        if (serialSet.has(serial.toLocaleLowerCase('vi'))) {
          setFeedback(`Serial ${serial} bị lặp trong phiếu.`)
          return
        }
        serialSet.add(serial.toLocaleLowerCase('vi'))
      }
    }

    const warehouse = receiptWarehouses.find((item) => item.id === Number(warehouseId))
    if (!warehouse) {
      setFeedback('Kho nhận không hợp lệ.')
      return
    }
    if (confirmAfterSave || receipt?.status === 'CONFIRMED') {
      const message = receipt?.status === 'CONFIRMED'
        ? 'Lưu thay đổi phiếu đã xác nhận? Tồn kho sẽ được điều chỉnh theo các dòng hàng mới.'
        : 'Xác nhận nhập kho? Hành động này sẽ cập nhật tồn kho.'
      if (!window.confirm(message)) return
    }
    const supplier = receiptPartners.suppliers.find((item) => item.id === Number(supplierId))
    const customer = receiptPartners.customers.find((item) => item.id === Number(customerId))
    const input: ReceiptInput = {
      warehouseId: warehouse.id,
      warehouseCode: warehouse.code,
      warehouseName: warehouse.name,
      sourceType,
      supplierId: sourceType === 'SUPPLIER' ? supplier?.id ?? null : null,
      supplierName: sourceType === 'SUPPLIER' ? supplier?.name ?? '' : '',
      customerId: sourceType === 'RETAIL_CUSTOMER' ? customer?.id ?? null : null,
      customerName: sourceType === 'RETAIL_CUSTOMER' ? customer?.name ?? '' : '',
      receiptDate,
      contractNo: contractNo.trim(),
      trackingNo: trackingNo.trim(),
      bankPaymentRef: bankPaymentRef.trim(),
      vatRate,
      note: note.trim(),
      lines: lines.map((line) => ({
        ...line,
        qty: Number(line.qty),
        unitPrice: Number(line.unitPrice),
        serials: line.serials.map((serial) => serial.trim()).filter(Boolean),
      })),
    }

    let savedReceipt: Receipt
    if (receipt) {
      update(receipt.id, input)
      savedReceipt = { ...receipt, ...input, subtotal, vatAmount, totalAmount: subtotal + vatAmount }
    } else {
      savedReceipt = create(input)
    }
    if (confirmAfterSave) setStatus(savedReceipt.id, 'CONFIRMED')
    navigate(`/receipts/${savedReceipt.id}`)
  }

  if (!editable && receipt) {
    return <ReceiptDetailPage receipt={receipt} />
  }

  return (
    <AppShell activePath="/receipts">
      <div className="receipt-page">
        <div className="receipt-title-row">
          <div>
            <p className="receipt-breadcrumb">
              Trang chủ / Nghiệp vụ / <Link to="/receipts">Phiếu nhập</Link> / {receipt ? receipt.code : 'Tạo mới'}
            </p>
            <h1>{receipt ? `Sửa phiếu nhập ${receipt.code}` : 'Tạo phiếu nhập'}</h1>
          </div>
          <Link className="receipt-button receipt-button-secondary" to="/receipts">Quay lại danh sách</Link>
        </div>

        <form className="receipt-form" onSubmit={save}>
          {receipt?.status === 'CONFIRMED' && (
            <div className="receipt-edit-warning" role="note">
              Sửa phiếu đã xác nhận sẽ điều chỉnh lại tồn kho. Bạn sẽ được hỏi xác nhận trước khi lưu.
            </div>
          )}
          <section className="receipt-form-card">
            <h2>Thông tin chung</h2>
            <div className="receipt-form-grid">
              <label className="receipt-field">
                <span>Kho nhận <b>*</b></span>
                <select disabled={!editable} onChange={(event) => setWarehouseId(event.target.value)} value={warehouseId}>
                  <option value="">Chọn kho nhận</option>
                  {receiptWarehouses.map((item) => <option key={item.id} value={item.id}>{item.code} – {item.name}</option>)}
                </select>
              </label>
              <label className="receipt-field">
                <span>Nguồn nhập <b>*</b></span>
                <select disabled={!editable} onChange={(event) => setSourceType(event.target.value as ReceiptSourceType)} value={sourceType}>
                  <option value="SUPPLIER">Nhà cung cấp</option>
                  <option value="RETAIL_CUSTOMER">Mua lại từ khách lẻ</option>
                  <option value="OTHER">Khác</option>
                </select>
              </label>
              {sourceType === 'SUPPLIER' && (
                <label className="receipt-field">
                  <span>Nhà cung cấp <b>*</b></span>
                  <select disabled={!editable} onChange={(event) => setSupplierId(event.target.value)} value={supplierId}>
                    <option value="">Chọn nhà cung cấp</option>
                    {receiptPartners.suppliers.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
                  </select>
                </label>
              )}
              {sourceType === 'RETAIL_CUSTOMER' && (
                <label className="receipt-field">
                  <span>Khách hàng <b>*</b></span>
                  <select disabled={!editable} onChange={(event) => setCustomerId(event.target.value)} value={customerId}>
                    <option value="">Chọn khách hàng</option>
                    {receiptPartners.customers.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
                  </select>
                </label>
              )}
              <label className="receipt-field">
                <span>Ngày nhập</span>
                <input disabled={!editable} onChange={(event) => setReceiptDate(event.target.value)} required type="datetime-local" value={receiptDate} />
              </label>
              <label className="receipt-field">
                <span>Số hợp đồng</span>
                <input disabled={!editable} maxLength={100} onChange={(event) => setContractNo(event.target.value)} placeholder="Nhập số hợp đồng" value={contractNo} />
              </label>
              <label className="receipt-field">
                <span>Mã vận đơn</span>
                <input disabled={!editable} maxLength={100} onChange={(event) => setTrackingNo(event.target.value)} placeholder="Nhập mã vận đơn" value={trackingNo} />
              </label>
              <label className="receipt-field">
                <span>Thuế VAT</span>
                <select disabled={!editable} onChange={(event) => setVatRate(event.target.value)} value={vatRate}>
                  <option value="NONE">Không áp dụng</option>
                  <option value="0">0%</option>
                  <option value="5">5%</option>
                  <option value="8">8%</option>
                  <option value="10">10%</option>
                </select>
              </label>
              <label className="receipt-field">
                <span>Mã tham chiếu thanh toán</span>
                <input disabled={!editable} maxLength={100} onChange={(event) => setBankPaymentRef(event.target.value)} placeholder="Nhập mã tham chiếu" value={bankPaymentRef} />
              </label>
            </div>
          </section>

          <section className="receipt-form-card">
            <div className="receipt-section-heading">
              <h2>Sản phẩm nhập</h2>
              <button className="receipt-button receipt-button-secondary" disabled={!editable} onClick={() => setLines((current) => [...current, createEmptyLine()])} type="button">
                ＋ Thêm dòng
              </button>
            </div>
            <div className="receipt-lines">
              {lines.map((line, index) => {
                const product = receiptProducts.find((item) => item.id === line.productId)
                return (
                  <div className="receipt-line" key={`${index}-${line.productId}`}>
                    <span className="receipt-line-number">{index + 1}</span>
                    <label className="receipt-field">
                      <span>Sản phẩm <b>*</b></span>
                      <select disabled={!editable} onChange={(event) => selectProduct(index, event.target.value)} value={line.productId || ''}>
                        <option value="">Chọn sản phẩm</option>
                        {receiptProducts.map((item) => <option key={item.id} value={item.id}>{item.code} – {item.name}</option>)}
                      </select>
                    </label>
                    <label className="receipt-field receipt-qty-field">
                      <span>SL <b>*</b></span>
                      <input disabled={!editable} min="1" step="1" onChange={(event) => updateLine(index, { qty: Number(event.target.value) })} type="number" value={line.qty || ''} />
                    </label>
                    <label className="receipt-field">
                      <span>Đơn giá <b>*</b></span>
                      <input disabled={!editable} min="0" onChange={(event) => updateLine(index, { unitPrice: Number(event.target.value) })} step="0.01" type="number" value={line.unitPrice || ''} />
                    </label>
                    <label className="receipt-field">
                      <span>Thành tiền</span>
                      <output>{money((Number(line.qty) || 0) * (Number(line.unitPrice) || 0))}</output>
                    </label>
                    <label className="receipt-field receipt-serial-field">
                      <span>Số serial{product?.hasSerial ? ' *' : ''}</span>
                      <textarea
                        disabled={!editable || !product?.hasSerial}
                        onChange={(event) => updateLine(index, { serials: event.target.value.split(/[\n,]+/) })}
                        placeholder={product?.hasSerial ? 'Mỗi serial một dòng hoặc cách nhau bằng dấu phẩy' : 'Sản phẩm không quản lý serial'}
                        value={line.serials.join('\n')}
                      />
                    </label>
                    <label className="receipt-field">
                      <span>Ghi chú tình trạng</span>
                      <input disabled={!editable} maxLength={255} onChange={(event) => updateLine(index, { conditionNote: event.target.value })} placeholder="Tình trạng sản phẩm" value={line.conditionNote} />
                    </label>
                    <button
                      aria-label={`Xóa dòng ${index + 1}`}
                      className="receipt-remove-line"
                      disabled={!editable || lines.length === 1}
                      onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))}
                      type="button"
                    >×</button>
                  </div>
                )
              })}
            </div>
            <div className="receipt-totals">
              <div><span>Tạm tính</span><strong>{money(subtotal)}</strong></div>
              <div><span>VAT ({vatRate === 'NONE' ? '0' : vatRate}%)</span><strong>{money(vatAmount)}</strong></div>
              <div className="receipt-total"><span>Tổng tiền</span><strong>{money(subtotal + vatAmount)}</strong></div>
            </div>
          </section>

          <section className="receipt-form-card">
            <label className="receipt-field">
              <span>Ghi chú</span>
              <textarea disabled={!editable} maxLength={2000} onChange={(event) => setNote(event.target.value)} placeholder="Nhập ghi chú…" rows={3} value={note} />
            </label>
          </section>

          {feedback && <div className="receipt-feedback" role="alert">{feedback}</div>}
          <div className="receipt-form-actions">
            <Link className="receipt-button receipt-button-secondary" to="/receipts">Hủy</Link>
            <button className="receipt-button receipt-button-secondary" type="submit" value="draft">{receipt?.status === 'CONFIRMED' ? 'Lưu thay đổi' : 'Lưu nháp'}</button>
            <button className="receipt-button receipt-button-primary" type="submit" value="confirm">{receipt?.status === 'CONFIRMED' ? 'Lưu & xác nhận' : 'Xác nhận nhập'}</button>
          </div>
        </form>
      </div>
    </AppShell>
  )
}

function createEmptyLine(): ReceiptLine {
  return {
    productId: 0,
    productName: '',
    productCode: '',
    unit: '',
    qty: 1,
    unitPrice: 0,
    serials: [],
    conditionNote: '',
  }
}

function ReceiptDetailPage({ receipt }: { receipt: Receipt }) {
  const setStatus = useReceiptStore((state) => state.setStatus)
  const navigate = useNavigate()
  const confirmed = receipt.status === 'CONFIRMED'
  const cancelled = receipt.status === 'CANCELLED'
  return (
    <AppShell activePath="/receipts">
      <div className="receipt-page">
        <div className="receipt-title-row">
          <div>
            <p className="receipt-breadcrumb">Trang chủ / Nghiệp vụ / <Link to="/receipts">Phiếu nhập</Link> / Chi tiết</p>
            <h1>{receipt.code}</h1>
          </div>
          <div className="receipt-detail-actions">
            <Link className="receipt-button receipt-button-secondary" to="/receipts">Danh sách</Link>
            {!cancelled && (
              <button className="receipt-button receipt-button-danger" onClick={() => {
                const message = confirmed
                  ? 'Hủy phiếu nhập đã xác nhận? Tồn kho sẽ được hoàn tác.'
                  : 'Hủy phiếu nhập nháp này?'
                if (window.confirm(message)) {
                  setStatus(receipt.id, 'CANCELLED')
                }
              }} type="button">Hủy phiếu</button>
            )}
            {!cancelled && (
              <>
                <button className="receipt-button receipt-button-secondary" onClick={() => navigate(`/receipts/${receipt.id}/edit`)} type="button">Sửa</button>
                {!confirmed && <button className="receipt-button receipt-button-primary" onClick={() => {
                  if (window.confirm('Xác nhận nhập kho? Hành động này sẽ cập nhật tồn kho.')) {
                    setStatus(receipt.id, 'CONFIRMED')
                  }
                }} type="button">Xác nhận nhập</button>}
              </>
            )}
          </div>
        </div>

        <section className="receipt-detail-grid">
          <div className="receipt-detail-main">
            <section className="receipt-form-card">
              <div className="receipt-section-heading">
                <h2>Thông tin phiếu</h2>
                <span className={`receipt-status status-${receipt.status.toLowerCase()}`}>{statusNames[receipt.status]}</span>
              </div>
              <dl className="receipt-info-grid">
                <InfoItem label="Kho nhận" value={`${receipt.warehouseCode} – ${receipt.warehouseName}`} />
                <InfoItem label={receipt.sourceType === 'SUPPLIER' ? 'Nhà cung cấp' : 'Đối tượng nhập'} value={receipt.supplierName || receipt.customerName || 'Khác'} />
                <InfoItem label="Ngày nhập" value={dateTime(receipt.receiptDate)} />
                <InfoItem label="Số hợp đồng" value={receipt.contractNo || '—'} />
                <InfoItem label="Mã vận đơn" value={receipt.trackingNo || '—'} />
                <InfoItem label="Thuế VAT" value={receipt.vatRate === 'NONE' ? 'Không áp dụng' : `${receipt.vatRate}%`} />
              </dl>
            </section>
            <section className="receipt-form-card">
              <h2>Sản phẩm nhập</h2>
              <div className="receipt-table-scroll">
                <table className="receipt-table receipt-detail-lines">
                  <thead><tr><th>STT</th><th>Sản phẩm</th><th>Số serial</th><th>SL</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead>
                  <tbody>
                    {receipt.lines.map((line, index) => (
                      <tr key={`${line.productId}-${index}`}>
                        <td>{index + 1}</td>
                        <td><strong>{line.productName}</strong><small>{line.productCode} · {line.unit}</small></td>
                        <td>{line.serials.length ? line.serials.join(', ') : '—'}</td>
                        <td>{line.qty}</td>
                        <td>{money(line.unitPrice)}</td>
                        <td>{money(line.qty * line.unitPrice)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
            <section className="receipt-form-card">
              <h2>Thanh toán</h2>
              <div className="receipt-totals">
                <div><span>Tạm tính</span><strong>{money(receipt.subtotal)}</strong></div>
                <div><span>VAT</span><strong>{money(receipt.vatAmount)}</strong></div>
                <div className="receipt-total"><span>Tổng tiền</span><strong>{money(receipt.totalAmount)}</strong></div>
                <div><span>Đã thanh toán</span><strong>{money(receipt.paidAmount)}</strong></div>
                <div><span>Trạng thái</span><strong>{paymentNames[receipt.paymentStatus]}</strong></div>
              </div>
            </section>
          </div>
          <aside className="receipt-detail-side">
            <section className="receipt-form-card">
              <h2>Lịch sử trạng thái</h2>
              <ol className="receipt-timeline">
                <li className={receipt.status === 'DRAFT' ? 'is-current' : 'is-done'}>
                  <strong>Đã tạo phiếu</strong>
                  <span>{dateTime(receipt.createdAt)}</span>
                </li>
                {confirmed && (
                  <li className="is-current">
                    <strong>Đã xác nhận</strong>
                    <span>{dateTime(receipt.confirmedAt)}</span>
                  </li>
                )}
                {cancelled && (
                  <li className="is-current">
                    <strong>Đã hủy</strong>
                    <span>{dateTime(receipt.cancelledAt)}</span>
                  </li>
                )}
              </ol>
            </section>
            <section className="receipt-form-card">
              <h2>Ghi chú</h2>
              <p className="receipt-note">{receipt.note || 'Chưa có ghi chú.'}</p>
            </section>
          </aside>
        </section>
      </div>
    </AppShell>
  )
}

function InfoItem({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>
}

export default ReceiptPage
