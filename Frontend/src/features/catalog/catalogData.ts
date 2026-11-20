export type CatalogKind = 'products' | 'groups' | 'suppliers' | 'customers'

export type CatalogRow = {
  id: number
  code: string
  name: string
  [key: string]: string | number | boolean
}

export type CatalogField = {
  key: string
  label: string
  placeholder: string
  required?: boolean
  type?: 'text' | 'number' | 'email' | 'select' | 'checkbox'
  options?: string[]
}

export type CatalogConfig = {
  kind: CatalogKind
  title: string
  breadcrumb: string
  action: string
  searchPlaceholder: string
  tabs: { label: string; value: string }[]
  filters: { key: string; label: string; options: string[] }[]
  columns: { key: string; label: string; width: number }[]
  fields: CatalogField[]
  codePrefix: string
  seed: CatalogRow[]
}

const groupNames = ['Phụ kiện', 'Thiết bị ngoại vi', 'Lưu trữ', 'Mạng', 'Màn hình']
const productNames = [
  'Cáp HDMI 2m',
  'Chuột không dây',
  'Bàn phím cơ',
  'Màn hình 24 inch',
  'Ổ cứng SSD 512GB',
  'Router Wi-Fi 6',
  'Webcam Full HD',
  'Tai nghe Bluetooth',
  'Hub USB-C 7 cổng',
  'Ổ cứng di động 1TB',
  'Bộ phát Wi-Fi Mesh',
  'Microphone USB',
]
const companies = [
  'Công ty An Phát',
  'Cửa hàng Minh Khoa',
  'Công ty Việt Tiến',
  'Công ty Hoàng Long',
  'Công ty Sao Mai',
  'Công ty Đại Việt',
  'Công ty Bình Minh',
]

const productSeed: CatalogRow[] = productNames.map((name, index) => ({
  id: index + 1,
  code: `SP-${String(index + 1).padStart(4, '0')}`,
  name,
  group: groupNames[index % groupNames.length],
  unit: index % 3 === 0 ? 'Bộ' : 'Cái',
  defaultPriceIn: 180_000 + index * 75_000,
  defaultPriceOut: 240_000 + index * 95_000,
  hasSerial: index % 3 !== 0,
  warrantyMonths: index % 3 === 0 ? 6 : 12,
  minStock: 5 + (index % 4) * 5,
  stockAvailable: index % 4 === 0 ? 2 : 18 + index * 3,
}))

const groupSeed: CatalogRow[] = groupNames.map((name, index) => ({
  id: index + 1,
  code: `NSP-${String(index + 1).padStart(2, '0')}`,
  name,
  productCount: 12 + index * 7,
  createdAt: `0${index + 1}/11/2026`,
}))

const supplierSeed: CatalogRow[] = companies.map((name, index) => ({
  id: index + 1,
  code: `NCC-${String(index + 1).padStart(3, '0')}`,
  name,
  taxCode: `01012345${String(index + 1).padStart(2, '0')}`,
  phoneFax: `028 38${String(1000 + index).slice(-4)}`,
  email: `contact${index + 1}@example.com`,
  address: `Quận ${index + 1}, TP. Hồ Chí Minh`,
}))

const customerSeed: CatalogRow[] = companies.map((name, index) => ({
  id: index + 1,
  code: `KH-${String(index + 1).padStart(3, '0')}`,
  name,
  customerType: index % 2 === 0 ? 'BUSINESS' : 'RETAIL',
  taxCode: index % 2 === 0 ? `03012345${String(index + 1).padStart(2, '0')}` : '—',
  phone: `09${String(12345670 + index)}`,
  email: `khach${index + 1}@example.com`,
  address: `Quận ${index + 1}, TP. Hồ Chí Minh`,
}))

