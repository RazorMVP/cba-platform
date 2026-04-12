import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

interface SchemeInfo {
  name: string;
  code: string;
  color: string;
  status: 'active' | 'stub';
  privateDes: string;
  settlementFormat: string;
  packagerXml: string;
  adapter: string;
  capabilities: string[];
}

@Component({
  selector: 'app-scheme-config',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './scheme-config.html',
  styleUrl: './scheme-config.scss',
})
export class SchemeConfigComponent {
  expanded: string | null = null;

  readonly schemes: SchemeInfo[] = [
    {
      name: 'Visa',
      code: 'VISA',
      color: '#1a1f71',
      status: 'stub',
      privateDes: 'DE 60–63, DE 126',
      settlementFormat: 'BASE II',
      packagerXml: 'iso8583-visa.xml',
      adapter: 'VisaSchemeAdapter',
      capabilities: ['EMV chip', 'Contactless NFC', 'Magnetic stripe', '3D Secure 2.x', 'STIP stand-in'],
    },
    {
      name: 'Mastercard',
      code: 'MASTERCARD',
      color: '#eb001b',
      status: 'stub',
      privateDes: 'DE 48 PDS, DE 111–127 (MIP)',
      settlementFormat: 'IPM / GCMS',
      packagerXml: 'iso8583-mastercard.xml',
      adapter: 'MastercardSchemeAdapter',
      capabilities: ['EMV chip', 'Contactless NFC', 'Magnetic stripe', '3D Secure 2.x', 'MIP private data'],
    },
    {
      name: 'Verve',
      code: 'VERVE',
      color: '#006400',
      status: 'stub',
      privateDes: 'DE 62–63 (Interswitch)',
      settlementFormat: 'NIBSS e-settlement',
      packagerXml: 'iso8583-verve.xml',
      adapter: 'VerveSchemeAdapter',
      capabilities: ['EMV chip', 'Contactless NFC', 'Magnetic stripe', '3D Secure 2.x', 'Interswitch subelements'],
    },
    {
      name: 'Afrigo / PAPSS',
      code: 'AFRIGO',
      color: '#ff6600',
      status: 'stub',
      privateDes: 'Minimal — largely base standard',
      settlementFormat: 'PAPSS clearing',
      packagerXml: 'iso8583-afrigo.xml',
      adapter: 'AfrigoSchemeAdapter',
      capabilities: ['EMV chip', 'Contactless NFC', 'Pan-African settlement', 'REST-based transmission'],
    },
    {
      name: 'UnionPay',
      code: 'UNIONPAY',
      color: '#c0102c',
      status: 'stub',
      privateDes: 'DE 60–63 (CUP), QPBOC tags 9F7C/9F77/9F78/9F79',
      settlementFormat: 'CUPS / CNAPS',
      packagerXml: 'iso8583-unionpay.xml',
      adapter: 'UnionPaySchemeAdapter',
      capabilities: ['EMV chip', 'QPBOC contactless', 'SM4 cryptogram (domestic)', 'Dual-currency DE49/DE50', '3DES fallback (international)'],
    },
  ];

  toggle(code: string): void { this.expanded = this.expanded === code ? null : code; }
}
