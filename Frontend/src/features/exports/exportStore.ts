import { create } from 'zustand'
import { catalogConfigs } from '../catalog/catalogData'
import { receiptWarehouses } from '../receipts/receiptStore'

export type ExportStatus = 'PENDING' | 'CONFIRMED' | 'EXPORTED' | 'CANCELLED'
export type ExportType = 'SALE' | 'DAMAGED' | 'WARRANTY' | 'RETURN_SUPPLIER' | 'OTHER'
export type PaymentStatus = 'UNPAID' | 'PARTIAL' | 'PAID'
export type DeliveryMethod = 'PICKUP' | 'CARRIER'

export type ExportLine = {
  productId: number
  productCode: string
  productName: string
  unit: string
  hasSerial: boolean
  qty: number
  unitPrice: number
  serials: string[]
  available: number
}

export type ExportShortage = {
  productId: number
  productName: string
  requested: number
  available: number
  shortage: number
  otherWarehouses: { warehouseId: number; warehouseName: string; available: number }[]
}

export type ExportSlip = {
  id: number
  code: string
  exportType: ExportType
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  customerId: number | null
  customerName: string
  status: ExportStatus
  stockReserved: boolean
  deliveryMethod: DeliveryMethod
  trackingNo: string
  vatRate: string
  subtotal: number
  vatAmount: number
  totalAmount: number
  depositAmount: number
  paidAmount: number
  paymentStatus: PaymentStatus
  dueDate: string
  salesUserId: number | null
  splitFromId: number | null
  note: string
  createdBy: string
  confirmedBy: string
  exportedBy: string
  createdAt: string
  confirmedAt: string | null
  exportedAt: string | null
  cancelledAt: string | null
  lines: ExportLine[]
  shortages: ExportShortage[]
}

export type ExportInput = Omit<
  ExportSlip,
  | 'id'
  | 'code'
  | 'status'
  | 'stockReserved'
  | 'subtotal'
  | 'vatAmount'
  | 'totalAmount'
  | 'paidAmount'
  | 'paymentStatus'
  | 'createdBy'
  | 'confirmedBy'
  | 'exportedBy'
  | 'createdAt'
  | 'confirmedAt'
  | 'exportedAt'
  | 'cancelledAt'
  | 'shortages'
> & {
  shortages: ExportShortage[]
}

const products = catalogConfigs.products.seed
const customers = catalogConfigs.customers.seed

function line(productIndex: number, qty: number, unitPrice: number, available: number, serials: string[] = []): ExportLine {
  const product = products[productIndex]
  return {
    productId: product.id,
    productCode: product.code,
    productName: product.name,
    unit: String(product.unit),
    hasSerial: Boolean(product.hasSerial),
    qty,
    unitPrice,
    serials,
    available,
  }
}

const sampleLines: { cable: ExportLine; mouse: ExportLine; keyboard: ExportLine } = {
  cable: line(0, 3, 240_000, 18),
  mouse: line(1, 2, 335_000, 10, ['SN-MOUSE-001', 'SN-MOUSE-002']),
  keyboard: line(2, 1, 525_000, 8, ['SN-KEY-009']),
}

const timestamp = (day: number, hour: number) => `2026-11-${String(day).padStart(2, '0')}T${String(hour).padStart(2, '0')}:15:00`

