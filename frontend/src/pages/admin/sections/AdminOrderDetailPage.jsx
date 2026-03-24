import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ChevronLeft, CheckCircle, XCircle, Clock, Truck, Package, MapPin, User, Phone, Mail } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../../api/admin.api';

const STATUS_STEPS = ['CONFIRMED', 'PREPARING', 'OUT_FOR_DELIVERY', 'DELIVERED'];

const STATUS_META = {
  PENDING:            { label: 'Pending',            icon: Clock,        color: 'text-yellow-500' },
  PAYMENT_PENDING:    { label: 'Payment Pending',    icon: Clock,        color: 'text-yellow-500' },
  PAYMENT_PROCESSING: { label: 'Payment Processing', icon: Clock,        color: 'text-yellow-500' },
  CONFIRMED:          { label: 'Order Confirmed',    icon: CheckCircle,  color: 'text-blue-500' },
  PREPARING:          { label: 'Being Prepared',     icon: Package,      color: 'text-orange-500' },
  OUT_FOR_DELIVERY:   { label: 'Out for Delivery',   icon: Truck,        color: 'text-purple-500' },
  DELIVERED:          { label: 'Delivered',          icon: CheckCircle,  color: 'text-green-500' },
  CANCELLED:          { label: 'Cancelled',          icon: XCircle,      color: 'text-red-500' },
};

const STATUS_STYLE = {
  PENDING:            'bg-yellow-100 text-yellow-700',
  PAYMENT_PENDING:    'bg-yellow-100 text-yellow-700',
  PAYMENT_PROCESSING: 'bg-yellow-100 text-yellow-700',
  CONFIRMED:          'bg-blue-100 text-blue-700',
  PREPARING:          'bg-orange-100 text-orange-700',
  OUT_FOR_DELIVERY:   'bg-purple-100 text-purple-700',
  DELIVERED:          'bg-green-100 text-green-700',
  CANCELLED:          'bg-red-100 text-red-600',
};

