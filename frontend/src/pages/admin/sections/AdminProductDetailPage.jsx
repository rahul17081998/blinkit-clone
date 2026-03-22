import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ChevronLeft, Edit2, Trash2, ToggleLeft, ToggleRight,
  Package, Tag, Star, Layers, Save, X,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../../api/admin.api';
import { productApi } from '../../../api/product.api';

// ── inline field editor ────────────────────────────────────────────────────
function EditableField({ value, onSave, type = 'text', prefix, suffix }) {
  const [editing, setEditing] = useState(false);
  const [val, setVal] = useState(value ?? '');
  const [saving, setSaving] = useState(false);

  // keep local val in sync if parent updates product
  useState(() => { setVal(value ?? ''); });

  const handleSave = async () => {
    setSaving(true);
    try { await onSave(val); setEditing(false); }
    catch { /* parent shows toast */ }
    finally { setSaving(false); }
  };

  if (editing) {
    return (
      <div className="flex items-center gap-2">
        {prefix && <span className="text-sm text-gray-500">{prefix}</span>}
        <input
          type={type}
          value={val}
          onChange={e => setVal(e.target.value)}
          className="flex-1 border border-yellow-400 rounded-lg px-2 py-1 text-sm focus:outline-none"
          autoFocus
        />
        {suffix && <span className="text-sm text-gray-500 whitespace-nowrap">{suffix}</span>}
        <button onClick={handleSave} disabled={saving}
          className="p-1 text-green-600 hover:text-green-700 disabled:opacity-50">
          <Save size={15} />
        </button>
        <button onClick={() => { setEditing(false); setVal(value ?? ''); }}
          className="p-1 text-gray-400 hover:text-gray-600">
          <X size={15} />
        </button>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-between group">
      <span className="text-sm font-semibold text-gray-900">
        {prefix && <span className="text-gray-400 font-normal mr-0.5">{prefix}</span>}
        {value ?? <span className="text-gray-300 italic font-normal">—</span>}
        {suffix && <span className="text-green-600 ml-1">{suffix}</span>}
      </span>
      <button onClick={() => { setEditing(true); setVal(value ?? ''); }}
        className="opacity-0 group-hover:opacity-100 p-1 text-gray-400 hover:text-blue-500 transition-all">
        <Edit2 size={13} />
      </button>
    </div>
  );
}

// ── main page ──────────────────────────────────────────────────────────────
export default function AdminProductDetailPage() {
  const { productId } = useParams();
  const navigate = useNavigate();

  const [product, setProduct] = useState(null);
  const [inventory, setInventory] = useState(null);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [toggling, setToggling] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [stockDelta, setStockDelta] = useState('');   // +/- amount to add/remove
  const [stockReason, setStockReason] = useState('');
  const [savingStock, setSavingStock] = useState(false);
  const [editModal, setEditModal] = useState(false);

  useEffect(() => { load(); }, [productId]);

  const load = async () => {
    setLoading(true);
    try {
      const [prodRes, catRes] = await Promise.all([
        productApi.getProduct(productId),
        productApi.getCategories(),
      ]);
      const p = prodRes.data?.data;
      setProduct(p);
      setCategories(catRes.data?.data || []);

      // fetch inventory separately (may not exist yet)
      try {
        const invRes = await productApi.getInventory(productId);
        setInventory(invRes.data?.data ?? null);
      } catch {
        setInventory(null);
      }
    } catch {
      toast.error('Product not found');
      navigate(-1);
    } finally {
      setLoading(false);
    }
  };

  const handleToggle = async () => {
    setToggling(true);
    try {
      const res = await adminApi.toggleProduct(productId);
      setProduct(res.data?.data ?? { ...product, isAvailable: !product.isAvailable });
      toast.success('Availability updated');
    } catch {
      toast.error('Failed to toggle availability');
    } finally {
      setToggling(false);
    }
  };

  const handleDelete = async () => {
    if (!window.confirm(`Delete "${product.name}"? This cannot be undone.`)) return;
    setDeleting(true);
    try {
      await adminApi.deleteProduct(productId);
      toast.success('Product deleted');
      navigate(-1);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to delete');
      setDeleting(false);
    }
  };

  const handleFieldSave = async (field, value) => {
    try {
      const payload = { [field]: field === 'mrp' || field === 'sellingPrice' ? parseFloat(value) : value };
      const res = await adminApi.updateProduct(productId, payload);
      setProduct(res.data?.data ?? { ...product, ...payload });
      toast.success('Updated');
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Update failed');
      throw err;
    }
  };

  const handleSaveStock = async () => {
    const delta = parseInt(stockDelta, 10);
    if (isNaN(delta) || delta === 0) { toast.error('Enter a non-zero quantity'); return; }
    setSavingStock(true);
    try {
      const res = await adminApi.updateStock(productId, delta, stockReason || undefined);
      setInventory(res.data?.data);
      setStockDelta('');
      setStockReason('');
      toast.success(`Stock ${delta > 0 ? 'increased' : 'decreased'} by ${Math.abs(delta)}`);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to update stock');
    } finally {
      setSavingStock(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-4 animate-pulse max-w-4xl">
        <div className="h-8 w-32 bg-gray-200 rounded-xl" />
        <div className="h-48 bg-white rounded-2xl" />
        <div className="grid grid-cols-2 gap-4">
          <div className="h-40 bg-white rounded-2xl" />
          <div className="h-40 bg-white rounded-2xl" />
        </div>
      </div>
    );
  }

  if (!product) return null;

  const isReal = product.thumbnailUrl?.startsWith('https://') && !product.thumbnailUrl.includes('placeholder');
  const availableQty = inventory?.availableQty ?? null;
  const stockColor = availableQty === null ? 'text-gray-400' : availableQty === 0 ? 'text-red-600' : availableQty < 10 ? 'text-orange-500' : 'text-green-600';

  return (
    <div className="space-y-4 max-w-4xl">
      {/* Back */}
      <button onClick={() => navigate(-1)}
        className="flex items-center gap-1 text-gray-500 hover:text-gray-700 text-sm">
        <ChevronLeft size={18} /> Back to Products
      </button>

      {/* Hero card */}
      <div className="bg-white rounded-2xl p-5 flex flex-col sm:flex-row gap-5">
        {/* Image */}
        <div className="w-full sm:w-36 h-36 rounded-2xl bg-gray-50 flex-shrink-0 overflow-hidden flex items-center justify-center border border-gray-100">
          {isReal
            ? <img src={product.thumbnailUrl} alt={product.name} className="w-full h-full object-cover" onError={e => e.currentTarget.style.display = 'none'} />
            : <Package size={40} className="text-gray-200" />}
        </div>

        {/* Core info */}
        <div className="flex-1 min-w-0 space-y-1">
          <div className="flex items-start justify-between gap-2">
            <h2 className="text-lg font-black text-gray-900 leading-tight">{product.name}</h2>
            <span className={`flex-shrink-0 text-xs font-bold px-2.5 py-1 rounded-full ${product.isAvailable ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
              {product.isAvailable ? 'Available' : 'Unavailable'}
            </span>
          </div>
          <p className="text-xs text-gray-400">{product.unit} · {product.categoryName}</p>
          {product.description && <p className="text-sm text-gray-500 mt-1 line-clamp-2">{product.description}</p>}

          {/* Ratings */}
          <div className="flex items-center gap-1 pt-1">
            <Star size={13} className="text-yellow-400 fill-yellow-400" />
            <span className="text-xs font-semibold text-gray-700">{product.avgRating?.toFixed(1) ?? '0.0'}</span>
            <span className="text-xs text-gray-400">({product.reviewCount ?? 0} reviews)</span>
          </div>

          {/* Action buttons */}
          <div className="flex flex-wrap gap-2 pt-3">
            <button
              onClick={handleToggle}
              disabled={toggling}
              className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-bold transition-colors disabled:opacity-50 ${
                product.isAvailable
                  ? 'bg-red-50 text-red-600 hover:bg-red-100'
                  : 'bg-green-50 text-green-700 hover:bg-green-100'
              }`}
            >
              {product.isAvailable ? <ToggleLeft size={14} /> : <ToggleRight size={14} />}
              {product.isAvailable ? 'Mark Unavailable' : 'Mark Available'}
            </button>
            <button
              onClick={() => setEditModal(true)}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-bold bg-blue-50 text-blue-600 hover:bg-blue-100 transition-colors"
            >
              <Edit2 size={14} /> Edit Product
            </button>
            <button
              onClick={handleDelete}
              disabled={deleting}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-bold bg-red-50 text-red-600 hover:bg-red-100 transition-colors disabled:opacity-50"
            >
              <Trash2 size={14} /> {deleting ? 'Deleting...' : 'Delete'}
            </button>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {/* Pricing card */}
        <div className="bg-white rounded-2xl p-5 space-y-3">
          <h3 className="text-sm font-bold text-gray-900 flex items-center gap-2">
            <Tag size={15} className="text-yellow-500" /> Pricing
          </h3>
          <div className="space-y-2">
            <div>
              <p className="text-xs text-gray-400 mb-0.5">MRP</p>
              <EditableField
                value={`${product.mrp}`}
                prefix="₹"
                type="number"
                onSave={v => handleFieldSave('mrp', v)}
              />
            </div>
            <div>
              <p className="text-xs text-gray-400 mb-0.5">Selling Price</p>
              <EditableField
                value={`${product.sellingPrice}`}
                prefix="₹"
                type="number"
                onSave={v => handleFieldSave('sellingPrice', v)}
              />
            </div>
            <div className="pt-1 border-t border-gray-50">
              <p className="text-xs text-gray-400 mb-0.5">Discount %</p>
              <EditableField
                value={`${product.discountPercent ?? 0}`}
                prefix=""
                type="number"
                suffix="% off"
                onSave={async (v) => {
                  const pct = Math.min(100, Math.max(0, parseFloat(v) || 0));
                  const newSelling = parseFloat((product.mrp * (1 - pct / 100)).toFixed(2));
                  await handleFieldSave('sellingPrice', newSelling);
                }}
              /></div>
          </div>
        </div>

        {/* Inventory card */}
        <div className="bg-white rounded-2xl p-5 space-y-3">
          <h3 className="text-sm font-bold text-gray-900 flex items-center gap-2">
            <Layers size={15} className="text-blue-500" /> Inventory
          </h3>

          {inventory === null ? (
            <p className="text-sm text-gray-400 italic">No stock record found for this product.</p>
          ) : (
            <>
              {/* Stock numbers */}
              <div className="grid grid-cols-3 gap-2 text-center">
                <div className="bg-gray-50 rounded-xl p-2">
                  <p className={`text-xl font-black ${stockColor}`}>{availableQty}</p>
                  <p className="text-xs text-gray-400 mt-0.5">Available</p>
                </div>
                <div className="bg-gray-50 rounded-xl p-2">
                  <p className="text-xl font-black text-orange-500">{inventory.reservedQty ?? 0}</p>
                  <p className="text-xs text-gray-400 mt-0.5">Reserved</p>
                </div>
                <div className="bg-gray-50 rounded-xl p-2">
                  <p className="text-xl font-black text-gray-700">{inventory.totalQty ?? 0}</p>
                  <p className="text-xs text-gray-400 mt-0.5">Total</p>
                </div>
              </div>

              {/* Adjust stock */}
              <div className="pt-2 border-t border-gray-50">
                <p className="text-xs font-semibold text-gray-600 mb-2">Adjust Stock</p>
                <div className="flex gap-2 mb-2">
                  <button
                    onClick={() => setStockDelta(d => d === '' ? '-1' : String(parseInt(d || 0) - 1))}
                    className="w-8 h-8 rounded-lg bg-red-50 text-red-600 font-bold text-lg hover:bg-red-100 flex items-center justify-center"
                  >−</button>
                  <input
                    type="number"
                    value={stockDelta}
                    onChange={e => setStockDelta(e.target.value)}
                    placeholder="e.g. +50 or -10"
                    className="flex-1 border border-gray-200 rounded-xl px-3 py-1.5 text-sm text-center font-semibold focus:outline-none focus:ring-2 focus:ring-yellow-400"
                  />
                  <button
                    onClick={() => setStockDelta(d => d === '' ? '1' : String(parseInt(d || 0) + 1))}
                    className="w-8 h-8 rounded-lg bg-green-50 text-green-600 font-bold text-lg hover:bg-green-100 flex items-center justify-center"
                  >+</button>
                </div>
                <input
                  type="text"
                  value={stockReason}
                  onChange={e => setStockReason(e.target.value)}
                  placeholder="Reason (optional)"
                  className="w-full border border-gray-200 rounded-xl px-3 py-1.5 text-xs mb-2 focus:outline-none focus:ring-2 focus:ring-yellow-400"
                />
                <button
                  onClick={handleSaveStock}
                  disabled={savingStock || stockDelta === '' || parseInt(stockDelta) === 0}
                  className="w-full bg-yellow-400 text-gray-900 font-bold py-2 rounded-xl text-sm hover:bg-yellow-500 disabled:opacity-50 transition-colors"
                >
                  {savingStock ? 'Saving...' : stockDelta && parseInt(stockDelta) < 0
                    ? `Remove ${Math.abs(parseInt(stockDelta))} units`
                    : stockDelta
                      ? `Add ${parseInt(stockDelta)} units`
                      : 'Update Stock'}
                </button>
              </div>
            </>
          )}

          <div className="grid grid-cols-2 gap-2 pt-2 border-t border-gray-50 text-xs text-gray-500">
            <div><span className="text-gray-400">Display unit:</span> {product.unit || '—'}</div>
            <div><span className="text-gray-400">Weight:</span> {product.weightInGrams ? `${product.weightInGrams}g` : '—'}</div>
            <div><span className="text-gray-400">Featured:</span> {product.isFeatured ? 'Yes' : 'No'}</div>
            <div><span className="text-gray-400">Reviews:</span> {product.reviewCount ?? 0}</div>
          </div>
        </div>
      </div>

      {/* Extra details */}
      <div className="bg-white rounded-2xl p-5 space-y-3">
        <h3 className="text-sm font-bold text-gray-900">Additional Details</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-3 text-sm">
          <div>
            <p className="text-xs text-gray-400 mb-0.5">Category</p>
            <p className="text-gray-700">{product.categoryName || '—'}</p>
          </div>
          <div>
            <p className="text-xs text-gray-400 mb-0.5">Slug</p>
            <p className="text-gray-700 font-mono text-xs">{product.slug || '—'}</p>
          </div>
          <div>
            <p className="text-xs text-gray-400 mb-0.5">Country of Origin</p>
            <p className="text-gray-700">{product.countryOfOrigin || '—'}</p>
          </div>
          <div>
            <p className="text-xs text-gray-400 mb-0.5">Expiry Info</p>
            <p className="text-gray-700">{product.expiryInfo || '—'}</p>
          </div>
          {product.nutritionInfo && (
            <div className="sm:col-span-2">
              <p className="text-xs text-gray-400 mb-0.5">Nutrition Info</p>
              <p className="text-gray-700">{product.nutritionInfo}</p>
            </div>
          )}
          {product.tags?.length > 0 && (
            <div className="sm:col-span-2">
              <p className="text-xs text-gray-400 mb-1">Tags</p>
              <div className="flex flex-wrap gap-1">
                {product.tags.map(tag => (
                  <span key={tag} className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full">{tag}</span>
                ))}
              </div>
            </div>
          )}
          {product.images?.length > 1 && (
            <div className="sm:col-span-2">
              <p className="text-xs text-gray-400 mb-2">All Images</p>
              <div className="flex gap-2 flex-wrap">
                {product.images.map((img, i) => (
                  <div key={i} className="w-16 h-16 rounded-xl overflow-hidden bg-gray-50 border border-gray-100">
                    <img src={img} alt="" className="w-full h-full object-cover" onError={e => e.currentTarget.style.display = 'none'} />
                  </div>
                ))}
              </div>
            </div>
          )}
          <div>
            <p className="text-xs text-gray-400 mb-0.5">Created</p>
            <p className="text-gray-700 text-xs">{product.createdAt ? new Date(product.createdAt).toLocaleString('en-IN') : '—'}</p>
          </div>
          <div>
            <p className="text-xs text-gray-400 mb-0.5">Last Updated</p>
            <p className="text-gray-700 text-xs">{product.updatedAt ? new Date(product.updatedAt).toLocaleString('en-IN') : '—'}</p>
          </div>
        </div>
      </div>

      {/* Edit modal */}
      {editModal && (
        <ProductEditModal
          product={product}
          categories={categories}
          onClose={() => setEditModal(false)}
          onSaved={() => { setEditModal(false); load(); }}
        />
      )}
    </div>
  );
}

// ── embedded edit modal ────────────────────────────────────────────────────
function ProductEditModal({ product, categories, onClose, onSaved }) {
  const [form, setForm] = useState({
    name:            product.name || '',
    slug:            product.slug || '',
    description:     product.description || '',
    categoryId:      product.categoryId || '',
    mrp:             product.mrp ?? '',
    sellingPrice:    product.sellingPrice ?? '',
    discountPercent: product.discountPercent ?? 0,
    unit:            product.unit || '',
    thumbnailUrl:    product.thumbnailUrl || '',
    isFeatured:      product.isFeatured || false,
    countryOfOrigin: product.countryOfOrigin || '',
    expiryInfo:      product.expiryInfo || '',
    nutritionInfo:   product.nutritionInfo || '',
    weightInGrams:   product.weightInGrams ?? '',
  });
  const [saving, setSaving] = useState(false);

  const set = (key, val) => setForm(prev => {
    const next = { ...prev, [key]: val };
    // keep discount/selling price in sync
    if (key === 'mrp' || key === 'discountPercent') {
      const mrp = parseFloat(key === 'mrp' ? val : next.mrp) || 0;
      const pct = parseFloat(key === 'discountPercent' ? val : next.discountPercent) || 0;
      next.sellingPrice = parseFloat((mrp * (1 - pct / 100)).toFixed(2));
    }
    if (key === 'sellingPrice') {
      const mrp = parseFloat(next.mrp) || 0;
      const sp  = parseFloat(val) || 0;
      next.discountPercent = mrp > 0 ? Math.floor(((mrp - sp) / mrp) * 100) : 0;
    }
    return next;
  });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      const payload = {
        ...form,
        mrp:          parseFloat(form.mrp),
        sellingPrice: parseFloat(form.sellingPrice),
        weightInGrams: form.weightInGrams ? parseFloat(form.weightInGrams) : null,
        images: form.thumbnailUrl ? [form.thumbnailUrl] : [],
      };
      await adminApi.updateProduct(product.productId, payload);
      toast.success('Product updated');
      onSaved();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to update');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 sticky top-0 bg-white z-10">
          <h3 className="text-base font-bold text-gray-900">Edit Product</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Name *</label>
            <input value={form.name} onChange={e => set('name', e.target.value)} required
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Category *</label>
            <select value={form.categoryId} onChange={e => set('categoryId', e.target.value)} required
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 bg-white">
              <option value="">Select category</option>
              {categories.map(c => (
                <option key={c.categoryId || c.id} value={c.categoryId || c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">MRP (₹) *</label>
              <input type="number" min="0" step="0.01" value={form.mrp} onChange={e => set('mrp', e.target.value)} required
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Discount %</label>
              <input type="number" min="0" max="100" step="1" value={form.discountPercent} onChange={e => set('discountPercent', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Selling Price (₹)</label>
              <input type="number" min="0" step="0.01" value={form.sellingPrice} onChange={e => set('sellingPrice', e.target.value)} required
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 bg-yellow-50" />
            </div>
          </div>
          <p className="text-xs text-gray-400 -mt-2">Change Discount % → Selling Price auto-updates, or edit Selling Price directly.</p>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Unit</label>
              <input value={form.unit} onChange={e => set('unit', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" placeholder="e.g. 500g, 1kg" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Weight (grams)</label>
              <input type="number" min="0" value={form.weightInGrams} onChange={e => set('weightInGrams', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Description</label>
            <textarea value={form.description} onChange={e => set('description', e.target.value)} rows={2}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 resize-none" />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Image URL</label>
            <input value={form.thumbnailUrl} onChange={e => set('thumbnailUrl', e.target.value)}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" placeholder="https://..." />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Country of Origin</label>
              <input value={form.countryOfOrigin} onChange={e => set('countryOfOrigin', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Expiry Info</label>
              <input value={form.expiryInfo} onChange={e => set('expiryInfo', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Nutrition Info</label>
            <input value={form.nutritionInfo} onChange={e => set('nutritionInfo', e.target.value)}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />
          </div>
          <div className="flex items-center gap-2">
            <input type="checkbox" id="featured" checked={form.isFeatured} onChange={e => set('isFeatured', e.target.checked)}
              className="w-4 h-4 accent-yellow-400" />
            <label htmlFor="featured" className="text-sm font-medium text-gray-700">Mark as Featured</label>
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 border border-gray-200 text-gray-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-gray-50">
              Cancel
            </button>
            <button type="submit" disabled={saving}
              className="flex-1 bg-yellow-400 text-gray-900 font-bold py-2.5 rounded-xl text-sm hover:bg-yellow-500 disabled:opacity-60">
              {saving ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
