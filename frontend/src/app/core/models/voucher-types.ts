/** Loại chứng từ ERP được hỗ trợ — trùng ErpQueryService.SUPPORTED_TYPES ở backend. */
export interface VoucherTypeOption {
  value: string;
  label: string;
}

export const VOUCHER_TYPES: VoucherTypeOption[] = [
  { value: 'PURCHASE_INVOICE', label: 'Hóa đơn mua hàng' },
  { value: 'SALES_INVOICE', label: 'Hóa đơn bán hàng' },
  { value: 'PAYMENT', label: 'Phiếu chi' },
  { value: 'RECEIPT', label: 'Phiếu thu' },
  { value: 'BANK_DEBIT', label: 'Báo Nợ' },
  { value: 'BANK_CREDIT', label: 'Báo Có' },
  { value: 'PURCHASE_RETURN', label: 'Hàng mua trả lại / giảm giá' },
  { value: 'SALES_RETURN', label: 'Hàng bán trả lại' },
];
