import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ChevronLeft, MapPin, Package, CheckCircle, XCircle, Clock, Truck } from 'lucide-react';
import toast from 'react-hot-toast';
import { orderApi } from '../../api/order.api';
import Header from '../../components/layout/Header';

const STATUS_STEPS = ['CONFIRMED', 'PREPARING', 'OUT_FOR_DELIVERY', 'DELIVERED'];

const STATUS_META = {
  PENDING:             { label: 'Pending',             icon: Clock,         color: 'text-yellow-500' },
  PAYMENT_PENDING:     { label: 'Payment Pending',     icon: Clock,         color: 'text-yellow-500' },
  PAYMENT_PROCESSING:  { label: 'Payment Processing',  icon: Clock,         color: 'text-yellow-500' },
  CONFIRMED:           { label: 'Order Confirmed',     icon: CheckCircle,   color: 'text-blue-500' },
  PREPARING:           { label: 'Being Prepared',      icon: Package,       color: 'text-orange-500' },
  OUT_FOR_DELIVERY:    { label: 'Out for Delivery',    icon: Truck,         color: 'text-purple-500' },
  DELIVERED:           { label: 'Delivered',           icon: CheckCircle,   color: 'text-green-500' },
  CANCELLED:           { label: 'Cancelled',           icon: XCircle,       color: 'text-red-500' },
};

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

function StatusProgress({ status }) {
  if (status === 'CANCELLED' || status === 'PENDING' || status === 'PAYMENT_PENDING') return null;
  const currentIdx = STATUS_STEPS.indexOf(status);
  return (
    <div className="flex items-center gap-1 mt-3">
      {STATUS_STEPS.map((step, i) => (
        <div key={step} className="flex items-center gap-1 flex-1">
          <div className={`h-1.5 flex-1 rounded-full ${i <= currentIdx ? 'bg-primary' : 'bg-gray-100'}`} />
          {i === STATUS_STEPS.length - 1 && (
            <div className={`w-2.5 h-2.5 rounded-full ${i <= currentIdx ? 'bg-primary' : 'bg-gray-100'}`} />
          )}
        </div>
      ))}
    </div>
  );
}

