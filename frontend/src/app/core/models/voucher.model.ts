export interface VoucherPartner {
  code: string | null;
  name: string | null;
  taxCode: string | null;
}

/** Trùng VoucherSearchController.VoucherListItem ở backend. */
export interface VoucherListItem {
  entityRef: string;
  entityType: string;
  voucherNo: string | null;
  voucherDate: string | null;
  description: string | null;
  partner: VoucherPartner | null;
  totalAmount: string | null;
  currency: string | null;
  openViolationCount: number;
  maxSeverity: 'ERROR' | 'WARNING' | 'INFO' | null;
}
