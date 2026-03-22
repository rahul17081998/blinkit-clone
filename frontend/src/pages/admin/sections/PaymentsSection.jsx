import { useEffect, useState } from 'react';
import { CreditCard, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../../api/admin.api';

function TopUpModal({ wallet, onClose, onTopUp }) {
  const [form, setForm] = useState({ amount: '', description: '' });
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.amount || isNaN(parseFloat(form.amount)) || parseFloat(form.amount) <= 0) {
      toast.error('Enter a valid amount');
      return;
    }
    setSaving(true);
    try {
      await adminApi.topUpWallet(wallet.userId, {
        amount: parseFloat(form.amount),
        description: form.description.trim() || 'Admin top-up',
      });
      toast.success('Wallet topped up successfully');
      onTopUp();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to top up wallet');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-sm shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 className="text-base font-bold text-gray-900">Top Up Wallet</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div className="bg-gray-50 rounded-xl p-3 text-xs text-gray-500 space-y-0.5">
            {wallet.customerName && <p className="font-semibold text-gray-800">{wallet.customerName}</p>}
            {wallet.customerEmail && <p>{wallet.customerEmail}</p>}
            <p>Current balance: <span className="font-bold text-gray-900">₹{wallet.balance?.toFixed(2) ?? '0.00'}</span></p>
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Amount (₹) *</label>
            <input
              type="number"
              min="1"
              step="0.01"
              value={form.amount}
              onChange={e => setForm(p => ({ ...p, amount: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
              placeholder="100"
              required
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Description</label>
            <input
              value={form.description}
              onChange={e => setForm(p => ({ ...p, description: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
              placeholder="Reason for top-up..."
            />
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 border border-gray-200 text-gray-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-gray-50 transition-colors">
              Cancel
            </button>
            <button type="submit" disabled={saving}
              className="flex-1 bg-yellow-400 text-gray-900 font-bold py-2.5 rounded-xl text-sm hover:bg-yellow-500 transition-colors disabled:opacity-60">
              {saving ? 'Processing...' : 'Top Up'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function WalletsTab() {
  const [wallets, setWallets]       = useState([]);
  const [loading, setLoading]       = useState(true);
  const [page, setPage]             = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [topUpModal, setTopUpModal] = useState(null);

  useEffect(() => { loadWallets(0); }, []);

  const loadWallets = async (p) => {
    setLoading(true);
    try {
      const res = await adminApi.getWallets({ page: p, size: 20 });
      const data = res.data?.data;
      const newWallets = data?.content || data || [];

      // Fetch customer name + email for each unique userId
      const uniqueIds = [...new Set(newWallets.map(w => w.userId).filter(Boolean))];
      const profileMap = {};
      if (uniqueIds.length) {
        const results = await Promise.allSettled(
          uniqueIds.map(id => adminApi.getUserById(id).then(r => ({ id, profile: r.data?.data })))
        );
        const missing = results
          .filter(r => r.status !== 'fulfilled' || !r.value?.profile)
          .map(r => r.value?.id).filter(Boolean);
        const authResults = missing.length
          ? await Promise.allSettled(missing.map(id => adminApi.getAuthUserById(id).then(r => ({ id, authUser: r.data?.data }))))
          : [];
        results.forEach(r => {
          if (r.status === 'fulfilled' && r.value?.profile) {
            const p = r.value.profile;
            profileMap[r.value.id] = {
              name: [p.firstName, p.lastName].filter(Boolean).join(' ') || null,
              email: p.email || null,
            };
          }
        });
        authResults.forEach(r => {
          if (r.status === 'fulfilled' && r.value?.authUser) {
            profileMap[r.value.id] = { name: null, email: r.value.authUser.email };
          }
        });
      }

      const enriched = newWallets.map(w => ({
        ...w,
        customerName:  profileMap[w.userId]?.name  || null,
        customerEmail: profileMap[w.userId]?.email || null,
      }));

      setWallets(prev => p === 0 ? enriched : [...prev, ...enriched]);
      setTotalPages(data?.totalPages || 1);
      setPage(p);
    } catch {
      toast.error('Failed to load wallets');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-4">
      {topUpModal && (
        <TopUpModal wallet={topUpModal} onClose={() => setTopUpModal(null)}
          onTopUp={() => { setTopUpModal(null); loadWallets(0); }} />
      )}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {loading && wallets.length === 0 ? (
          <div className="p-6 space-y-3">
            {[1, 2, 3, 4].map(i => <div key={i} className="h-12 bg-gray-100 rounded-xl animate-pulse" />)}
          </div>
        ) : wallets.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <CreditCard size={40} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No wallets found</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-5 py-3 text-left">Customer</th>
                  <th className="px-5 py-3 text-left">Balance</th>
                  <th className="px-5 py-3 text-left">Last Updated</th>
                  <th className="px-5 py-3 text-left">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {wallets.map((wallet) => {
                  const updated = wallet.updatedAt || wallet.lastUpdated;
                  const date = updated
                    ? new Date(updated).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
                    : '—';
                  return (
                    <tr key={wallet.userId || wallet.walletId} className="hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3">
                        <p className="text-sm font-semibold text-gray-800">
                          {wallet.customerName || <span className="text-gray-400 font-normal">—</span>}
                        </p>
                        <p className="text-xs text-gray-400">{wallet.customerEmail || wallet.userId?.slice(0, 16) + '...'}</p>
                      </td>
                      <td className="px-5 py-3 font-bold text-gray-900">₹{wallet.balance?.toFixed(2) ?? '0.00'}</td>
                      <td className="px-5 py-3 text-gray-400 text-xs">{date}</td>
                      <td className="px-5 py-3">
                        <button onClick={() => setTopUpModal(wallet)}
                          className="text-xs font-semibold text-blue-600 hover:text-blue-800 underline">
                          Top Up
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {page < totalPages - 1 && !loading && (
          <div className="px-6 py-4 border-t border-gray-50">
            <button onClick={() => loadWallets(page + 1)}
              className="w-full border border-yellow-400 text-yellow-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-yellow-50 transition-colors">
              Load More
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function TransactionsTab() {
  const [transactions, setTransactions] = useState([]);
  const [emailMap, setEmailMap]         = useState({}); // userId → email
  const [loading, setLoading]           = useState(true);
  const [page, setPage]                 = useState(0);
  const [totalPages, setTotalPages]     = useState(1);

  useEffect(() => { loadTransactions(0); }, []);

  const loadTransactions = async (p) => {
    setLoading(true);
    try {
      const res = await adminApi.getTransactions({ page: p, size: 20 });
      const data = res.data?.data;
      const newTx = data?.content || data || [];
      setTransactions(prev => p === 0 ? newTx : [...prev, ...newTx]);
      setTotalPages(data?.totalPages || 1);
      setPage(p);

      // Fetch emails for unique userIds not yet in map
      const uniqueIds = [...new Set(newTx.map(t => t.userId).filter(Boolean))];
      if (uniqueIds.length) {
        const results = await Promise.allSettled(
          uniqueIds.map(id => adminApi.getUserById(id).then(r => ({ id, profile: r.data?.data })))
        );
        const missing = results
          .filter(r => r.status !== 'fulfilled' || !r.value?.profile)
          .map(r => r.value?.id).filter(Boolean);
        const authResults = missing.length
          ? await Promise.allSettled(missing.map(id => adminApi.getAuthUserById(id).then(r => ({ id, authUser: r.data?.data }))))
          : [];
        setEmailMap(prev => {
          const next = { ...prev };
          results.forEach(r => {
            if (r.status === 'fulfilled' && r.value?.profile)
              next[r.value.id] = r.value.profile.email || null;
          });
          authResults.forEach(r => {
            if (r.status === 'fulfilled' && r.value?.authUser)
              next[r.value.id] = r.value.authUser.email || null;
          });
          return next;
        });
      }
    } catch {
      toast.error('Failed to load transactions');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {loading && transactions.length === 0 ? (
          <div className="p-6 space-y-3">
            {[1, 2, 3, 4].map(i => <div key={i} className="h-12 bg-gray-100 rounded-xl animate-pulse" />)}
          </div>
        ) : transactions.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <CreditCard size={40} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No transactions found</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-5 py-3 text-left">Transaction ID</th>
                  <th className="px-5 py-3 text-left">User Email</th>
                  <th className="px-5 py-3 text-left">Type</th>
                  <th className="px-5 py-3 text-left">Amount</th>
                  <th className="px-5 py-3 text-left">Description</th>
                  <th className="px-5 py-3 text-left">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {transactions.map((tx) => {
                  const tid = tx.transactionId || tx.id;
                  const isCredit = tx.type === 'CREDIT';
                  const date = tx.createdAt
                    ? new Date(tx.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
                    : '—';
                  return (
                    <tr key={tid} className="hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3 font-mono text-xs text-gray-600">{tid?.slice(0, 10)}...</td>
                      <td className="px-5 py-3 text-xs text-gray-600">
                        {emailMap[tx.userId] || <span className="text-gray-300">{tx.userId?.slice(0, 10)}...</span>}
                      </td>
                      <td className="px-5 py-3">
                        <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${isCredit ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
                          {tx.type || '—'}
                        </span>
                      </td>
                      <td className={`px-5 py-3 font-bold ${isCredit ? 'text-green-600' : 'text-red-500'}`}>
                        {isCredit ? '+' : '-'}₹{tx.amount?.toFixed(2) ?? '0.00'}
                      </td>
                      <td className="px-5 py-3 text-gray-500 text-xs line-clamp-1 max-w-[200px]">
                        {tx.description || tx.remarks || '—'}
                      </td>
                      <td className="px-5 py-3 text-gray-400 text-xs">{date}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {page < totalPages - 1 && !loading && (
          <div className="px-6 py-4 border-t border-gray-50">
            <button onClick={() => loadTransactions(page + 1)}
              className="w-full border border-yellow-400 text-yellow-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-yellow-50 transition-colors">
              Load More
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default function PaymentsSection() {
  const [activeTab, setActiveTab] = useState('wallets');

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-xl font-bold text-gray-900">Payments</h2>
        <p className="text-sm text-gray-500">Manage wallets and transaction history</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-gray-100 p-1 rounded-xl w-fit">
        {[
          { key: 'wallets', label: 'Wallets' },
          { key: 'transactions', label: 'Transactions' },
        ].map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
              activeTab === tab.key
                ? 'bg-white text-gray-900 shadow-sm'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'wallets' ? <WalletsTab /> : <TransactionsTab />}
    </div>
  );
}
