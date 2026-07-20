import React, { useState, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { Haptics, NotificationType } from '@capacitor/haptics';

export type DonationRow = {
  key: string;
  label: string;
  helper: string;
  value: string;
  iconClass: string;
  iconTileClass: string;
  copyLabel?: string;
  openLabel?: string;
  canCopy: boolean;
  canOpen: boolean;
};

interface DonationSheetProps {
  donation: { kofiUrl?: string; upiId?: string; paypalUrl?: string } | null;
  isOpen: boolean;
  onClose: () => void;
  hapticEnabled?: boolean;
}

const DonationSheet: React.FC<DonationSheetProps> = ({
  donation,
  isOpen,
  onClose,
  hapticEnabled = false
}) => {
  const [openDonationKey, setOpenDonationKey] = useState<string | null>(null);
  const [copiedDonationField, setCopiedDonationField] = useState<string | null>(null);

  const toggleDonationRow = (key: string) => {
    setOpenDonationKey((current) => (current === key ? null : key));
  };

  const copyDonationValue = useCallback((text: string, key: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopiedDonationField(key);
      if (hapticEnabled) {
          Haptics.notification({ type: NotificationType.Success }).catch(() => {});
      }
      window.setTimeout(() => setCopiedDonationField(null), 2000);
    }).catch(() => {});
  }, [hapticEnabled]);

  const openDonationLink = useCallback((url: string) => {
    if (hapticEnabled) Haptics.selection().catch(() => {});
    const normalizedUrl = url.startsWith('http://') || url.startsWith('https://') ? url : `https://${url}`;
    window.open(normalizedUrl, '_blank', 'noopener,noreferrer');
  }, [hapticEnabled]);

  if (!isOpen) return null;

  const donationRows = [
    donation?.kofiUrl
      ? {
          key: 'kofi',
          label: 'Ko-fi',
          helper: 'Support Orion through Ko-fi',
          value: donation.kofiUrl,
          iconClass: 'fas fa-mug-hot',
          iconTileClass: 'bg-[#29ABE0]/15 text-[#29ABE0]',
          openLabel: 'Open Ko-fi',
          canCopy: false,
          canOpen: true
        }
      : null,
    donation?.upiId
      ? {
          key: 'upi',
          label: 'UPI',
          helper: 'Copy the UPI ID to pay from your app',
          value: donation.upiId,
          iconClass: 'fas fa-qrcode',
          iconTileClass: 'bg-green-500/15 text-green-500',
          copyLabel: 'Copy UPI',
          canCopy: true,
          canOpen: false
        }
      : null,
    donation?.paypalUrl
      ? {
          key: 'paypal',
          label: 'PayPal',
          helper: 'Open PayPal or copy the payment link',
          value: donation.paypalUrl,
          iconClass: 'fab fa-paypal',
          iconTileClass: 'bg-[#0070BA]/15 text-[#0070BA]',
          copyLabel: 'Copy PayPal',
          openLabel: 'Open PayPal',
          canCopy: true,
          canOpen: true
        }
      : null
  ].filter(Boolean) as DonationRow[];

  if (donationRows.length === 0) return null;

  const renderDonationRow = (row: DonationRow) => {
    const isRowOpen = openDonationKey === row.key;
    const isCopied = copiedDonationField === row.key;

    return (
      <div key={row.key} className="overflow-hidden rounded-2xl border border-theme-border/60 dark:border-theme-border/30 bg-card shadow-sm">
        <button
          onClick={() => toggleDonationRow(row.key)}
          className={`flex w-full items-center justify-between gap-3 px-4 py-4 text-left transition-colors ${isRowOpen ? 'bg-primary/6' : 'hover:bg-theme-element/50'}`}
        >
          <div className={`w-10 h-10 shrink-0 rounded-2xl flex items-center justify-center text-base ${row.iconTileClass}`}>
            <i className={row.iconClass}></i>
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-black text-theme-text">{row.label}</p>
            <p className="mt-1 text-[11px] text-theme-sub">{row.helper}</p>
          </div>
          <i className={`fas fa-chevron-down text-xs text-theme-sub transition-transform ${isRowOpen ? 'rotate-180' : ''}`}></i>
        </button>

        {isRowOpen && (
          <div className="px-4 pb-4">
            <code className="block rounded-xl border border-theme-border/50 dark:border-theme-border/30 bg-theme-element/45 dark:bg-theme-element/30 px-3 py-3 text-[11px] text-theme-text font-mono break-all">
              {row.value}
            </code>

            <div className="mt-3 flex flex-col gap-2">
              {row.canOpen && row.openLabel && (
                <button
                  onClick={() => openDonationLink(row.value)}
                  className="inline-flex w-full items-center justify-center gap-2 rounded-xl px-3 py-2.5 text-xs font-bold transition-all bg-primary text-white shadow-lg shadow-primary/20"
                >
                  <i className="fas fa-arrow-up-right-from-square text-[11px]" />
                  {row.openLabel}
                </button>
              )}

              {row.canCopy && row.copyLabel && (
                <button
                  onClick={() => copyDonationValue(row.value, row.key)}
                  className={`inline-flex w-full items-center justify-center gap-2 rounded-xl px-3 py-2.5 text-xs font-bold transition-all ${isCopied ? 'bg-primary text-white shadow-lg shadow-primary/20' : 'bg-primary/10 text-primary border border-primary/20 hover:bg-primary/15'}`}
                >
                  <i className={`fas ${isCopied ? 'fa-check' : 'fa-copy'} text-[11px]`} />
                  {isCopied ? 'Copied' : row.copyLabel}
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    );
  };

  return createPortal(
    <div
      className="fixed inset-0 z-[200] flex items-end justify-center bg-black/50 animate-fade-in sm:items-center sm:p-4"
      onClick={onClose}
    >
      <div
        className="relative flex max-h-[85vh] w-full max-w-md flex-col gap-5 overflow-y-auto rounded-t-[2.5rem] bg-surface dark:bg-surface border border-theme-border p-6 pb-8 shadow-2xl animate-slide-up sm:rounded-[2.5rem]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0 text-left">
            <h3 className="text-xl font-black tracking-tight text-theme-text">Support Orion</h3>
            <p className="mt-1 text-xs leading-relaxed text-theme-sub">
              Choose a payment method to support development.
            </p>
          </div>
          <button
            onClick={onClose}
            className="w-9 h-9 shrink-0 rounded-full bg-theme-element border border-theme-border flex items-center justify-center text-theme-text transition-colors hover:bg-theme-hover"
          >
            <i className="fas fa-times text-sm"></i>
          </button>
        </div>

        <div className="space-y-3">
          {donationRows.map(renderDonationRow)}
        </div>
      </div>
    </div>,
    document.body
  );
};

export default DonationSheet;
