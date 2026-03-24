import { useEffect, useState } from 'react';
import { Wallet, ArrowDownLeft, ArrowUpRight, CreditCard } from 'lucide-react';
import toast from 'react-hot-toast';
import { paymentApi } from '../../api/payment.api';
import Header from '../../components/layout/Header';
import FloatingCartBar from '../../components/cart/FloatingCartBar';

export default function WalletPage() {
  const [wallet, setWallet]           = useState(null);
  const [transactions, setTxns]       = useState([]);
  const [page, setPage]               = useState(0);
  const [totalPages, setTotalPages]   = useState(1);
  const [loading, setLoading]         = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    const init = async () => {
      try {
        const [walletRes, histRes] = await Promise.allSettled([
          paymentApi.getWallet(),
          paymentApi.getHistory({ page: 0, size: 20 }),
        ]);
        if (walletRes.status === 'fulfilled') setWallet(walletRes.value.data?.data);
        if (histRes.status === 'fulfilled') {
          const d = histRes.value.data?.data;
          setTxns(d?.content || []);
          setTotalPages(d?.totalPages || 1);
        }
      } catch {
        toast.error('Failed to load wallet');
      } finally {
        setLoading(false);
      }
    };
    init();
  }, []);

  const loadMore = async () => {
    setLoadingMore(true);
    try {
      const res = await paymentApi.getHistory({ page: page + 1, size: 20 });
      const d = res.data?.data;
      setTxns(prev => [...prev, ...(d?.content || [])]);
      setPage(p => p + 1);
      setTotalPages(d?.totalPages || 1);
    } catch {
      toast.error('Failed to load more');
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <div className="min-h-screen bg-blinkit-bg">
      <Header />
      <main className="max-w-xl mx-auto px-4 py-6 pb-24 space-y-4">
        <h1 className="text-xl font-black text-gray-900">My Wallet</h1>

        {/* Balance card */}
        <div className="bg-gradient-to-br from-yellow-400 to-yellow-300 rounded-3xl p-6 shadow-sm">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 bg-white/30 rounded-xl flex items-center justify-center">
              <Wallet size={20} className="text-gray-900" />
            </div>
            <p className="text-sm font-semibold text-gray-800">Available Balance</p>
          </div>
          {loading ? (
            <div className="h-10 bg-yellow-200 rounded-xl animate-pulse w-40" />
          ) : (
            <p className="text-4xl font-black text-gray-900">
              ₹{wallet?.balance?.toFixed(2) ?? '0.00'}
            </p>
          )}
          <p className="text-xs text-gray-700 mt-2 opacity-70">Blinkit Wallet</p>
        </div>

        {/* Transaction history */}
        <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100">
            <h2 className="text-sm font-bold text-gray-900">Transaction History</h2>
          </div>

          {loading ? (
            <div className="p-5 space-y-3">
              {[1, 2, 3, 4].map(i => <div key={i} className="h-14 bg-gray-100 rounded-xl animate-pulse" />)}
            </div>
          ) : transactions.length === 0 ? (
            <div className="text-center py-14 text-gray-400">
              <CreditCard size={36} className="mx-auto mb-3 opacity-30" />
              <p className="font-medium">No transactions yet</p>
            </div>
          ) : (
            <div className="divide-y divide-gray-50">
              {transactions.map(tx => {
                const isCredit = tx.type === 'CREDIT';
                const date = tx.createdAt
                  ? new Date(tx.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
                  : '—';
                return (
                  <div key={tx.transactionId || tx.id} className="flex items-center gap-3 px-5 py-3.5">
                    <div className={`w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0 ${
                      isCredit ? 'bg-green-100' : 'bg-red-100'
                    }`}>
                      {isCredit
                        ? <ArrowDownLeft size={16} className="text-green-600" />
                        : <ArrowUpRight size={16} className="text-red-500" />
                      }
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold text-gray-800 truncate">
                        {tx.description || tx.remarks || tx.type}
                      </p>
                      <p className="text-xs text-gray-400">{date}</p>
                    </div>
                    <p className={`text-sm font-black flex-shrink-0 ${isCredit ? 'text-green-600' : 'text-red-500'}`}>
                      {isCredit ? '+' : '-'}₹{tx.amount?.toFixed(2)}
                    </p>
                  </div>
                );
              })}
            </div>
          )}

          {page < totalPages - 1 && !loading && (
            <div className="px-5 py-4 border-t border-gray-50">
              <button onClick={loadMore} disabled={loadingMore}
                className="w-full border border-yellow-400 text-yellow-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-yellow-50 disabled:opacity-50">
                {loadingMore ? 'Loading...' : 'Load More'}
              </button>
            </div>
          )}
        </div>
      </main>
      <FloatingCartBar />
    </div>
  );
}
