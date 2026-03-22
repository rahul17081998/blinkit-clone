import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Package, ChevronRight, ChevronLeft } from 'lucide-react';
import { orderApi } from '../../api/order.api';
import Header from '../../components/layout/Header';

const STATUS_STYLE = {
  PENDING:             'bg-yellow-100 text-yellow-700',
  PAYMENT_PENDING:     'bg-yellow-100 text-yellow-700',
  PAYMENT_PROCESSING:  'bg-yellow-100 text-yellow-700',
  CONFIRMED:           'bg-blue-100 text-blue-700',
  PREPARING:           'bg-orange-100 text-orange-700',
  OUT_FOR_DELIVERY:    'bg-purple-100 text-purple-700',
  DELIVERED:           'bg-green-100 text-green-700',
  CANCELLED:           'bg-red-100 text-red-600',
};

function OrderCard({ order }) {
  const date = new Date(order.createdAt).toLocaleDateString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric',
  });
  const statusClass = STATUS_STYLE[order.status] || 'bg-gray-100 text-gray-600';

  return (
    <Link to={`/orders/${order.orderId}`} className="bg-white rounded-2xl p-4 flex items-center gap-3 hover:shadow-md transition-shadow">
      <div className="bg-gray-50 rounded-xl p-3">
        <Package size={22} className="text-gray-400" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-0.5">
          <p className="text-sm font-bold text-gray-900">#{order.orderNumber}</p>
          <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${statusClass}`}>
            {order.status?.replace(/_/g, ' ')}
          </span>
        </div>
        <p className="text-xs text-gray-400">{date} · {order.items?.length || 0} item{(order.items?.length || 0) > 1 ? 's' : ''}</p>
        <p className="text-sm font-semibold text-gray-700 mt-0.5">₹{order.totalAmount?.toFixed(0)}</p>
      </div>
      <ChevronRight size={16} className="text-gray-300 flex-shrink-0" />
    </Link>
  );
}

export default function OrdersPage() {
  const [orders, setOrders]   = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage]       = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  useEffect(() => {
    load(0);
  }, []);

  const load = async (p) => {
    setLoading(true);
    try {
      const res = await orderApi.getOrders({ page: p, size: 10 });
      const data = res.data.data;
      setOrders(prev => p === 0 ? data.content : [...prev, ...data.content]);
      setTotalPages(data.totalPages);
      setPage(p);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-blinkit-bg">
      <Header />
      <main className="max-w-2xl mx-auto px-4 py-4 pb-24">

        <div className="flex items-center gap-2 mb-4">
          <Link to="/" className="text-gray-400 hover:text-gray-600">
            <ChevronLeft size={22} />
          </Link>
          <h1 className="text-lg font-bold text-gray-900">My Orders</h1>
        </div>

        {loading && orders.length === 0 ? (
          <div className="space-y-3">
            {[1,2,3].map(i => <div key={i} className="h-20 bg-white rounded-2xl animate-pulse" />)}
          </div>
        ) : orders.length === 0 ? (
          <div className="text-center py-20 text-gray-400">
            <Package size={48} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No orders yet</p>
            <p className="text-sm mt-1">Start shopping to place your first order</p>
            <Link to="/" className="mt-4 inline-block text-primary font-semibold text-sm hover:underline">
              Browse Products →
            </Link>
          </div>
        ) : (
          <div className="space-y-3">
            {orders.map(order => <OrderCard key={order.orderId} order={order} />)}

            {page < totalPages - 1 && !loading && (
              <button
                onClick={() => load(page + 1)}
                className="w-full border border-primary text-primary font-semibold py-2.5 rounded-xl text-sm hover:bg-primary hover:text-dark transition-all"
              >
                Load More
              </button>
            )}
            {loading && page > 0 && (
              <div className="text-center text-gray-400 text-sm py-2">Loading...</div>
            )}
          </div>
        )}
      </main>
    </div>
  );
}
