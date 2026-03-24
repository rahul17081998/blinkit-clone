import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShoppingBag, Check } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../../api/admin.api';

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

const ALL_STATUSES = [
  'PENDING',
  'PAYMENT_PENDING',
  'PAYMENT_PROCESSING',
  'CONFIRMED',
  'PREPARING',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'CANCELLED',
];

function OrderRow({ order, customerName, onStatusSaved }) {
  const navigate = useNavigate();
  const [editing, setEditing]   = useState(false);
  const [newStatus, setNewStatus] = useState(order.status);
  const [saving, setSaving]     = useState(false);

  const statusClass = STATUS_STYLE[order.status] || 'bg-gray-100 text-gray-600';
  const date = new Date(order.createdAt).toLocaleDateString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric',
  });

  const handleSave = async () => {
    if (newStatus === order.status) { setEditing(false); return; }
    setSaving(true);
    try {
      await adminApi.updateOrderStatus(order.orderId, newStatus);
      toast.success('Order status updated');
      onStatusSaved(order.orderId, newStatus);
      setEditing(false);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to update status');
    } finally {
      setSaving(false);
    }
  };

  return (
    <tr className="hover:bg-gray-50 transition-colors">
      <td
        className="px-5 py-3 font-semibold text-gray-900 cursor-pointer hover:text-yellow-600"
        onClick={() => navigate(`/admin/orders/${order.orderId}`)}
      >#{order.orderNumber}</td>
      <td className="px-5 py-3">
        <p className="text-sm font-medium text-gray-800">{customerName || '—'}</p>
        <p className="text-xs text-gray-400 font-mono">{order.userId?.slice(0, 8)}...</p>
      </td>
      <td className="px-5 py-3 text-gray-600">{order.items?.length || 0}</td>
      <td className="px-5 py-3 font-semibold text-gray-900">₹{order.totalAmount?.toFixed(0)}</td>
      <td className="px-5 py-3">
        <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${statusClass}`}>
          {order.status?.replace(/_/g, ' ')}
        </span>
      </td>
      <td className="px-5 py-3 text-gray-400 text-xs">{date}</td>
      <td className="px-5 py-3">
        {editing ? (
          <div className="flex items-center gap-1.5">
            <select
              value={newStatus}
              onChange={e => setNewStatus(e.target.value)}
              className="border border-gray-200 rounded-lg px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-yellow-400 bg-white"
            >
              {ALL_STATUSES.map(s => (
                <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
              ))}
            </select>
            <button
              onClick={handleSave}
              disabled={saving}
              className="bg-yellow-400 text-gray-900 font-bold px-2 py-1 rounded-lg text-xs hover:bg-yellow-500 disabled:opacity-60"
            >
              {saving ? '...' : <Check size={12} />}
            </button>
            <button
              onClick={() => { setEditing(false); setNewStatus(order.status); }}
              className="text-gray-400 hover:text-gray-600 text-xs px-1"
            >
              ✕
            </button>
          </div>
        ) : (
          <button
            onClick={() => setEditing(true)}
            className="text-xs font-semibold text-blue-600 hover:text-blue-800 underline"
          >
            Update
          </button>
        )}
      </td>
    </tr>
  );
}

export default function OrdersSection() {
  const navigate = useNavigate();
  const [orders, setOrders]       = useState([]);
  const [loading, setLoading]     = useState(true);
  const [page, setPage]           = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [customerNames, setCustomerNames] = useState({}); // userId → name

  useEffect(() => {
    loadOrders(0);
  }, [statusFilter]);

  const loadOrders = async (p) => {
    setLoading(true);
    try {
      const params = { page: p, size: 20 };
      if (statusFilter !== 'ALL') params.status = statusFilter;
      const res = await adminApi.getAllOrders(params);
      const data = res.data?.data;
      const newOrders = data?.content || [];
      setOrders(prev => p === 0 ? newOrders : [...prev, ...newOrders]);
      setTotalPages(data?.totalPages || 1);
      setPage(p);

      // fetch customer names for new unique userIds
      const uniqueIds = [...new Set(newOrders.map(o => o.userId).filter(Boolean))];
      if (uniqueIds.length) {
        const results = await Promise.allSettled(
          uniqueIds.map(id => adminApi.getUserById(id).then(r => ({ id, profile: r.data?.data })))
        );
        // For any userId where user-service has no profile, fall back to auth-service email
        const missingIds = results
          .filter(r => r.status !== 'fulfilled' || !r.value?.profile)
          .map(r => r.value?.id)
          .filter(Boolean);
        const authResults = missingIds.length
          ? await Promise.allSettled(
              missingIds.map(id => adminApi.getAuthUserById(id).then(r => ({ id, authUser: r.data?.data })))
            )
          : [];
        setCustomerNames(prev => {
          const next = { ...prev };
          results.forEach(r => {
            if (r.status === 'fulfilled' && r.value?.profile) {
              const p = r.value.profile;
              next[r.value.id] = [p.firstName, p.lastName].filter(Boolean).join(' ') || p.email || r.value.id;
            }
          });
          authResults.forEach(r => {
            if (r.status === 'fulfilled' && r.value?.authUser) {
              const a = r.value.authUser;
              next[r.value.id] = a.email || r.value.id;
            }
          });
          return next;
        });
      }
    } catch {
      toast.error('Failed to load orders');
    } finally {
      setLoading(false);
    }
  };

  const handleStatusSaved = (orderId, newStatus) => {
    setOrders(prev =>
      prev.map(o => o.orderId === orderId ? { ...o, status: newStatus } : o)
    );
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Orders</h2>
          <p className="text-sm text-gray-500">Manage all customer orders</p>
        </div>
        <select
          value={statusFilter}
          onChange={e => setStatusFilter(e.target.value)}
          className="border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 bg-white"
        >
          <option value="ALL">All Statuses</option>
          {ALL_STATUSES.map(s => (
            <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
          ))}
        </select>
      </div>

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {loading && orders.length === 0 ? (
          <div className="p-6 space-y-3">
            {[1, 2, 3, 4, 5].map(i => (
              <div key={i} className="h-12 bg-gray-100 rounded-xl animate-pulse" />
            ))}
          </div>
        ) : orders.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <ShoppingBag size={40} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No orders found</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-5 py-3 text-left">Order #</th>
                  <th className="px-5 py-3 text-left">Customer</th>
                  <th className="px-5 py-3 text-left">Items</th>
                  <th className="px-5 py-3 text-left">Total</th>
                  <th className="px-5 py-3 text-left">Status</th>
                  <th className="px-5 py-3 text-left">Date</th>
                  <th className="px-5 py-3 text-left">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {orders.map(order => (
                  <OrderRow
                    key={order.orderId}
                    order={order}
                    customerName={customerNames[order.userId]}
                    onStatusSaved={handleStatusSaved}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
        {page < totalPages - 1 && !loading && (
          <div className="px-6 py-4 border-t border-gray-50">
            <button
              onClick={() => loadOrders(page + 1)}
              className="w-full border border-yellow-400 text-yellow-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-yellow-50 transition-colors"
            >
              Load More
            </button>
          </div>
        )}
        {loading && orders.length > 0 && (
          <div className="text-center py-3 text-gray-400 text-sm">Loading...</div>
        )}
      </div>
    </div>
  );
}
