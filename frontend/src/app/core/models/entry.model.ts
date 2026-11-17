export interface InvoiceLine {
  lineNo: number;
  description: string | null;
  unit: string | null;
  quantity: string | null;
  unitPrice: string | null;
  amount: string | null;
  vatRate: string | null;
  vatLabel: string | null;
  vatAmount: string | null;
}

export interface ExtractedInvoice {
  invoiceTemplate: string | null;
  invoiceSeries: string | null;
  invoiceNo: string | null;
  invoiceDate: string | null;
  sellerName: string | null;
  sellerTaxCode: string | null;
  buyerName: string | null;
  buyerTaxCode: string | null;
  currency: string | null;
  exchangeRate: string | null;
  totalBeforeTax: string | null;
  totalVat: string | null;
  totalPayable: string | null;
  paymentMethod: string | null;
  lines: InvoiceLine[];
}

export interface SuggestedEntry {
  debitAccount: string;
  creditAccount: string;
  amount: string;
  reason: string;
  confidence: number;
}

export interface ValidationItem {
  ruleId: string;
  severity: 'ERROR' | 'WARNING' | 'INFO';
  message: string;
}

export interface EntryDraftResponse {
  id: string;
  sourceType: string;
  status: 'DRAFT' | 'VALIDATED' | 'CONFIRMED' | 'DISCARDED';
  invoice: ExtractedInvoice;
  partnerMatch: { found: boolean | null; code: string | null; name: string | null };
  fieldConfidence: Record<string, number>;
  /** Trường có độ tin cậy dưới ngưỡng cấu hình ở server — cần người dùng kiểm tra. */
  lowConfidenceFields: string[];
  suggestedEntries: SuggestedEntry[];
  validation: ValidationItem[];
  createdAt: string;
  updatedAt: string;
}
