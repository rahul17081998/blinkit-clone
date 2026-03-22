import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Trash2, Tag, ChevronRight, ShoppingBag, ChevronDown, ChevronUp } from 'lucide-react';
import toast from 'react-hot-toast';
import { useCartStore } from '../../stores/cartStore';
import { productApi } from '../../api/product.api';
import Header from '../../components/layout/Header';
import AddToCartButton from '../../components/product/AddToCartButton';

function CartItemRow({ item }) {
  const deleteItem = useCartStore(s => s.deleteItem);

  const isRealImg = item.thumbnailUrl &&
    item.thumbnailUrl.startsWith('https://') &&
    !item.thumbnailUrl.includes('source.unsplash') &&
    !item.thumbnailUrl.includes('placehold');

  return (
    <div className="flex items-center gap-3 py-3 border-b border-gray-50 last:border-0">
      <div className="w-14 h-14 rounded-xl bg-gray-50 flex-shrink-0 overflow-hidden flex items-center justify-center">
        {isRealImg
          ? <img src={item.thumbnailUrl} alt={item.productName} className="w-full h-full object-cover" onError={e => e.currentTarget.style.display='none'} />
          : <span className="text-2xl">🛒</span>}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-gray-900 line-clamp-1">{item.productName}</p>
        <p className="text-xs text-gray-400">{item.unit}</p>
        <p className="text-sm font-bold text-gray-900 mt-0.5">₹{item.sellingPrice}</p>
      </div>
      <div className="flex items-center gap-2 flex-shrink-0">
        <AddToCartButton
          product={{ productId: item.productId, name: item.productName, thumbnailUrl: item.thumbnailUrl, sellingPrice: item.sellingPrice, mrp: item.mrp, unit: item.unit, isAvailable: item.isAvailable !== false }}
          size="sm"
        />
        <button onClick={() => deleteItem(item.productId)} className="p-1.5 text-gray-300 hover:text-red-400 transition-colors">
          <Trash2 size={15} />
        </button>
      </div>
    </div>
  );
}

function CouponLabel({ coupon }) {
  if (coupon.type === 'PERCENT') return `${coupon.value}% OFF${coupon.maxDiscount ? ` (up to ₹${coupon.maxDiscount})` : ''}`;
  if (coupon.type === 'FLAT')    return `₹${coupon.value} OFF`;
  if (coupon.type === 'FREE_DELIVERY') return 'Free Delivery';
  return `${coupon.value} OFF`;
}

function CouponCard({ coupon, subtotal, applied, onApply, onRemove, loading }) {
  const eligible = subtotal >= (coupon.minOrderAmount || 0);
  const isApplied = applied === coupon.code;

  return (
    <div className={`border rounded-xl p-3 transition-all ${isApplied ? 'border-green-400 bg-green-50' : eligible ? 'border-gray-200 bg-white' : 'border-gray-100 bg-gray-50 opacity-60'}`}>
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-0.5">
            <span className="font-black text-sm text-dark tracking-wide">{coupon.code}</span>
            <span className="text-[10px] font-bold bg-primary/20 text-dark px-1.5 py-0.5 rounded-md">
              <CouponLabel coupon={coupon} />
            </span>
          </div>
          {coupon.minOrderAmount > 0 && (
            <p className={`text-xs ${eligible ? 'text-gray-500' : 'text-red-400'}`}>
              {eligible
                ? `✓ Min order ₹${coupon.minOrderAmount} — you're eligible!`
                : `Min order ₹${coupon.minOrderAmount} (add ₹${(coupon.minOrderAmount - subtotal).toFixed(0)} more)`}
            </p>
          )}
        </div>
        {isApplied ? (
          <button onClick={onRemove} className="text-xs text-red-500 font-semibold hover:underline flex-shrink-0">
            Remove
          </button>
        ) : (
          <button
            onClick={() => onApply(coupon.code)}
            disabled={!eligible || loading}
            className="text-xs font-bold text-primary disabled:text-gray-300 disabled:cursor-not-allowed hover:underline flex-shrink-0"
          >
            {loading ? '...' : 'Apply'}
          </button>
        )}
      </div>
    </div>
  );
}