export const catalogConfigs: Record<CatalogKind, CatalogConfig> = {
  products: {
    kind: 'products',
    title: 'Sản phẩm',
    breadcrumb: 'Trang chủ / Danh mục / Sản phẩm',
    action: '+ Thêm sản phẩm',
    searchPlaceholder: 'Tìm mã, tên sản phẩm…',
    tabs: [
      { label: 'Tất cả', value: 'all' },
      { label: 'Có serial', value: 'serial' },
      { label: 'Dưới ngưỡng tồn', value: 'low-stock' },
    ],
    filters: [
      { key: 'group', label: 'Nhóm sản phẩm', options: ['Tất cả nhóm', ...groupNames] },
      { key: 'serial', label: 'Có serial', options: ['Tất cả', 'Có serial', 'Không serial'] },
    ],
    columns: [
      { key: 'code', label: 'Mã SP', width: 110 },
      { key: 'name', label: 'Tên sản phẩm', width: 240 },
      { key: 'group', label: 'Nhóm', width: 140 },
      { key: 'unit', label: 'Đơn vị', width: 80 },
      { key: 'defaultPriceIn', label: 'Giá nhập', width: 110 },
      { key: 'defaultPriceOut', label: 'Giá bán', width: 110 },
      { key: 'hasSerial', label: 'Serial', width: 90 },
      { key: 'warrantyMonths', label: 'Bảo hành', width: 100 },
    ],
    fields: [
      { key: 'code', label: 'Mã sản phẩm', placeholder: 'Nhập mã', required: true },
      { key: 'name', label: 'Tên sản phẩm', placeholder: 'Nhập tên', required: true },
      { key: 'group', label: 'Nhóm sản phẩm', placeholder: 'Chọn nhóm', required: true, type: 'select', options: groupNames },
      { key: 'unit', label: 'Đơn vị', placeholder: 'Cái / Bộ', required: true },
      { key: 'defaultPriceIn', label: 'Giá nhập mặc định', placeholder: '0', type: 'number' },
      { key: 'defaultPriceOut', label: 'Giá bán mặc định', placeholder: '0', type: 'number' },
      { key: 'defaultRentPricePerDay', label: 'Giá thuê mặc định / ngày', placeholder: '0', type: 'number' },
      { key: 'minStock', label: 'Tồn tối thiểu', placeholder: '0', type: 'number' },
      { key: 'warrantyMonths', label: 'Bảo hành (tháng)', placeholder: '0', type: 'number' },
      { key: 'hasSerial', label: 'Quản lý theo serial', placeholder: '', type: 'checkbox' },
    ],
    codePrefix: 'SP',
    seed: productSeed,
  },
  groups: {
    kind: 'groups',
    title: 'Nhóm sản phẩm',
    breadcrumb: 'Trang chủ / Danh mục / Nhóm sản phẩm',
    action: '+ Thêm nhóm',
    searchPlaceholder: 'Tìm mã, tên nhóm…',
    tabs: [{ label: 'Tất cả', value: 'all' }],
    filters: [{ key: 'sort', label: 'Sắp xếp', options: ['Tên A-Z', 'Tên Z-A'] }],
    columns: [
      { key: 'code', label: 'Mã nhóm', width: 160 },
      { key: 'name', label: 'Tên nhóm', width: 400 },
      { key: 'productCount', label: 'Số sản phẩm', width: 160 },
      { key: 'createdAt', label: 'Ngày tạo', width: 160 },
    ],
    fields: [
      { key: 'code', label: 'Mã nhóm', placeholder: 'Nhập mã nhóm', required: true },
      { key: 'name', label: 'Tên nhóm', placeholder: 'Nhập tên nhóm', required: true },
    ],
    codePrefix: 'NSP',
    seed: groupSeed,
  },
  suppliers: {
    kind: 'suppliers',
    title: 'Nhà cung cấp',
    breadcrumb: 'Trang chủ / Danh mục / Nhà cung cấp',
    action: '+ Thêm NCC',
    searchPlaceholder: 'Tìm mã, tên, mã số thuế…',
    tabs: [{ label: 'Tất cả', value: 'all' }],
    filters: [{ key: 'sort', label: 'Sắp xếp', options: ['Tên A-Z', 'Tên Z-A'] }],
    columns: [
      { key: 'code', label: 'Mã NCC', width: 100 },
      { key: 'name', label: 'Tên nhà cung cấp', width: 230 },
      { key: 'taxCode', label: 'Mã số thuế', width: 130 },
      { key: 'phoneFax', label: 'SĐT / Fax', width: 130 },
      { key: 'email', label: 'Email', width: 180 },
      { key: 'address', label: 'Địa chỉ', width: 210 },
    ],
    fields: [
      { key: 'code', label: 'Mã NCC', placeholder: 'Nhập mã', required: true },
      { key: 'name', label: 'Tên nhà cung cấp', placeholder: 'Nhập tên', required: true },
      { key: 'taxCode', label: 'Mã số thuế', placeholder: 'Nhập mã số thuế', required: true },
      { key: 'phoneFax', label: 'SĐT / Fax', placeholder: 'Nhập số điện thoại' },
      { key: 'email', label: 'Email', placeholder: 'name@congty.com', type: 'email' },
      { key: 'address', label: 'Địa chỉ', placeholder: 'Nhập địa chỉ' },
      { key: 'bankAccountName', label: 'Tên chủ tài khoản', placeholder: 'Nhập tên chủ tài khoản' },
      { key: 'bankAccountNo', label: 'Số tài khoản', placeholder: 'Nhập số tài khoản' },
      { key: 'bankName', label: 'Ngân hàng', placeholder: 'Nhập tên ngân hàng' },
    ],
    codePrefix: 'NCC',
    seed: supplierSeed,
  },
  customers: {
    kind: 'customers',
    title: 'Khách hàng',
    breadcrumb: 'Trang chủ / Danh mục / Khách hàng',
    action: '+ Thêm khách hàng',
    searchPlaceholder: 'Tìm mã, tên, SĐT…',
    tabs: [
      { label: 'Tất cả', value: 'all' },
      { label: 'Bán lẻ', value: 'RETAIL' },
      { label: 'Doanh nghiệp', value: 'BUSINESS' },
    ],
    filters: [
      { key: 'customerType', label: 'Loại khách', options: ['Tất cả loại', 'Bán lẻ', 'Doanh nghiệp'] },
    ],
    columns: [
      { key: 'code', label: 'Mã KH', width: 100 },
      { key: 'name', label: 'Tên khách hàng', width: 220 },
      { key: 'customerType', label: 'Loại', width: 110 },
      { key: 'taxCode', label: 'Mã số thuế', width: 130 },
      { key: 'phone', label: 'SĐT', width: 130 },
      { key: 'email', label: 'Email', width: 180 },
      { key: 'address', label: 'Địa chỉ', width: 110 },
    ],
    fields: [
      { key: 'code', label: 'Mã khách hàng', placeholder: 'Nhập mã', required: true },
      { key: 'name', label: 'Tên khách hàng', placeholder: 'Nhập tên', required: true },
      { key: 'customerType', label: 'Loại khách', placeholder: 'Chọn loại khách', required: true, type: 'select', options: ['Bán lẻ', 'Doanh nghiệp'] },
      { key: 'taxCode', label: 'Mã số thuế (doanh nghiệp)', placeholder: 'Nhập mã số thuế' },
      { key: 'idCardNo', label: 'CCCD / CMND', placeholder: 'Nhập số giấy tờ' },
      { key: 'phone', label: 'Số điện thoại', placeholder: 'Nhập số điện thoại' },
      { key: 'email', label: 'Email', placeholder: 'name@congty.com', type: 'email' },
      { key: 'address', label: 'Địa chỉ', placeholder: 'Nhập địa chỉ' },
    ],
    codePrefix: 'KH',
    seed: customerSeed,
  },
}

export function newCatalogRows(): Record<CatalogKind, CatalogRow[]> {
  return {
    products: productSeed.map((row) => ({ ...row })),
    groups: groupSeed.map((row) => ({ ...row })),
    suppliers: supplierSeed.map((row) => ({ ...row })),
    customers: customerSeed.map((row) => ({ ...row })),
  }
}
