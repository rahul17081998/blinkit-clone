import { useEffect, useState } from 'react';
import { Plus, Edit2, Trash2, Ticket, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../../api/admin.api';

const EMPTY_FORM = {
  code: '',
  type: 'FLAT',
  value: '',
  maxDiscount: '',
  minOrderAmount: 0,
  usageLimit: '',
  perUserLimit: 1,
  validFrom: '',
  validUntil: '',
  isActive: true,
};

function toDatetimeLocal(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function CouponModal({ coupon, onClose, onSaved }) {
  const isEdit = !!coupon?.id;
  const [form, setForm] = useState(
    isEdit
      ? {
          code: coupon.code || '',
          type: coupon.type || 'FLAT',
          value: coupon.value ?? '',
          maxDiscount: coupon.maxDiscount ?? '',
          minOrderAmount: coupon.minOrderAmount ?? 0,
          usageLimit: coupon.usageLimit ?? '',
          perUserLimit: coupon.perUserLimit ?? 1,
          validFrom: toDatetimeLocal(coupon.validFrom),
          validUntil: toDatetimeLocal(coupon.validUntil),
          isActive: coupon.isActive !== false,
        }
      : { ...EMPTY_FORM }
  );
  const [saving, setSaving] = useState(false);

  const set = (key, val) => setForm(prev => ({ ...prev, [key]: val }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.code.trim()) { toast.error('Coupon code is required'); return; }
    if (form.type !== 'FREE_DELIVERY' && (!form.value || isNaN(parseFloat(form.value)))) {
      toast.error('Discount value is required');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        code: form.code.toUpperCase().trim(),
        type: form.type,
        value: form.type === 'FREE_DELIVERY' ? 0 : parseFloat(form.value),
        maxDiscount: form.maxDiscount !== '' ? parseFloat(form.maxDiscount) : null,
        minOrderAmount: parseFloat(form.minOrderAmount) || 0,
        usageLimit: form.usageLimit !== '' ? parseInt(form.usageLimit) : null,
        perUserLimit: parseInt(form.perUserLimit) || 1,
        validFrom: form.validFrom ? new Date(form.validFrom).toISOString() : null,
        validUntil: form.validUntil ? new Date(form.validUntil).toISOString() : null,
        isActive: form.isActive,
      };
      if (isEdit) {
        await adminApi.updateCoupon(coupon.id, payload);
        toast.success('Coupon updated');
      } else {
        await adminApi.createCoupon(payload);
        toast.success('Coupon created');
      }
      onSaved();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to save coupon');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 className="text-base font-bold text-gray-900">{isEdit ? 'Edit Coupon' : 'Create Coupon'}</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Code *</label>
              <input
                value={form.code}
                onChange={e => set('code', e.target.value.toUpperCase())}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm font-mono uppercase focus:outline-none focus:ring-2 focus:ring-yellow-400"
                placeholder="SAVE50"
                required
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Type *</label>
              <select
                value={form.type}
                onChange={e => set('type', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 bg-white"
              >
                <option value="FLAT">Flat Discount</option>
                <option value="PERCENT">Percentage Discount</option>
                <option value="FREE_DELIVERY">Free Delivery</option>
              </select>
            </div>
            {form.type !== 'FREE_DELIVERY' && (
              <div>
                <label className="block text-xs font-semibold text-gray-600 mb-1">
                  {form.type === 'PERCENT' ? 'Percentage (%)' : 'Flat Amount (₹)'} *
                </label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.value}
                  onChange={e => set('value', e.target.value)}
                  className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
                  placeholder={form.type === 'PERCENT' ? '10' : '50'}
                  required
                />
              </div>
            )}
            {form.type === 'PERCENT' && (
              <div>
                <label className="block text-xs font-semibold text-gray-600 mb-1">Max Discount (₹)</label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.maxDiscount}
                  onChange={e => set('maxDiscount', e.target.value)}
                  className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
                  placeholder="Optional cap"
                />
              </div>
            )}
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Min Order (₹)</label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.minOrderAmount}
                onChange={e => set('minOrderAmount', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
                placeholder="0"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Usage Limit</label>
              <input
                type="number"
                min="1"
                value={form.usageLimit}
                onChange={e => set('usageLimit', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
                placeholder="Leave blank for unlimited"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Per User Limit</label>
              <input
                type="number"
                min="1"
                value={form.perUserLimit}
                onChange={e => set('perUserLimit', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
                placeholder="1"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Valid From</label>
              <input
                type="datetime-local"
                value={form.validFrom}
                onChange={e => set('validFrom', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Valid Until</label>
              <input
                type="datetime-local"
                value={form.validUntil}
                onChange={e => set('validUntil', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
              />
            </div>
            <div className="col-span-2 flex items-center gap-3">
              <input
                type="checkbox"
                id="isActive"
                checked={form.isActive}
                onChange={e => set('isActive', e.target.checked)}
                className="w-4 h-4 accent-yellow-400 rounded"
              />
              <label htmlFor="isActive" className="text-sm font-medium text-gray-700">
                Active
              </label>
            </div>
          </div>
          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 border border-gray-200 text-gray-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving}
              className="flex-1 bg-yellow-400 text-gray-900 font-bold py-2.5 rounded-xl text-sm hover:bg-yellow-500 transition-colors disabled:opacity-60"
            >
              {saving ? 'Saving...' : isEdit ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function CouponTypeLabel({ type, value, maxDiscount }) {
  if (type === 'PERCENT') return <span>{value}% OFF{maxDiscount ? ` (max ₹${maxDiscount})` : ''}</span>;
  if (type === 'FLAT') return <span>₹{value} OFF</span>;
  if (type === 'FREE_DELIVERY') return <span>Free Delivery</span>;
  return <span>{value} OFF</span>;
}

export default function CouponsSection() {
  const [coupons, setCoupons]   = useState([]);
  const [loading, setLoading]   = useState(true);
  const [page, setPage]         = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [modal, setModal]       = useState(null); // null | 'create' | coupon object
  const [deleting, setDeleting] = useState(null);

  useEffect(() => {
    loadCoupons(0);
  }, []);

  const loadCoupons = async (p) => {
    setLoading(true);
    try {
      const res = await adminApi.getCoupons({ page: p, size: 20 });
      const data = res.data?.data;
      setCoupons(prev => p === 0 ? (data?.content || data || []) : [...prev, ...(data?.content || data || [])]);
      setTotalPages(data?.totalPages || 1);
      setPage(p);
    } catch {
      toast.error('Failed to load coupons');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this coupon?')) return;
    setDeleting(id);
    try {
      await adminApi.deleteCoupon(id);
      toast.success('Coupon deleted');
      setCoupons(prev => prev.filter(c => (c.id || c.couponId) !== id));
    } catch {
      toast.error('Failed to delete coupon');
    } finally {
      setDeleting(null);
    }
  };

  const handleSaved = () => {
    setModal(null);
    loadCoupons(0);
  };

  return (
    <div className="space-y-5">
      {modal && (
        <CouponModal
          coupon={modal === 'create' ? null : modal}
          onClose={() => setModal(null)}
          onSaved={handleSaved}
        />
      )}

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Coupons</h2>
          <p className="text-sm text-gray-500">Manage discount codes and offers</p>
        </div>
        <button
          onClick={() => setModal('create')}
          className="flex items-center gap-2 bg-yellow-400 text-gray-900 font-bold px-4 py-2.5 rounded-xl text-sm hover:bg-yellow-500 transition-colors"
        >
          <Plus size={16} /> Create Coupon
        </button>
      </div>

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {loading && coupons.length === 0 ? (
          <div className="p-6 space-y-3">
            {[1, 2, 3, 4].map(i => (
              <div key={i} className="h-12 bg-gray-100 rounded-xl animate-pulse" />
            ))}
          </div>
        ) : coupons.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <Ticket size={40} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No coupons yet</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-5 py-3 text-left">Code</th>
                  <th className="px-5 py-3 text-left">Discount</th>
                  <th className="px-5 py-3 text-left">Min Order</th>
                  <th className="px-5 py-3 text-left">Valid Until</th>
                  <th className="px-5 py-3 text-left">Status</th>
                  <th className="px-5 py-3 text-left">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {coupons.map(coupon => {
                  const id = coupon.id || coupon.couponId;
                  const until = coupon.validUntil
                    ? new Date(coupon.validUntil).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
                    : '—';
                  return (
                    <tr key={id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3 font-black font-mono text-gray-900">{coupon.code}</td>
                      <td className="px-5 py-3 text-gray-700">
                        <CouponTypeLabel type={coupon.type} value={coupon.value} maxDiscount={coupon.maxDiscount} />
                      </td>
                      <td className="px-5 py-3 text-gray-500">
                        {coupon.minOrderAmount > 0 ? `₹${coupon.minOrderAmount}` : '—'}
                      </td>
                      <td className="px-5 py-3 text-gray-400 text-xs">{until}</td>
                      <td className="px-5 py-3">
                        <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${coupon.isActive ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                          {coupon.isActive ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-1">
                          <button
                            onClick={() => setModal(coupon)}
                            className="p-1.5 text-gray-400 hover:text-blue-500 transition-colors"
                            title="Edit"
                          >
                            <Edit2 size={15} />
                          </button>
                          <button
                            onClick={() => handleDelete(id)}
                            disabled={deleting === id}
                            className="p-1.5 text-gray-400 hover:text-red-500 transition-colors disabled:opacity-40"
                            title="Delete"
                          >
                            <Trash2 size={15} />
                          </button>
                        </div>
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
            <button
              onClick={() => loadCoupons(page + 1)}
              className="w-full border border-yellow-400 text-yellow-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-yellow-50 transition-colors"
            >
              Load More
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