const initialExports: ExportSlip[] = [
  {
    id: 1,
    code: 'PXK-2026-0001',
    exportType: 'SALE',
    warehouseId: 1,
    warehouseCode: 'HN01',
    warehouseName: 'Kho Hà Nội',
    customerId: customers[0].id,
    customerName: String(customers[0].name),
    status: 'PENDING',
    stockReserved: true,
    deliveryMethod: 'PICKUP',
    trackingNo: '',
    vatRate: '10',
    subtotal: 1_245_000,
    vatAmount: 124_500,
    totalAmount: 1_369_500,
    depositAmount: 0,
    paidAmount: 0,
    paymentStatus: 'UNPAID',
    dueDate: '2026-12-03',
    salesUserId: 1,
    splitFromId: null,
    note: 'Giao hàng trong giờ hành chính.',
    createdBy: 'Nguyễn Văn An',
    confirmedBy: '',
    exportedBy: '',
    createdAt: timestamp(23, 9),
    confirmedAt: null,
    exportedAt: null,
    cancelledAt: null,
    lines: [sampleLines.cable, sampleLines.mouse],
    shortages: [],
  },
  {
    id: 2,
    code: 'PXK-2026-0002',
    exportType: 'SALE',
    warehouseId: 1,
    warehouseCode: 'HN01',
    warehouseName: 'Kho Hà Nội',
    customerId: customers[1].id,
    customerName: String(customers[1].name),
    status: 'PENDING',
    stockReserved: false,
    deliveryMethod: 'CARRIER',
    trackingNo: 'GHN-20261122',
    vatRate: '8',
    subtotal: 1_320_000,
    vatAmount: 105_600,
    totalAmount: 1_425_600,
    depositAmount: 0,
    paidAmount: 500_000,
    paymentStatus: 'PARTIAL',
    dueDate: '2026-12-10',
    salesUserId: 2,
    splitFromId: null,
    note: 'Thiếu hàng tại kho xuất, cần tách theo kho.',
    createdBy: 'Lê Quốc Cường',
    confirmedBy: '',
    exportedBy: '',
    createdAt: timestamp(22, 14),
    confirmedAt: null,
    exportedAt: null,
    cancelledAt: null,
    lines: [{ ...sampleLines.mouse, available: 1 }, sampleLines.keyboard],
    shortages: [
      {
        productId: sampleLines.mouse.productId,
        productName: sampleLines.mouse.productName,
        requested: 2,
        available: 1,
        shortage: 1,
        otherWarehouses: [
          { warehouseId: 2, warehouseName: 'Kho Hồ Chí Minh', available: 10 },
          { warehouseId: 3, warehouseName: 'Kho Đà Nẵng', available: 5 },
        ],
      },
    ],
  },
  {
    id: 3,
    code: 'PXK-2026-0003',
    exportType: 'WARRANTY',
    warehouseId: 2,
    warehouseCode: 'HCM01',
    warehouseName: 'Kho Hồ Chí Minh',
    customerId: customers[2].id,
    customerName: String(customers[2].name),
    status: 'CONFIRMED',
    stockReserved: true,
    deliveryMethod: 'CARRIER',
    trackingNo: 'VTP-20261120',
    vatRate: 'NONE',
    subtotal: 2_400_000,
    vatAmount: 0,
    totalAmount: 2_400_000,
    depositAmount: 0,
    paidAmount: 2_400_000,
    paymentStatus: 'PAID',
    dueDate: '',
    salesUserId: 1,
    splitFromId: null,
    note: '',
    createdBy: 'Nguyễn Văn An',
    confirmedBy: 'Trần Thị Bình',
    exportedBy: '',
    createdAt: timestamp(20, 10),
    confirmedAt: timestamp(20, 11),
    exportedAt: null,
    cancelledAt: null,
    lines: [sampleLines.keyboard],
    shortages: [],
  },
  {
    id: 4,
    code: 'PXK-2026-0004',
    exportType: 'SALE',
    warehouseId: 2,
    warehouseCode: 'HCM01',
    warehouseName: 'Kho Hồ Chí Minh',
    customerId: customers[3].id,
    customerName: String(customers[3].name),
    status: 'EXPORTED',
    stockReserved: false,
    deliveryMethod: 'PICKUP',
    trackingNo: '',
    vatRate: '10',
    subtotal: 720_000,
    vatAmount: 72_000,
    totalAmount: 792_000,
    depositAmount: 100_000,
    paidAmount: 792_000,
    paymentStatus: 'PAID',
    dueDate: '',
    salesUserId: 2,
    splitFromId: null,
    note: '',
    createdBy: 'Đỗ Thanh Mai',
    confirmedBy: 'Trần Thị Bình',
    exportedBy: 'Lê Quốc Cường',
    createdAt: timestamp(18, 8),
    confirmedAt: timestamp(18, 9),
    exportedAt: timestamp(18, 10),
    cancelledAt: null,
    lines: [sampleLines.cable],
    shortages: [],
  },
  {
    id: 5,
    code: 'PXK-2026-0005',
    exportType: 'DAMAGED',
    warehouseId: 1,
    warehouseCode: 'HN01',
    warehouseName: 'Kho Hà Nội',
    customerId: null,
    customerName: '',
    status: 'CANCELLED',
    stockReserved: false,
    deliveryMethod: 'PICKUP',
    trackingNo: '',
    vatRate: 'NONE',
    subtotal: 525_000,
    vatAmount: 0,
    totalAmount: 525_000,
    depositAmount: 0,
    paidAmount: 0,
    paymentStatus: 'UNPAID',
    dueDate: '',
    salesUserId: null,
    splitFromId: null,
    note: 'Hủy do lập nhầm phiếu.',
    createdBy: 'Lê Quốc Cường',
    confirmedBy: '',
    exportedBy: '',
    createdAt: timestamp(17, 15),
    confirmedAt: null,
    exportedAt: null,
    cancelledAt: timestamp(17, 16),
    lines: [sampleLines.keyboard],
    shortages: [],
  },
]

