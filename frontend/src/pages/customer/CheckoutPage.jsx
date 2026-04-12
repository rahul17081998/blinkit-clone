import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapPin, Plus, ChevronLeft, CheckCircle, Wallet, CreditCard, AlertCircle, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { userApi } from '../../api/user.api';
import { orderApi } from '../../api/order.api';
import { paymentApi } from '../../api/payment.api';
import { useCartStore } from '../../stores/cartStore';
import Header from '../../components/layout/Header';

const LABELS = ['HOME', 'WORK', 'OTHER'];

const EMPTY_FORM = {
  label: 'HOME',
  recipientName: '',
  recipientPhone: '',
  flatNo: '',
  building: '',
  street: '',
  area: '',
  city: '',
  state: '',
  pincode: '',
  landmark: '',
  lat: 0.0,
  lng: 0.0,
};

// ── Method icon map ────────────────────────────────────────────────────────────
function MethodIcon({ iconType, size = 20 }) {
  if (iconType === 'WALLET') return <Wallet size={size} className="text-yellow-500" />;
  return <CreditCard size={size} className="text-blue-500" />;
}

// ── Address card ───────────────────────────────────────────────────────────────
function AddressCard({ address, selected, onSelect }) {
  return (
    <div
      onClick={onSelect}
      className={`border-2 rounded-xl p-3 cursor-pointer transition-all ${selected ? 'border-primary bg-yellow-50' : 'border-gray-100 bg-white hover:border-gray-200'}`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-start gap-2">
          <MapPin size={15} className={`mt-0.5 flex-shrink-0 ${selected ? 'text-primary' : 'text-gray-400'}`} />
          <div>
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wide">{address.label}</p>
            <p className="text-sm font-semibold text-gray-900">{address.recipientName}</p>
            <p className="text-xs text-gray-500">
              {address.flatNo}, {address.building}, {address.area}, {address.city} — {address.pincode}
            </p>
            <p className="text-xs text-gray-400">{address.recipientPhone}</p>
          </div>
        </div>
        {selected && <CheckCircle size={18} className="text-primary flex-shrink-0" />}
      </div>
    </div>
  );
}

// ── Address form ───────────────────────────────────────────────────────────────
function AddressForm({ onSave, onCancel }) {
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const set = (field, val) => setForm(f => ({ ...f, [field]: val }));

  const handleSave = async () => {
    if (!form.recipientName || !form.recipientPhone || !form.flatNo || !form.building || !form.area || !form.city || !form.state || !form.pincode) {
      toast.error('Please fill all required fields');
      return;
    }
    setSaving(true);
    try {
      const res = await userApi.addAddress({ ...form, lat: 0.0, lng: 0.0 });
      onSave(res.data.data);
      toast.success('Address saved!');
    } catch (e) {
      toast.error(e.response?.data?.message || 'Failed to save address');
    } finally {
      setSaving(false);
    }
  };

  const input = (field, placeholder, required = true) => (
    <input
      type="text"
      value={form[field]}
      onChange={e => set(field, e.target.value)}
      placeholder={`${placeholder}${required ? ' *' : ''}`}
      className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
    />
  );

  return (
    <div className="bg-white rounded-2xl p-4 space-y-3">
      <h3 className="text-sm font-bold text-gray-900">Add New Address</h3>
      {/* Label */}
      <div className="flex gap-2">
        {LABELS.map(l => (
          <button key={l} onClick={() => set('label', l)}
            className={`px-3 py-1.5 text-xs font-semibold rounded-full border transition-all ${form.label === l ? 'bg-primary border-primary text-dark' : 'bg-white border-gray-200 text-gray-600'}`}>
            {l}
          </button>
        ))}
      </div>
      {/* Name + Phone */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {input('recipientName', 'Full Name')}
        {input('recipientPhone', 'Phone (10 digits)')}
      </div>
      {/* Address fields */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {input('flatNo', 'Flat / House No.')}
        {input('building', 'Building / Society')}
      </div>
      {input('street', 'Street', false)}
      {input('area', 'Area / Locality')}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {input('city', 'City')}
        {input('state', 'State')}
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {input('pincode', 'Pincode (6 digits)')}
        {input('landmark', 'Landmark', false)}
      </div>
      <div className="flex gap-2 pt-1">
        <button onClick={onCancel} className="flex-1 border border-gray-200 text-gray-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-gray-50 transition-colors">
          Cancel
        </button>
        <button onClick={handleSave} disabled={saving} className="flex-1 bg-primary text-dark font-bold py-2.5 rounded-xl text-sm disabled:opacity-50 hover:bg-yellow-400 transition-colors">
          {saving ? 'Saving...' : 'Save Address'}
        </button>
      </div>
    </div>
  );
}

// ── Payment method card ────────────────────────────────────────────────────────
function PaymentMethodCard({ method, selected, onSelect, walletBalance, orderTotal }) {
  const isWallet = method.methodId === 'WALLET';
  const insufficient = isWallet && walletBalance !== null && walletBalance !== undefined && walletBalance < orderTotal;

  return (
    <div
      onClick={() => !insufficient && onSelect(method.methodId)}
      className={`border-2 rounded-xl p-4 transition-all ${
        insufficient
          ? 'border-gray-100 bg-gray-50 opacity-60 cursor-not-allowed'
          : selected
            ? 'border-primary bg-yellow-50 cursor-pointer'
            : 'border-gray-100 bg-white hover:border-gray-200 cursor-pointer'
      }`}
    >
      <div className="flex items-start gap-3">
        <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${isWallet ? 'bg-yellow-100' : 'bg-blue-50'}`}>
          <MethodIcon iconType={method.iconType} size={20} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-bold text-gray-900">{method.displayName}</p>
            {selected && !insufficient && <CheckCircle size={16} className="text-primary flex-shrink-0" />}
          </div>
          <p className="text-xs text-gray-500 mt-0.5">{method.description}</p>

          {/* Wallet balance indicator */}
          {isWallet && walletBalance !== null && walletBalance !== undefined && (
            <div className={`flex items-center gap-1 mt-1.5 text-xs font-semibold ${insufficient ? 'text-red-500' : 'text-green-600'}`}>
              {insufficient
                ? <><AlertCircle size={12} /> Insufficient balance (₹{walletBalance.toFixed(0)} available)</>
                : <><CheckCircle size={12} /> Balance: ₹{walletBalance.toFixed(0)} available</>
              }
            </div>
          )}

          {/* Razorpay sub-labels */}
          {method.methodId === 'RAZORPAY' && (
            <div className="flex gap-1.5 mt-1.5 flex-wrap">
              {['UPI', 'Cards', 'Net Banking'].map(tag => (
                <span key={tag} className="text-xs bg-blue-50 text-blue-600 font-semibold px-2 py-0.5 rounded-full">{tag}</span>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ── Load Razorpay script dynamically ──────────────────────────────────────────
function loadRazorpayScript() {
  return new Promise((resolve) => {
    if (document.getElementById('razorpay-script')) { resolve(true); return; }
    const script = document.createElement('script');
    script.id = 'razorpay-script';
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

// ── Main CheckoutPage ──────────────────────────────────────────────────────────
export default function CheckoutPage() {
  const navigate = useNavigate();
  const items          = useCartStore(s => s.items);
  const couponCode     = useCartStore(s => s.couponCode);
  const discount       = useCartStore(s => s.discount);
  const getSubtotal    = useCartStore(s => s.getSubtotal);
  const getDeliveryFee = useCartStore(s => s.getDeliveryFee);
  const getTotal       = useCartStore(s => s.getTotal);
  const clearCart      = useCartStore(s => s.clearCart);

  const [addresses, setAddresses]           = useState([]);
  const [selectedId, setSelectedId]         = useState(null);
  const [showForm, setShowForm]             = useState(false);
  const [placing, setPlacing]               = useState(false);
  const [loadingAddresses, setLoadingAddresses] = useState(true);

  // Payment methods
  const [paymentMethods, setPaymentMethods] = useState([]);
  const [walletBalance, setWalletBalance]   = useState(null);
  const [selectedMethod, setSelectedMethod] = useState('WALLET');
  const [loadingMethods, setLoadingMethods] = useState(true);

  const total = getTotal();

  useEffect(() => {
    if (items.length === 0) { navigate('/cart'); return; }
    loadAddresses();
    loadPaymentMethods();
  }, []);

  const loadAddresses = async () => {
    try {
      const res = await userApi.getAddresses();
      const addrs = res.data.data || [];
      setAddresses(addrs);
      const def = addrs.find(a => a.isDefault) || addrs[0];
      if (def) setSelectedId(def.addressId);
    } catch (e) {
      console.error(e);
    } finally {
      setLoadingAddresses(false);
    }
  };

  const loadPaymentMethods = async () => {
    try {
      const res = await paymentApi.getMethods();
      const data = res.data?.data;
      const methods = data?.methods || [];
      const balance = data?.walletBalance ?? null;
      setPaymentMethods(methods);
      setWalletBalance(balance);

      // Auto-select: prefer wallet if sufficient, else first available method
      if (methods.length > 0) {
        const walletMethod = methods.find(m => m.methodId === 'WALLET');
        const walletSufficient = walletMethod && (balance === null || balance >= total);
        if (walletSufficient) {
          setSelectedMethod('WALLET');
        } else {
          const firstNonWallet = methods.find(m => m.methodId !== 'WALLET');
          if (firstNonWallet) setSelectedMethod(firstNonWallet.methodId);
          else setSelectedMethod(methods[0].methodId);
        }
      }
    } catch (e) {
      console.error(e);
      toast.error('Failed to load payment methods');
    } finally {
      setLoadingMethods(false);
    }
  };

  const handleAddressSaved = (addr) => {
    setAddresses(prev => [...prev, addr]);
    setSelectedId(addr.addressId);
    setShowForm(false);
  };

  // ── Place order via Wallet ─────────────────────────────────────────────────
  const placeWithWallet = async () => {
    const res = await orderApi.placeOrder(selectedId, 'WALLET');
    const order = res.data.data;
    await clearCart();
    toast.success('Order placed! Payment debited from wallet.');
    navigate(`/orders/${order.orderId}`, { replace: true });
  };

  // ── Place order via Razorpay ───────────────────────────────────────────────
  const placeWithRazorpay = async () => {
    // Step 1: Create our internal order (PAYMENT_PENDING)
    const orderRes = await orderApi.placeOrder(selectedId, 'RAZORPAY');
    const order = orderRes.data.data;

    // Step 2: Create Razorpay order on our backend
    const rpRes = await paymentApi.createRazorpayOrder({
      orderId: order.orderId,
      amount: total,
    });
    const rpData = rpRes.data?.data;

    // Step 3: Load Razorpay script if not loaded
    const loaded = await loadRazorpayScript();
    if (!loaded) {
      toast.error('Could not load Razorpay. Check your internet connection.');
      return;
    }

    // Step 4: Open Razorpay checkout popup
    return new Promise((resolve, reject) => {
      const options = {
        key: rpData.keyId,
        amount: rpData.amountInPaise,
        currency: rpData.currency || 'INR',
        name: 'Blinkit',
        description: `Order #${order.orderNumber}`,
        order_id: rpData.razorpayOrderId,
        handler: async (response) => {
          // Step 5: Verify payment on backend
          try {
            await paymentApi.verifyRazorpay({
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
              orderId: order.orderId,
              userId: '',          // gateway injects X-User-Id; backend overrides this
              addressId: selectedId,
              amount: total,
            });
            await clearCart();
            toast.success('Payment successful! Order confirmed.');
            navigate(`/orders/${order.orderId}`, { replace: true });
            resolve();
          } catch (err) {
            toast.error(err.response?.data?.message || 'Payment verification failed');
            reject(err);
          }
        },
        prefill: {},
        theme: { color: '#F8C200' },
        modal: {
          ondismiss: () => {
            toast('Payment cancelled. Your order is saved — complete payment later.', { icon: 'ℹ️' });
            navigate(`/orders/${order.orderId}`, { replace: true });
            resolve();
          },
        },
      };

      // eslint-disable-next-line no-undef
      const rzp = new Razorpay(options);
      rzp.open();
    });
  };

  const handlePlaceOrder = async () => {
    if (!selectedId) { toast.error('Please select a delivery address'); return; }
    if (!selectedMethod) { toast.error('Please select a payment method'); return; }
    setPlacing(true);
    try {
      if (selectedMethod === 'RAZORPAY') {
        await placeWithRazorpay();
      } else {
        await placeWithWallet();
      }
    } catch (e) {
      toast.error(e.response?.data?.message || 'Failed to place order');
    } finally {
      setPlacing(false);
    }
  };

  const subtotal    = getSubtotal();
  const deliveryFee = getDeliveryFee();

  return (
    <div className="min-h-screen bg-blinkit-bg">
      <Header />
      <main className="max-w-2xl mx-auto px-4 py-4 pb-24 space-y-4">

        {/* Back */}
        <button onClick={() => navigate('/cart')} className="flex items-center gap-1 text-gray-500 hover:text-gray-700 text-sm">
          <ChevronLeft size={18} /> Back to Cart
        </button>

        {/* Delivery address */}
        <div className="space-y-3">
          <h2 className="text-base font-bold text-gray-900">Delivery Address</h2>
          {loadingAddresses ? (
            <div className="space-y-2">
              {[1,2].map(i => <div key={i} className="h-20 bg-white rounded-xl animate-pulse" />)}
            </div>
          ) : (
            <>
              {addresses.map(addr => (
                <AddressCard
                  key={addr.addressId}
                  address={addr}
                  selected={addr.addressId === selectedId}
                  onSelect={() => setSelectedId(addr.addressId)}
                />
              ))}
              {!showForm && (
                <button
                  onClick={() => setShowForm(true)}
                  className="w-full border-2 border-dashed border-gray-200 rounded-xl py-3 text-sm text-gray-500 font-semibold flex items-center justify-center gap-2 hover:border-primary hover:text-primary transition-colors"
                >
                  <Plus size={16} /> Add New Address
                </button>
              )}
            </>
          )}
          {showForm && (
            <AddressForm onSave={handleAddressSaved} onCancel={() => setShowForm(false)} />
          )}
        </div>

        {/* Payment method */}
        <div className="space-y-3">
          <h2 className="text-base font-bold text-gray-900">Payment Method</h2>
          {loadingMethods ? (
            <div className="space-y-2">
              {[1,2].map(i => <div key={i} className="h-20 bg-white rounded-xl animate-pulse" />)}
            </div>
          ) : paymentMethods.length === 0 ? (
            <div className="bg-white rounded-xl p-4 text-sm text-gray-500 text-center">
              No payment methods available right now.
            </div>
          ) : (
            paymentMethods.map(method => (
              <PaymentMethodCard
                key={method.methodId}
                method={method}
                selected={selectedMethod === method.methodId}
                onSelect={setSelectedMethod}
                walletBalance={walletBalance}
                orderTotal={total}
              />
            ))
          )}
        </div>

        {/* Order summary */}
        <div className="bg-white rounded-2xl p-4">
          <h3 className="text-sm font-bold text-gray-900 mb-3">Order Summary</h3>
          <div className="space-y-1.5 mb-3">
            {items.slice(0, 3).map(item => (
              <div key={item.productId} className="flex justify-between text-sm text-gray-600">
                <span className="line-clamp-1 flex-1 mr-2">{item.productName} × {item.quantity}</span>
                <span className="flex-shrink-0">₹{(item.sellingPrice * item.quantity).toFixed(0)}</span>
              </div>
            ))}
            {items.length > 3 && (
              <p className="text-xs text-gray-400">+{items.length - 3} more items</p>
            )}
          </div>
          <div className="border-t border-gray-100 pt-2 space-y-1">
            <div className="flex justify-between text-sm text-gray-600">
              <span>Item Total</span><span>₹{subtotal.toFixed(0)}</span>
            </div>
            {discount > 0 && (
              <div className="flex justify-between text-sm text-green-600">
                <span>Discount ({couponCode})</span><span>- ₹{discount.toFixed(0)}</span>
              </div>
            )}
            <div className="flex justify-between text-sm text-gray-600">
              <span>Delivery</span>
              {deliveryFee === 0
                ? <span className="text-green-600 font-semibold">FREE</span>
                : <span>₹{deliveryFee}</span>}
            </div>
            <div className="flex justify-between text-base font-bold text-gray-900 pt-1">
              <span>Total</span><span>₹{total.toFixed(0)}</span>
            </div>
          </div>
        </div>

        {/* Place order button */}
        <button
          onClick={handlePlaceOrder}
          disabled={placing || !selectedId || !selectedMethod}
          className="w-full bg-primary text-dark font-bold py-4 rounded-2xl text-sm disabled:opacity-50 hover:bg-yellow-400 transition-colors flex items-center justify-center gap-2"
        >
          {placing
            ? <><Loader2 size={16} className="animate-spin" /> Processing...</>
            : `Pay ₹${total.toFixed(0)} via ${selectedMethod === 'WALLET' ? 'Wallet' : 'Razorpay'}`
          }
        </button>

        {selectedMethod === 'RAZORPAY' && (
          <p className="text-xs text-center text-gray-400">
            You'll be redirected to Razorpay's secure payment page to complete the payment.
          </p>
        )}
        {selectedMethod === 'WALLET' && walletBalance !== null && (
          <p className="text-xs text-center text-gray-400">
            ₹{total.toFixed(0)} will be deducted from your wallet balance of ₹{walletBalance.toFixed(0)}.
          </p>
        )}

      </main>
    </div>
  );
}
