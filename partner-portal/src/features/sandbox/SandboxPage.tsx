import { ExternalLink, Copy, CheckCheck } from 'lucide-react'
import { useState } from 'react'

const TEST_DATA = [
  { label: 'Customer ID', value: 'cust_demo_001', note: 'Jane Smith — KYC ACTIVE' },
  { label: 'Savings Account', value: 'acct_demo_savings', note: 'GBP — Balance £5,000' },
  { label: 'Checking Account', value: 'acct_demo_checking', note: 'GBP — Balance £12,500' },
  { label: 'Active Loan', value: 'loan_demo_001', note: '£25,000 personal loan' },
  { label: 'Debit Card', value: 'card_demo_debit', note: 'Linked to savings account' },
  { label: 'Credit Card', value: 'card_demo_credit', note: '£5,000 credit limit' },
]

const CODE_SAMPLES = [
  {
    title: 'Get an access token',
    lang: 'bash',
    code: `curl -X POST https://sandbox.nubbank.com/api/v1/partners/auth/login \\
  -H "Content-Type: application/json" \\
  -d '{"email":"dev@yourcompany.com","password":"yourpassword"}'`,
  },
  {
    title: 'List accounts',
    lang: 'bash',
    code: `curl https://sandbox.nubbank.com/open-banking/v3.1/accounts \\
  -H "Authorization: Bearer YOUR_TOKEN" \\
  -H "x-fapi-interaction-id: $(uuidgen)"`,
  },
  {
    title: 'Issue a card (API Key)',
    lang: 'bash',
    code: `curl -X POST https://sandbox.nubbank.com/card-api/v1/cards \\
  -H "Authorization: ApiKey YOUR_API_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{"customerId":"cust_demo_001","accountId":"acct_demo_savings","productId":"prod_demo_debit","cardType":"VIRTUAL"}'`,
  },
]

export default function SandboxPage() {
  const [copiedIdx, setCopiedIdx] = useState<number | null>(null)
  const copy = (text: string, i: number) => { navigator.clipboard.writeText(text); setCopiedIdx(i); setTimeout(() => setCopiedIdx(null), 2000) }

  return (
    <div className="p-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Sandbox</h1>
        <p className="text-gray-500 text-sm mt-1">Pre-seeded test data and code samples to get you started</p>
      </div>

      {/* Links */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        {[
          { label: 'Developer Docs', url: 'https://docs-site-five-dusky.vercel.app', desc: 'Full API reference and guides' },
          { label: 'Swagger UI (Backend)', url: 'https://sandbox.nubbank.com/swagger-ui.html', desc: 'Try backend endpoints live' },
          { label: 'Swagger UI (Card API)', url: 'https://sandbox.nubbank.com/card/swagger-ui.html', desc: 'Try card API endpoints live' },
        ].map(link => (
          <a key={link.label} href={link.url} target="_blank" rel="noreferrer" className="bg-white rounded-xl border border-gray-100 hover:border-blue-300 p-5 transition-colors group">
            <div className="flex items-start justify-between">
              <div>
                <p className="font-semibold text-gray-900 text-sm group-hover:text-blue-700">{link.label}</p>
                <p className="text-gray-400 text-xs mt-0.5">{link.desc}</p>
              </div>
              <ExternalLink size={14} className="text-gray-400 group-hover:text-blue-500 flex-shrink-0 mt-0.5" />
            </div>
          </a>
        ))}
      </div>

      {/* Test data */}
      <div className="bg-white rounded-xl border border-gray-100 overflow-hidden mb-8">
        <div className="px-5 py-4 border-b border-gray-100">
          <h2 className="font-semibold text-gray-900 text-sm">Pre-Seeded Test Data</h2>
          <p className="text-gray-400 text-xs mt-0.5">Use these IDs in your API calls against the sandbox</p>
        </div>
        <table className="w-full text-sm">
          <thead className="bg-gray-50"><tr><th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase">Resource</th><th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase">ID</th><th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase">Details</th></tr></thead>
          <tbody className="divide-y divide-gray-100">
            {TEST_DATA.map((row, i) => (
              <tr key={i} className="hover:bg-gray-50">
                <td className="px-5 py-3 text-gray-700 font-medium">{row.label}</td>
                <td className="px-5 py-3">
                  <div className="flex items-center gap-2">
                    <code className="font-mono text-xs bg-gray-100 px-2 py-0.5 rounded text-gray-800">{row.value}</code>
                    <button onClick={() => copy(row.value, i)} className="text-gray-400 hover:text-gray-700">
                      {copiedIdx === i ? <CheckCheck size={13} className="text-green-500" /> : <Copy size={13} />}
                    </button>
                  </div>
                </td>
                <td className="px-5 py-3 text-gray-500 text-xs">{row.note}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Code samples */}
      <h2 className="font-semibold text-gray-900 mb-4">Quick Start</h2>
      <div className="space-y-4">
        {CODE_SAMPLES.map((s, i) => (
          <div key={i} className="bg-white rounded-xl border border-gray-100 overflow-hidden">
            <div className="flex items-center justify-between px-5 py-3 border-b border-gray-100 bg-gray-50">
              <span className="text-xs font-medium text-gray-700">{s.title}</span>
              <button onClick={() => copy(s.code, 100 + i)} className="text-gray-400 hover:text-gray-700">
                {copiedIdx === 100 + i ? <CheckCheck size={13} className="text-green-500" /> : <Copy size={13} />}
              </button>
            </div>
            <pre className="px-5 py-4 text-xs text-gray-700 overflow-x-auto leading-relaxed"><code>{s.code}</code></pre>
          </div>
        ))}
      </div>
    </div>
  )
}