function calculateTotals(lines: ExportLine[], vatRate: string) {
  const subtotal = lines.reduce((sum, item) => sum + item.qty * item.unitPrice, 0)
  const rate = vatRate === 'NONE' ? 0 : Number(vatRate)
  const vatAmount = Math.round(subtotal * rate) / 100
  return { subtotal, vatAmount, totalAmount: subtotal + vatAmount }
}

type ExportStore = {
  slips: ExportSlip[]
  create: (input: ExportInput) => ExportSlip
  update: (id: number, input: ExportInput) => void
  setStatus: (id: number, status: ExportStatus) => void
}

export const useExportStore = create<ExportStore>((set, get) => ({
  slips: initialExports,
  create: (input) => {
    const id = Math.max(0, ...get().slips.map((slip) => slip.id)) + 1
    const totals = calculateTotals(input.lines, input.vatRate)
    const stockReserved = input.shortages.length === 0
    const slip: ExportSlip = {
      ...input,
      ...totals,
      id,
      code: `PXK-2026-${String(id).padStart(4, '0')}`,
      status: 'PENDING',
      stockReserved,
      paidAmount: 0,
      paymentStatus: 'UNPAID',
      createdBy: 'Nguyễn An',
      confirmedBy: '',
      exportedBy: '',
      createdAt: new Date().toISOString(),
      confirmedAt: null,
      exportedAt: null,
      cancelledAt: null,
    }
    set((state) => ({ slips: [slip, ...state.slips] }))
    return slip
  },
  update: (id, input) => {
    const totals = calculateTotals(input.lines, input.vatRate)
    set((state) => ({
      slips: state.slips.map((slip) =>
        slip.id === id && slip.status !== 'EXPORTED' && slip.status !== 'CANCELLED'
          ? {
              ...slip,
              ...input,
              ...totals,
              status: 'PENDING',
              stockReserved: input.shortages.length === 0,
            }
          : slip,
      ),
    }))
  },
  setStatus: (id, status) => {
    const now = new Date().toISOString()
    set((state) => ({
      slips: state.slips.map((slip) =>
        slip.id === id
          ? {
              ...slip,
              status,
              confirmedAt: status === 'CONFIRMED' ? now : slip.confirmedAt,
              exportedAt: status === 'EXPORTED' ? now : slip.exportedAt,
              cancelledAt: status === 'CANCELLED' ? now : slip.cancelledAt,
              stockReserved: status === 'EXPORTED' || status === 'CANCELLED' ? false : slip.stockReserved,
            }
          : slip,
      ),
    }))
  },
}))

export const exportWarehouses = receiptWarehouses

export const exportPartners = customers.map((customer) => ({
  id: customer.id,
  name: String(customer.name),
}))

export const exportProducts: {
  id: number
  code: string
  name: string
  unit: string
  hasSerial: boolean
  defaultPrice: number
  availableByWarehouse: Record<number, number>
}[] = products.map((product) => ({
  id: product.id,
  code: product.code,
  name: product.name,
  unit: String(product.unit),
  hasSerial: Boolean(product.hasSerial),
  defaultPrice: Number(product.defaultPriceOut),
  availableByWarehouse: {
    1: Number(product.stockAvailable),
    2: Number(product.stockAvailable) + 4,
    3: Math.max(0, Number(product.stockAvailable) - 3),
  },
}))
