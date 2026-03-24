import { useEffect, useRef, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ChevronLeft, MapPin, Package, CheckCircle, XCircle,
  Clock, Truck, User, Phone, Bike,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { orderApi } from '../../api/order.api';
import Header from '../../components/layout/Header';

// ── Status config ──────────────────────────────────────────────────────────

const STATUS_STEPS = [
  { key: 'CONFIRMED',        label: 'Order Confirmed'      },
  { key: 'PACKED',           label: 'Picked by Partner'    },
  { key: 'OUT_FOR_DELIVERY', label: 'On the Way'           },
  { key: 'DELIVERED',        label: 'Delivered'            },
];

const STATUS_META = {
  PAYMENT_PENDING:    { label: 'Payment Pending',    icon: Clock,        color: 'text-yellow-500' },
  PAYMENT_PROCESSING: { label: 'Payment Processing', icon: Clock,        color: 'text-yellow-500' },
  PAYMENT_FAILED:     { label: 'Payment Failed',     icon: XCircle,      color: 'text-red-500'    },
  CONFIRMED:          { label: 'Order Confirmed',    icon: CheckCircle,  color: 'text-blue-500'   },
  PACKED:             { label: 'Picked by Partner',  icon: Package,      color: 'text-orange-500' },
  OUT_FOR_DELIVERY:   { label: 'On the Way',         icon: Truck,        color: 'text-purple-500' },
  DELIVERED:          { label: 'Delivered',          icon: CheckCircle,  color: 'text-green-500'  },
  CANCELLED:          { label: 'Cancelled',          icon: XCircle,      color: 'text-red-500'    },
};

const STATUS_BADGE = {
  PAYMENT_PENDING:    'bg-yellow-100 text-yellow-700',
  PAYMENT_PROCESSING: 'bg-yellow-100 text-yellow-700',
  PAYMENT_FAILED:     'bg-red-100 text-red-600',
  CONFIRMED:          'bg-blue-100 text-blue-700',
  PACKED:             'bg-orange-100 text-orange-700',
  OUT_FOR_DELIVERY:   'bg-purple-100 text-purple-700',
  DELIVERED:          'bg-green-100 text-green-700',
  CANCELLED:          'bg-red-100 text-red-600',
};

const ACTIVE_STATUSES = ['CONFIRMED', 'PACKED', 'OUT_FOR_DELIVERY'];

const VEHICLE_ICON = { BICYCLE: '🚲', MOTORCYCLE: '🏍️', SCOOTER: '🛵', CAR: '🚗' };

// ── Sub-components ─────────────────────────────────────────────────────────

function StatusTimeline({ status }) {
  if (['CANCELLED', 'PAYMENT_PENDING', 'PAYMENT_PROCESSING', 'PAYMENT_FAILED'].includes(status)) return null;
  const currentIdx = STATUS_STEPS.findIndex(s => s.key === status);
  return (
    <div className="mt-4">
      <div className="flex items-start gap-0">
        {STATUS_STEPS.map((step, i) => {
          const done    = i <= currentIdx;
          const current = i === currentIdx;
          return (
            <div key={step.key} className="flex-1 flex flex-col items-center">
              <div className="flex items-center w-full">
                {i > 0 && <div className={`flex-1 h-1 ${done ? 'bg-yellow-400' : 'bg-gray-100'}`} />}
                <div className={`w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0 border-2 transition-all ${
                  done
                    ? 'bg-yellow-400 border-yellow-400'
                    : 'bg-white border-gray-200'
                } ${current ? 'ring-2 ring-yellow-200' : ''}`}>
                  {done && <CheckCircle size={14} className="text-gray-900" />}
                </div>
                {i < STATUS_STEPS.length - 1 && (
                  <div className={`flex-1 h-1 ${i < currentIdx ? 'bg-yellow-400' : 'bg-gray-100'}`} />
                )}
              </div>
              <p className={`text-[10px] mt-1.5 text-center leading-tight font-medium ${
                current ? 'text-yellow-600' : done ? 'text-gray-600' : 'text-gray-300'
              }`}>
                {step.label}
              </p>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function PartnerCard({ tracking }) {
  if (!tracking || !tracking.deliveryPartnerId) return null;
  const vehicle = VEHICLE_ICON[tracking.vehicleType] || '🚚';
  return (
    <div className="bg-white rounded-2xl p-4">
      <h3 className="text-sm font-bold text-gray-900 mb-3 flex items-center gap-2">
        <Truck size={15} className="text-purple-500" /> Delivery Partner
      </h3>
      <div className="flex items-center gap-3">
        <div className="w-11 h-11 rounded-xl bg-purple-100 flex items-center justify-center flex-shrink-0">
          <span className="text-xl">{vehicle}</span>
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-bold text-gray-900">{tracking.partnerName || 'Delivery Partner'}</p>
          <p className="text-xs text-gray-500 capitalize">
            {tracking.vehicleType?.toLowerCase() || 'Vehicle'}
            {tracking.vehicleNumber ? ` · ${tracking.vehicleNumber}` : ''}
          </p>
        </div>
        {tracking.partnerPhone && (
          <a
            href={`tel:${tracking.partnerPhone}`}
            className="flex items-center gap-1.5 bg-green-100 text-green-700 text-xs font-semibold px-3 py-2 rounded-xl hover:bg-green-200 transition-colors"
          >
            <Phone size={13} /> Call
          </a>
        )}
      </div>

      {/* Live status message */}
      <div className={`mt-3 text-xs font-semibold px-3 py-2 rounded-xl ${
        tracking.status === 'ASSIGNED'         ? 'bg-blue-50 text-blue-700'   :
        tracking.status === 'PICKED_UP'        ? 'bg-orange-50 text-orange-700' :
        tracking.status === 'OUT_FOR_DELIVERY' ? 'bg-purple-50 text-purple-700' :
        tracking.status === 'DELIVERED'        ? 'bg-green-50 text-green-700' :
        'bg-gray-50 text-gray-500'
      }`}>
        {tracking.status === 'ASSIGNED'         && '📦 Partner is heading to pick up your order'}
        {tracking.status === 'PICKED_UP'        && '✅ Order picked up from store'}
        {tracking.status === 'OUT_FOR_DELIVERY' && '🛵 Partner is on the way to you!'}
        {tracking.status === 'DELIVERED'        && '🎉 Order delivered successfully'}
      </div>

      {tracking.estimatedDeliveryAt && tracking.status !== 'DELIVERED' && (
        <p className="text-xs text-gray-400 mt-2">
          ETA: {new Date(tracking.estimatedDeliveryAt).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}
        </p>
      )}
    </div>
  );
}

// ── Main component ─────────────────────────────────────────────────────────

export default function OrderDetailPage() {
  const { orderId } = useParams();
  const [order, setOrder]       = useState(null);
  const [tracking, setTracking] = useState(null);
  const [loading, setLoading]   = useState(true);
  const [cancelling, setCancelling] = useState(false);
  const pollRef = useRef(null);

  const fetchTracking = async (currentOrder) => {
    try {
      const res = await orderApi.trackOrder(orderId);
      setTracking(res.data.data);
    } catch {
      // task not yet created (order just placed) — silently ignore
    }
  };

  const load = async () => {
    try {
      const res = await orderApi.getOrder(orderId);
      const o = res.data.data;
      setOrder(o);
      if (o && !['CANCELLED', 'DELIVERED', 'PAYMENT_FAILED'].includes(o.status)) {
        fetchTracking(o);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  // Poll every 10 s when order is active
  const startPolling = () => {
    stopPolling();
    pollRef.current = setInterval(async () => {
      try {
        const [orderRes, trackRes] = await Promise.allSettled([
          orderApi.getOrder(orderId),
          orderApi.trackOrder(orderId),
        ]);
        if (orderRes.status === 'fulfilled') {
          const o = orderRes.value.data.data;
          setOrder(o);
          if (['CANCELLED', 'DELIVERED', 'PAYMENT_FAILED'].includes(o?.status)) stopPolling();
        }
        if (trackRes.status === 'fulfilled') setTracking(trackRes.value.data.data);
      } catch {}
    }, 10000);
  };

  const stopPolling = () => {
    if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
  };

  useEffect(() => {
    load();
    return stopPolling;
  }, [orderId]);

  // Start/stop polling based on order status
  useEffect(() => {
    if (!order) return;
    if (ACTIVE_STATUSES.includes(order.status)) {
      startPolling();
    } else {
      stopPolling();
    }
  }, [order?.status]);

  const handleCancel = async () => {
    if (!confirm('Cancel this order?')) return;
    setCancelling(true);
    try {
      const res = await orderApi.cancelOrder(orderId);
      setOrder(res.data.data);
      stopPolling();
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
          <div className="h-36 bg-white rounded-2xl" />
          <div className="h-24 bg-white rounded-2xl" />
          <div className="h-40 bg-white rounded-2xl" />
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

  const meta       = STATUS_META[order.status] || STATUS_META['PAYMENT_PENDING'];
  const StatusIcon = meta.icon;
  const badgeClass = STATUS_BADGE[order.status] || 'bg-gray-100 text-gray-600';
  const canCancel  = ['PAYMENT_PENDING', 'PAYMENT_PROCESSING', 'CONFIRMED'].includes(order.status);
  const isActive   = ACTIVE_STATUSES.includes(order.status);
  const date = new Date(order.createdAt).toLocaleString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });

  return (
    <div className="min-h-screen bg-blinkit-bg">
      <Header />
      <main className="max-w-2xl mx-auto px-4 py-4 pb-24 space-y-4">

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
                {isActive && (
                  <span className="flex items-center gap-1 text-[10px] text-green-600 font-semibold bg-green-50 px-2 py-0.5 rounded-full">
                    <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse inline-block" />
                    Live
                  </span>
                )}
              </div>
              <p className="text-xs text-gray-400 mt-1">{date}</p>
            </div>
            <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${badgeClass}`}>
              {order.status?.replace(/_/g, ' ')}
            </span>
          </div>
          <StatusTimeline status={order.status} />
          {order.status === 'DELIVERED' && (
            <p className="text-xs text-green-600 font-medium mt-3">🎉 Your order was delivered!</p>
          )}
        </div>

        {/* Delivery partner card — shown when partner assigned */}
        <PartnerCard tracking={tracking} />

        {/* Items */}
        <div className="bg-white rounded-2xl p-4">
          <h3 className="text-sm font-bold text-gray-900 mb-3">Items ({order.items?.length})</h3>
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

        {/* Payment */}
        <div className="bg-white rounded-2xl p-4 flex items-center gap-2">
          <MapPin size={16} className="text-primary flex-shrink-0" />
          <div>
            <p className="text-sm font-bold text-gray-900">Payment</p>
            <p className="text-xs text-gray-500">
              {order.paymentId ? 'Online Payment · ' + order.paymentId.slice(0, 8) + '...' : 'Cash on Delivery'}
            </p>
          </div>
        </div>

        {canCancel && (
          <button onClick={handleCancel} disabled={cancelling}
            className="w-full border-2 border-red-200 text-red-500 font-semibold py-3 rounded-2xl text-sm hover:bg-red-50 transition-colors disabled:opacity-50">
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
