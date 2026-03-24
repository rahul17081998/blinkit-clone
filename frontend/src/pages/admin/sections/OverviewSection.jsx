import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShoppingBag, Package, TrendingUp, DollarSign } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../../api/admin.api';
import { productApi } from '../../../api/product.api';

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

function StatCard({ icon: Icon, label, value, color, loading }) {
  return (
    <div className="bg-white rounded-2xl p-5 flex items-center gap-4 shadow-sm">
      <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${color}`}>
        <Icon size={22} />
      </div>
      <div>
        <p className="text-xs text-gray-500 font-medium">{label}</p>
        {loading ? (
          <div className="h-7 w-20 bg-gray-100 rounded animate-pulse mt-1" />
        ) : (
          <p className="text-2xl font-black text-gray-900">{value}</p>
        )}
      </div>
    </div>
  );
}

export default function OverviewSection() {
  const navigate = useNavigate();
  const [orders, setOrders]           = useState([]);
  const [recentOrders, setRecentOrders] = useState([]);
  const [productTotal, setProductTotal] = useState(0);
  const [loading, setLoading]         = useState(true);

  useEffect(() => {
    load();
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      const [ordersRes, prodRes] = await Promise.all([
        adminApi.getAllOrders({ page: 0, size: 20 }),
        productApi.getProducts({ page: 0, size: 1 }),
      ]);

      const ordersData = ordersRes.data?.data;
      const allOrders = ordersData?.content || [];
      setOrders(allOrders);
      setRecentOrders(allOrders.slice(0, 10));

      const prodData = prodRes.data?.data;
      setProductTotal(prodData?.totalElements || 0);
    } catch {
      toast.error('Failed to load overview data');
    } finally {
      setLoading(false);
    }
  };

  const totalRevenue = orders
    .filter(o => o.status === 'DELIVERED')
    .reduce((sum, o) => sum + (o.totalAmount || 0), 0);

  const recentRevenue = orders
    .slice(0, 20)
    .reduce((sum, o) => sum + (o.totalAmount || 0), 0);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-gray-900 mb-1">Overview</h2>
        <p className="text-sm text-gray-500">Welcome back, Admin</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard
          icon={ShoppingBag}
          label="Total Orders"
          value={orders.length}
          color="bg-blue-100 text-blue-600"
          loading={loading}
        />
        <StatCard
          icon={TrendingUp}
          label="Recent Revenue"
          value={`₹${recentRevenue.toFixed(0)}`}
          color="bg-green-100 text-green-600"
          loading={loading}
        />
        <StatCard
          icon={Package}
          label="Active Products"
          value={productTotal}
          color="bg-yellow-100 text-yellow-600"
          loading={loading}
        />
        <StatCard
          icon={DollarSign}
          label="Delivered Revenue"
          value={`₹${totalRevenue.toFixed(0)}`}
          color="bg-purple-100 text-purple-600"
          loading={loading}
        />
      </div>

      {/* Recent orders table */}
      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100">
          <h3 className="text-base font-bold text-gray-900">Recent Orders</h3>
        </div>
        {loading ? (
          <div className="p-6 space-y-3">
            {[1, 2, 3, 4, 5].map(i => (
              <div key={i} className="h-10 bg-gray-100 rounded-xl animate-pulse" />
            ))}
          </div>
        ) : recentOrders.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <ShoppingBag size={40} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No orders yet</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-6 py-3 text-left">Order #</th>
                  <th className="px-6 py-3 text-left">Customer</th>
                  <th className="px-6 py-3 text-left">Items</th>
                  <th className="px-6 py-3 text-left">Total</th>
                  <th className="px-6 py-3 text-left">Status</th>
                  <th className="px-6 py-3 text-left">Date</th>
                  <th className="px-6 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {recentOrders.map(order => {
                  const statusClass = STATUS_STYLE[order.status] || 'bg-gray-100 text-gray-600';
                  const date = new Date(order.createdAt).toLocaleDateString('en-IN', {
                    day: 'numeric', month: 'short', year: 'numeric',
                  });
                  return (
                    <tr
                      key={order.orderId}
                      onClick={() => navigate(`/admin/orders/${order.orderId}`)}
                      className="hover:bg-yellow-50 cursor-pointer transition-colors"
                    >
                      <td className="px-6 py-3 font-semibold text-gray-900">#{order.orderNumber}</td>
                      <td className="px-6 py-3 text-gray-500 font-mono text-xs">
                        {order.userId ? order.userId.slice(0, 8) + '...' : '—'}
                      </td>
                      <td className="px-6 py-3 text-gray-600">{order.items?.length || 0}</td>
                      <td className="px-6 py-3 font-semibold text-gray-900">₹{order.totalAmount?.toFixed(0)}</td>
                      <td className="px-6 py-3">
                        <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${statusClass}`}>
                          {order.status?.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="px-6 py-3 text-gray-400 text-xs">{date}</td>
                      <td className="px-6 py-3 text-gray-300 text-xs">View →</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