export default function CartPage() {
  const navigate = useNavigate();
  const items          = useCartStore(s => s.items);
  const couponCode     = useCartStore(s => s.couponCode);
  const discount       = useCartStore(s => s.discount);
  const getSubtotal    = useCartStore(s => s.getSubtotal);
  const getDeliveryFee = useCartStore(s => s.getDeliveryFee);
  const getTotal       = useCartStore(s => s.getTotal);
  const applyPromo     = useCartStore(s => s.applyPromo);
  const removePromo    = useCartStore(s => s.removePromo);

  const [coupons, setCoupons]         = useState([]);
  const [showManual, setShowManual]   = useState(false);
  const [promoInput, setPromoInput]   = useState('');
  const [promoLoading, setPromoLoading] = useState(false);
  const [applying, setApplying]       = useState(null); // track which coupon is being applied

  const subtotal    = getSubtotal();
  const deliveryFee = getDeliveryFee();
  const total       = getTotal();

  useEffect(() => {
    productApi.getActiveCoupons()
      .then(res => setCoupons(res.data.data || []))
      .catch(() => {});
  }, []);

  const handleApply = async (code) => {
    setApplying(code);
    const res = await applyPromo(code);
    setApplying(null);
    if (res.success) toast.success('Coupon applied!');
    else if (res.message) toast.error(res.message);
  };

  const handleRemove = async () => {
    await removePromo();
    toast.success('Coupon removed');
  };

  const handleManualApply = async () => {
    if (!promoInput.trim()) return;
    setPromoLoading(true);
    const res = await applyPromo(promoInput.trim().toUpperCase());
    setPromoLoading(false);
    if (res.success) { toast.success('Coupon applied!'); setPromoInput(''); setShowManual(false); }
    else if (res.message) toast.error(res.message);
  };

  if (items.length === 0) {
    return (
      <div className="min-h-screen bg-blinkit-bg">
        <Header />
        <div className="flex flex-col items-center justify-center py-24 text-center px-4">
          <ShoppingBag size={56} className="text-gray-200 mb-4" />
          <h2 className="text-lg font-bold text-gray-700 mb-1">Your cart is empty</h2>
          <p className="text-sm text-gray-400 mb-5">Add items from the store to get started</p>
          <Link to="/" className="bg-primary text-dark font-bold px-6 py-3 rounded-xl text-sm hover:bg-yellow-400 transition-colors">
            Browse Products
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-blinkit-bg">
      <Header />
      <main className="max-w-2xl mx-auto px-4 py-4 pb-24 space-y-4">

        {/* Cart items */}
        <div className="bg-white rounded-2xl p-4">
          <h2 className="text-base font-bold text-gray-900 mb-1">
            My Cart <span className="text-gray-400 font-normal text-sm">({items.length} item{items.length > 1 ? 's' : ''})</span>
          </h2>
          <p className="text-xs text-green-600 font-medium mb-3">⚡ Delivery in 10 minutes</p>
          {items.map(item => <CartItemRow key={item.productId} item={item} />)}
        </div>

        {/* Coupons */}
        <div className="bg-white rounded-2xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <Tag size={16} className="text-primary" />
            <h3 className="text-sm font-bold text-gray-900">Coupons & Offers</h3>
          </div>

          {coupons.length > 0 ? (
            <div className="space-y-2">
              {coupons.map(coupon => (
                <CouponCard
                  key={coupon.code}
                  coupon={coupon}
                  subtotal={subtotal}
                  applied={couponCode}
                  onApply={handleApply}
                  onRemove={handleRemove}
                  loading={applying === coupon.code}
                />
              ))}
            </div>
          ) : null}

          {/* Manual entry toggle */}
          <button
            onClick={() => setShowManual(v => !v)}
            className="w-full mt-3 flex items-center justify-between text-sm text-gray-500 hover:text-gray-700 py-1"
          >
            <span>Have another code?</span>
            {showManual ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
          </button>

          {showManual && (
            <div className="flex gap-2 mt-2">
              <input
                type="text"
                value={promoInput}
                onChange={e => setPromoInput(e.target.value.toUpperCase())}
                placeholder="Enter coupon code"
                className="flex-1 border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                onKeyDown={e => e.key === 'Enter' && handleManualApply()}
              />
              <button
                onClick={handleManualApply}
                disabled={promoLoading || !promoInput.trim()}
                className="bg-primary text-dark font-bold px-4 py-2 rounded-xl text-sm disabled:opacity-50 hover:bg-yellow-400 transition-colors"
              >
                {promoLoading ? '...' : 'Apply'}
              </button>
            </div>
          )}
        </div>

        {/* Bill summary */}
        <div className="bg-white rounded-2xl p-4 space-y-2">
          <h3 className="text-sm font-bold text-gray-900 mb-3">Bill Details</h3>
          <div className="flex justify-between text-sm text-gray-600">
            <span>Item Total</span>
            <span>₹{subtotal.toFixed(0)}</span>
          </div>
          {discount > 0 && (
            <div className="flex justify-between text-sm text-green-600">
              <span>Coupon Discount ({couponCode})</span>
              <span>- ₹{discount.toFixed(0)}</span>
            </div>
          )}
          <div className="flex justify-between text-sm text-gray-600">
            <span>Delivery Fee</span>
            {deliveryFee === 0
              ? <span className="text-green-600 font-semibold">FREE</span>
              : <span>₹{deliveryFee}</span>}
          </div>
          {deliveryFee > 0 && (
            <p className="text-xs text-gray-400">Add ₹{(199 - subtotal).toFixed(0)} more for free delivery</p>
          )}
          <div className="border-t border-gray-100 pt-2 flex justify-between text-base font-bold text-gray-900">
            <span>To Pay</span>
            <span>₹{total.toFixed(0)}</span>
          </div>
        </div>

        {/* Checkout button */}
        <button
          onClick={() => navigate('/checkout')}
          className="w-full bg-primary text-dark font-bold py-4 rounded-2xl text-sm flex items-center justify-between px-5 hover:bg-yellow-400 transition-colors"
        >
          <span>₹{total.toFixed(0)} to pay</span>
          <span className="flex items-center gap-1">Proceed to Checkout <ChevronRight size={16} /></span>
        </button>

      </main>
    </div>
  );
}
