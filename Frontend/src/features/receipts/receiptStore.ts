import { create } from 'zustand'
import { catalogConfigs } from '../catalog/catalogData'

export type ReceiptStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED'
export type ReceiptSourceType = 'SUPPLIER' | 'RETAIL_CUSTOMER' | 'OTHER'
export type ReceiptPaymentStatus = 'UNPAID' | 'PARTIAL' | 'PAID'

export type ReceiptLine = {
  productId: number
  productName: string
  productCode: string
  unit: string
  qty: number
  unitPrice: number
  serials: string[]
  conditionNote: string
}

export type Receipt = {
  id: number
  code: string
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  sourceType: ReceiptSourceType
  supplierId: number | null
  supplierName: string
  customerId: number | null
  customerName: string
  receiptDate: string
  status: ReceiptStatus
  contractNo: string
  trackingNo: string
  bankPaymentRef: string
  vatRate: string
  subtotal: number
  vatAmount: number
  totalAmount: number
  paidAmount: number
  paymentStatus: ReceiptPaymentStatus
  note: string
  lines: ReceiptLine[]
  createdAt: string
  confirmedAt: string | null
  cancelledAt: string | null
}

export type ReceiptInput = Omit<
  Receipt,
  | 'id'
  | 'code'
  | 'status'
  | 'subtotal'
  | 'vatAmount'
  | 'totalAmount'
  | 'paidAmount'
  | 'paymentStatus'
  | 'createdAt'
  | 'confirmedAt'
  | 'cancelledAt'
> & {
  status?: ReceiptStatus
}

const products = catalogConfigs.products.seed
const suppliers = catalogConfigs.suppliers.seed

const initialReceipts: Receipt[] = [
  {
    id: 1,
    code: 'PNK-2026-0001',
    warehouseId: 1,
    warehouseCode: 'HN01',
    warehouseName: 'Kho Hà Nội',
    sourceType: 'SUPPLIER',
    supplierId: 1,
    supplierName: 'Công ty An Phát',
    customerId: null,
    customerName: '',
    receiptDate: '2026-11-18T09:10',
    status: 'DRAFT',
    contractNo: 'HD-2026-015',
    trackingNo: 'VD-884201',
    bankPaymentRef: '',
    vatRate: '10',
    subtotal: 4_500_000,
    vatAmount: 450_000,
    totalAmount: 4_950_000,
    paidAmount: 0,
    paymentStatus: 'UNPAID',
    note: 'Kiểm tra tình trạng và serial khi nhập kho.',
    createdAt: '2026-11-18T09:05:00',
    confirmedAt: null,
    cancelledAt: null,
    lines: [
      {
        productId: products[0].id,
        productName: products[0].name,
        productCode: products[0].code,
        unit: String(products[0].unit),
        qty: 3,
        unitPrice: 1_500_000,
        serials: [],
        conditionNote: '',
      },
    ],
  },
  {
    id: 2,
    code: 'PNK-2026-0002',
    warehouseId: 2,
    warehouseCode: 'HCM01',
    warehouseName: 'Kho Hồ Chí Minh',
    sourceType: 'SUPPLIER',
    supplierId: 2,
    supplierName: 'Cửa hàng Minh Khoa',
    customerId: null,
    customerName: '',
    receiptDate: '2026-11-17T14:30',
    status: 'CONFIRMED',
    contractNo: 'HD-2026-018',
    trackingNo: '',
    bankPaymentRef: 'UNC-118',
    vatRate: '8',
    subtotal: 2_400_000,
    vatAmount: 192_000,
    totalAmount: 2_592_000,
    paidAmount: 1_000_000,
    paymentStatus: 'PARTIAL',
    note: '',
    createdAt: '2026-11-17T14:10:00',
    confirmedAt: '2026-11-17T14:30:00',
    cancelledAt: null,
    lines: [
      {
        productId: products[1].id,
        productName: products[1].name,
        productCode: products[1].code,
        unit: String(products[1].unit),
        qty: 4,
        unitPrice: 600_000,
        serials: [],
        conditionNote: '',
      },
    ],
  },
]

function calculateTotals(lines: ReceiptLine[], vatRate: string) {
  const subtotal = lines.reduce((sum, line) => sum + line.qty * line.unitPrice, 0)
  const rate = vatRate === 'NONE' ? 0 : Number(vatRate)
  const vatAmount = Math.round(subtotal * rate) / 100
  return { subtotal, vatAmount, totalAmount: subtotal + vatAmount }
}

type ReceiptStore = {
  receipts: Receipt[]
  create: (input: ReceiptInput) => Receipt
  update: (id: number, input: ReceiptInput) => void
  setStatus: (id: number, status: ReceiptStatus) => void
}

export const useReceiptStore = create<ReceiptStore>((set, get) => ({
  receipts: initialReceipts,
  create: (input) => {
    const id = Math.max(0, ...get().receipts.map((receipt) => receipt.id)) + 1
    const totals = calculateTotals(input.lines, input.vatRate)
    const receipt: Receipt = {
      ...input,
      ...totals,
      id,
      code: `PNK-2026-${String(id).padStart(4, '0')}`,
      status: input.status ?? 'DRAFT',
      paidAmount: 0,
      paymentStatus: 'UNPAID',
      createdAt: new Date().toISOString(),
      confirmedAt: input.status === 'CONFIRMED' ? new Date().toISOString() : null,
      cancelledAt: null,
    }
    set((state) => ({ receipts: [receipt, ...state.receipts] }))
    return receipt
  },
  update: (id, input) => {
    const totals = calculateTotals(input.lines, input.vatRate)
    set((state) => ({
      receipts: state.receipts.map((receipt) =>
        receipt.id === id && receipt.status !== 'CANCELLED'
          ? { ...receipt, ...input, ...totals }
          : receipt,
      ),
    }))
  },
  setStatus: (id, status) => {
    set((state) => ({
      receipts: state.receipts.map((receipt) =>
        receipt.id === id
          ? {
              ...receipt,
              status,
              confirmedAt: status === 'CONFIRMED' ? new Date().toISOString() : receipt.confirmedAt,
              cancelledAt: status === 'CANCELLED' ? new Date().toISOString() : receipt.cancelledAt,
            }
          : receipt,
      ),
    }))
  },
}))

export const receiptWarehouses = [
  { id: 1, code: 'HN01', name: 'Kho Hà Nội' },
  { id: 2, code: 'HCM01', name: 'Kho Hồ Chí Minh' },
  { id: 3, code: 'DN01', name: 'Kho Đà Nẵng' },
]

export const receiptPartners = {
  suppliers: suppliers.map((supplier) => ({
    id: supplier.id,
    name: supplier.name,
  })),
  customers: catalogConfigs.customers.seed.map((customer) => ({
    id: customer.id,
    name: customer.name,
  })),
}

export const receiptProducts = products.map((product) => ({
  id: product.id,
  code: product.code,
  name: product.name,
  unit: String(product.unit),
  hasSerial: Boolean(product.hasSerial),
  defaultPrice: Number(product.defaultPriceIn),
}))