export default function OrderDetailPage() {
  const { orderId } = useParams();
  const [order, setOrder]     = useState(null);
  const [loading, setLoading] = useState(true);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    load();
  }, [orderId]);

  const load = async () => {
    try {
      const res = await orderApi.getOrder(orderId);
      setOrder(res.data.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = async () => {
    if (!confirm('Cancel this order?')) return;
    setCancelling(true);
    try {
      const res = await orderApi.cancelOrder(orderId);
      setOrder(res.data.data);
      toast.success('Order cancelled');
    } catch (e) {
      toast.error(e.response?.data?.message || 'Cannot cancel this order');
    } finally {
      setCancelling(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-blinkit-bg">
        <Header />
        <div className="max-w-2xl mx-auto px-4 py-6 space-y-3 animate-pulse">
          <div className="h-32 bg-white rounded-2xl" />
          <div className="h-40 bg-white rounded-2xl" />
          <div className="h-24 bg-white rounded-2xl" />
        </div>
      </div>
    );
  }

  if (!order) {
    return (
      <div className="min-h-screen bg-blinkit-bg">
        <Header />
        <div className="text-center py-20 text-gray-400">
          <p>Order not found</p>
          <Link to="/orders" className="text-primary font-semibold mt-2 inline-block">← My Orders</Link>
        </div>
      </div>
    );
  }

  const meta = STATUS_META[order.status] || STATUS_META['PENDING'];
  const StatusIcon = meta.icon;
  const statusClass = STATUS_STYLE[order.status] || 'bg-gray-100 text-gray-600';
  const canCancel = ['PENDING', 'PAYMENT_PENDING', 'PAYMENT_PROCESSING', 'CONFIRMED'].includes(order.status);
  const date = new Date(order.createdAt).toLocaleString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });

  return (
    <div className="min-h-screen bg-blinkit-bg">
      <Header />
      <main className="max-w-2xl mx-auto px-4 py-4 pb-24 space-y-4">

        {/* Back */}
        <Link to="/orders" className="flex items-center gap-1 text-gray-500 hover:text-gray-700 text-sm">
          <ChevronLeft size={18} /> My Orders
        </Link>

        {/* Status card */}
        <div className="bg-white rounded-2xl p-4">
          <div className="flex items-start justify-between mb-2">
            <div>
              <p className="text-xs text-gray-400 mb-1">Order #{order.orderNumber}</p>
              <div className="flex items-center gap-2">
                <StatusIcon size={18} className={meta.color} />
                <h2 className="text-base font-bold text-gray-900">{meta.label}</h2>
              </div>
              <p className="text-xs text-gray-400 mt-1">{date}</p>
            </div>
            <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${statusClass}`}>
              {order.status?.replace(/_/g, ' ')}
            </span>
          </div>
          <StatusProgress status={order.status} />
          {order.status === 'DELIVERED' && (
            <p className="text-xs text-green-600 font-medium mt-2">⚡ Delivered in 10 minutes</p>
          )}
        </div>

        {/* Items */}
        <div className="bg-white rounded-2xl p-4">
          <h3 className="text-sm font-bold text-gray-900 mb-3">
            Items ({order.items?.length})
          </h3>
          <div className="space-y-3">
            {order.items?.map((item, idx) => (
              <div key={item.productId || idx} className="flex items-center gap-3">
                <div className="w-12 h-12 bg-gray-50 rounded-xl flex-shrink-0 overflow-hidden flex items-center justify-center">
                  {item.imageUrl
                    ? <img src={item.imageUrl} alt={item.name} className="w-full h-full object-cover" onError={e => e.currentTarget.style.display='none'} />
                    : <span className="text-xl">🛒</span>}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-gray-900 line-clamp-1">{item.name}</p>
                  <p className="text-xs text-gray-400">{item.unit} × {item.quantity}</p>
                </div>
                <p className="text-sm font-bold text-gray-900 flex-shrink-0">
                  ₹{(item.unitPrice * item.quantity).toFixed(0)}
                </p>
              </div>
            ))}
          </div>
        </div>

        {/* Bill */}
        <div className="bg-white rounded-2xl p-4 space-y-1.5">
          <h3 className="text-sm font-bold text-gray-900 mb-2">Bill Details</h3>
          <div className="flex justify-between text-sm text-gray-600">
            <span>Item Total</span>
            <span>₹{order.itemsTotal?.toFixed(0)}</span>
          </div>
          {order.couponDiscount > 0 && (
            <div className="flex justify-between text-sm text-green-600">
              <span>Discount {order.couponCode ? `(${order.couponCode})` : ''}</span>
              <span>- ₹{order.couponDiscount?.toFixed(0)}</span>
            </div>
          )}
          <div className="flex justify-between text-sm text-gray-600">
            <span>Delivery Fee</span>
            {(order.deliveryFee || 0) === 0
              ? <span className="text-green-600 font-semibold">FREE</span>
              : <span>₹{order.deliveryFee}</span>}
          </div>
          <div className="flex justify-between text-base font-bold text-gray-900 border-t border-gray-100 pt-2">
            <span>Total Paid</span>
            <span>₹{order.totalAmount?.toFixed(0)}</span>
          </div>
        </div>

        {/* Payment info */}
        <div className="bg-white rounded-2xl p-4">
          <div className="flex items-center gap-2">
            <MapPin size={16} className="text-primary flex-shrink-0" />
            <div>
              <p className="text-sm font-bold text-gray-900">Payment</p>
              <p className="text-xs text-gray-500">
                {order.paymentId ? 'Online Payment · ' + order.paymentId.slice(0, 8) + '...' : 'Cash on Delivery'}
              </p>
            </div>
          </div>
        </div>

        {/* Cancel button */}
        {canCancel && (
          <button
            onClick={handleCancel}
            disabled={cancelling}
            className="w-full border-2 border-red-200 text-red-500 font-semibold py-3 rounded-2xl text-sm hover:bg-red-50 transition-colors disabled:opacity-50"
          >
            {cancelling ? 'Cancelling...' : 'Cancel Order'}
          </button>
        )}

        <Link to="/" className="block text-center text-primary font-semibold text-sm hover:underline">
          Continue Shopping →
        </Link>
      </main>
    </div>
  );
}