const ALL_STATUSES = ['PENDING', 'CONFIRMED', 'PREPARING', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'];

function StatusProgress({ status }) {
  if (['CANCELLED', 'PENDING', 'PAYMENT_PENDING', 'PAYMENT_PROCESSING'].includes(status)) return null;
  const currentIdx = STATUS_STEPS.indexOf(status);
  return (
    <div className="flex items-center gap-1 mt-3">
      {STATUS_STEPS.map((step, i) => (
        <div key={step} className="flex items-center gap-1 flex-1">
          <div className={`h-1.5 flex-1 rounded-full ${i <= currentIdx ? 'bg-yellow-400' : 'bg-gray-100'}`} />
          {i === STATUS_STEPS.length - 1 && (
            <div className={`w-2.5 h-2.5 rounded-full ${i <= currentIdx ? 'bg-yellow-400' : 'bg-gray-100'}`} />
          )}
        </div>
      ))}
    </div>
  );
}

export default function AdminOrderDetailPage() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [customer, setCustomer] = useState(null);
  const [address, setAddress] = useState(null);
  const [delivery, setDelivery] = useState(null);   // task
  const [partner, setPartner] = useState(null);     // delivery agent
  const [loading, setLoading] = useState(true);
  const [newStatus, setNewStatus] = useState('');
  const [updating, setUpdating] = useState(false);

  useEffect(() => { load(); }, [orderId]);

  const load = async () => {
    try {
      const res = await adminApi.getOrder(orderId);
      const data = res.data.data;
      setOrder(data);
      setNewStatus(data.status);

      // Fetch customer, address, delivery — all in parallel, silently ignore failures
      const [custRes, addrRes, delivRes] = await Promise.allSettled([
        data.userId   ? adminApi.getUserById(data.userId)           : Promise.resolve(null),
        data.addressId ? adminApi.getAddressById(data.addressId)    : Promise.resolve(null),
        adminApi.getDeliveryTaskByOrder(orderId),
      ]);

      if (custRes.status === 'fulfilled' && custRes.value?.data?.data) {
        setCustomer(custRes.value.data.data);
      } else if (data.userId) {
        // user-service profile missing — fall back to auth-service email
        try {
          const authRes = await adminApi.getAuthUserById(data.userId);
          const authUser = authRes.data?.data;
          if (authUser) setCustomer({ email: authUser.email, userId: authUser.userId });
        } catch {}
      }
      if (addrRes.status === 'fulfilled' && addrRes.value?.data?.data)   setAddress(addrRes.value.data.data);

      const task = delivRes.status === 'fulfilled' ? delivRes.value?.data?.data : null;
      if (task) {
        setDelivery(task);
        if (task.deliveryPartnerId) {
          try {
            const pRes = await adminApi.getDeliveryPartner(task.deliveryPartnerId);
            setPartner(pRes.data?.data ?? null);
          } catch {}
        }
      }
    } catch {
      toast.error('Order not found');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateStatus = async () => {
    if (newStatus === order.status) return;
    setUpdating(true);
    try {
      const res = await adminApi.updateOrderStatus(orderId, newStatus);
      setOrder(res.data.data);
      setNewStatus(res.data.data.status);
      toast.success('Order status updated');
    } catch (e) {
      toast.error(e.response?.data?.message || 'Failed to update status');
    } finally {
      setUpdating(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-4 animate-pulse">
        <div className="h-8 w-32 bg-gray-200 rounded-xl" />
        <div className="h-40 bg-white rounded-2xl" />
        <div className="h-48 bg-white rounded-2xl" />
        <div className="h-32 bg-white rounded-2xl" />
      </div>
    );
  }

  if (!order) {
    return (
      <div className="text-center py-20 text-gray-400">
        <p className="font-medium">Order not found</p>
        <button onClick={() => navigate(-1)} className="mt-3 text-yellow-500 font-semibold text-sm hover:underline">
          ← Go back
        </button>
      </div>
    );
  }

  const meta = STATUS_META[order.status] || STATUS_META['PENDING'];
  const StatusIcon = meta.icon;
  const statusClass = STATUS_STYLE[order.status] || 'bg-gray-100 text-gray-600';
  const date = new Date(order.createdAt).toLocaleString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });

  return (
    <div className="space-y-4 max-w-3xl">
      {/* Back */}
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-1 text-gray-500 hover:text-gray-700 text-sm"
      >
        <ChevronLeft size={18} /> Back to Orders
      </button>

      {/* Status card */}
      <div className="bg-white rounded-2xl p-5">
        <div className="flex items-start justify-between mb-2">
          <div>
            <p className="text-xs text-gray-400 mb-1">Order #{order.orderNumber}</p>
            <div className="flex items-center gap-2">
              <StatusIcon size={18} className={meta.color} />
              <h2 className="text-base font-bold text-gray-900">{meta.label}</h2>
            </div>
            <p className="text-xs text-gray-400 mt-1">{date}</p>
            <p className="text-xs text-gray-400 mt-0.5">Customer: <span className="font-mono">{order.userId}</span></p>
          </div>
          <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${statusClass}`}>
            {order.status?.replace(/_/g, ' ')}
          </span>
        </div>
        <StatusProgress status={order.status} />

        {/* Admin status update */}
        <div className="mt-4 pt-4 border-t border-gray-100 flex items-center gap-3">
          <label className="text-xs font-semibold text-gray-600">Update Status:</label>
          <select
            value={newStatus}
            onChange={e => setNewStatus(e.target.value)}
            className="flex-1 border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
          >
            {ALL_STATUSES.map(s => (
              <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
            ))}
          </select>
          <button
            onClick={handleUpdateStatus}
            disabled={updating || newStatus === order.status}
            className="bg-yellow-400 text-gray-900 font-bold px-4 py-2 rounded-xl text-sm disabled:opacity-40 hover:bg-yellow-300 transition-colors"
          >
            {updating ? 'Saving...' : 'Save'}
          </button>
        </div>
      </div>

      {/* Items */}
      <div className="bg-white rounded-2xl p-5">
        <h3 className="text-sm font-bold text-gray-900 mb-3">Items ({order.items?.length})</h3>
        <div className="space-y-3">
          {order.items?.map((item, idx) => (
            <div key={item.productId || idx} className="flex items-center gap-3">
              <div className="w-12 h-12 bg-gray-50 rounded-xl flex-shrink-0 overflow-hidden flex items-center justify-center">
                {item.imageUrl
                  ? <img src={item.imageUrl} alt={item.name} className="w-full h-full object-cover" onError={e => e.currentTarget.style.display = 'none'} />
                  : <span className="text-xl">📦</span>}
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
      <div className="bg-white rounded-2xl p-5 space-y-1.5">
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
          <span>Total</span>
          <span>₹{order.totalAmount?.toFixed(0)}</span>
        </div>
      </div>

      {/* Customer + Address */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {/* Customer */}
        <div className="bg-white rounded-2xl p-5 space-y-2">
          <h3 className="text-sm font-bold text-gray-900 flex items-center gap-2">
            <User size={15} className="text-blue-500" /> Customer
          </h3>
          {customer ? (
            <div className="space-y-1.5 text-sm">
              <p className="font-semibold text-gray-900">
                {[customer.firstName, customer.lastName].filter(Boolean).join(' ') || '—'}
              </p>
              <p className="text-gray-500 flex items-center gap-1.5">
                <Mail size={12} className="text-gray-400" /> {customer.email || '—'}
              </p>
              <p className="text-gray-500 flex items-center gap-1.5">
                <Phone size={12} className="text-gray-400" /> {customer.phone || 'No phone'}
              </p>
              <p className="text-xs text-gray-400 font-mono mt-1">{order.userId}</p>
            </div>
          ) : (
            <p className="text-xs text-gray-400 font-mono">{order.userId}</p>
          )}
        </div>

        {/* Delivery Address */}
        <div className="bg-white rounded-2xl p-5 space-y-2">
          <h3 className="text-sm font-bold text-gray-900 flex items-center gap-2">
            <MapPin size={15} className="text-yellow-500" /> Delivery Address
          </h3>
          {address ? (
            <div className="text-sm space-y-0.5">
              {address.label && <span className="text-xs font-bold text-blue-600 uppercase">{address.label}</span>}
              {address.recipientName && <p className="font-semibold text-gray-900">{address.recipientName}</p>}
              {address.recipientPhone && <p className="text-gray-500 flex items-center gap-1"><Phone size={11} className="text-gray-400" />{address.recipientPhone}</p>}
              <p className="text-gray-600">
                {[address.flatNo, address.building, address.street].filter(Boolean).join(', ')}
              </p>
              <p className="text-gray-600">
                {[address.area, address.city].filter(Boolean).join(', ')}
              </p>
              <p className="text-gray-600">
                {[address.state, address.pincode].filter(Boolean).join(' - ')}
              </p>
              {address.landmark && <p className="text-xs text-gray-400">Near: {address.landmark}</p>}
            </div>
          ) : (
            <p className="text-xs text-gray-400">{order.addressId || 'No address info'}</p>
          )}
        </div>
      </div>

      {/* Delivery Agent */}
      <div className="bg-white rounded-2xl p-5 space-y-3">
        <h3 className="text-sm font-bold text-gray-900 flex items-center gap-2">
          <Truck size={15} className="text-purple-500" /> Delivery Agent
        </h3>
        {partner ? (
          <div className="flex flex-col sm:flex-row sm:items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-purple-100 flex items-center justify-center flex-shrink-0">
              <span className="text-lg font-black text-purple-600">{partner.name?.[0]?.toUpperCase()}</span>
            </div>
            <div className="flex-1 space-y-1 text-sm">
              <p className="font-bold text-gray-900">{partner.name}</p>
              <p className="text-gray-500 flex items-center gap-1.5"><Phone size={12} className="text-gray-400" />{partner.phone}</p>
              <p className="text-gray-500 flex items-center gap-1.5"><Mail size={12} className="text-gray-400" />{partner.email}</p>
            </div>
            <div className="text-right space-y-1 text-xs">
              <p className="text-gray-500">{partner.vehicleType} · {partner.vehicleNumber}</p>
              <p className="text-gray-400">⭐ {partner.avgRating?.toFixed(1)} · {partner.totalDeliveries} deliveries</p>
            </div>
          </div>
        ) : delivery ? (
          <p className="text-sm text-gray-400">Partner ID: {delivery.deliveryPartnerId || 'Not yet assigned'}</p>
        ) : (
          <p className="text-sm text-gray-400 italic">No delivery task assigned yet</p>
        )}
        {delivery && (
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 pt-3 border-t border-gray-50 text-xs text-gray-500">
            <div><p className="text-gray-400">Task Status</p><p className="font-semibold text-gray-700">{delivery.status?.replace(/_/g,' ')}</p></div>
            <div><p className="text-gray-400">Store</p><p className="font-semibold text-gray-700">{delivery.storeName}</p></div>
            <div><p className="text-gray-400">Est. Delivery</p><p className="font-semibold text-gray-700">{delivery.estimatedDeliveryAt ? new Date(delivery.estimatedDeliveryAt).toLocaleTimeString('en-IN',{hour:'2-digit',minute:'2-digit'}) : '—'}</p></div>
            <div><p className="text-gray-400">Actual Delivery</p><p className="font-semibold text-gray-700">{delivery.actualDeliveryAt ? new Date(delivery.actualDeliveryAt).toLocaleString('en-IN',{day:'numeric',month:'short',hour:'2-digit',minute:'2-digit'}) : '—'}</p></div>
            <div className="sm:col-span-2"><p className="text-gray-400">Store Address</p><p className="font-semibold text-gray-700">{delivery.storeAddress}</p></div>
          </div>
        )}
      </div>

      {/* Payment */}
      <div className="bg-white rounded-2xl p-5">
        <div className="flex items-center gap-2">
          <MapPin size={16} className="text-yellow-400 flex-shrink-0" />
          <div>
            <p className="text-sm font-bold text-gray-900">Payment</p>
            <p className="text-xs text-gray-500">
              {order.paymentId ? 'Online Payment · ' + order.paymentId.slice(0, 12) + '...' : 'Cash on Delivery'}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
