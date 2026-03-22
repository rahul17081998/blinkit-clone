import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Edit2, Trash2, ToggleLeft, ToggleRight, Search, X, Package } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../../api/admin.api';
import { productApi } from '../../../api/product.api';

const EMPTY_FORM = {
  name: '',
  slug: '',
  description: '',
  categoryId: '',
  mrp: '',
  sellingPrice: '',
  unit: '',
  thumbnailUrl: '',
  isFeatured: false,
};

function slugify(str) {
  return str.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '');
}

function ProductModal({ product, categories, onClose, onSaved }) {
  const isEdit = !!product?.productId;
  const [form, setForm] = useState(
    isEdit
      ? {
          name: product.name || '',
          slug: product.slug || '',
          description: product.description || '',
          categoryId: product.categoryId || '',
          mrp: product.mrp ?? '',
          sellingPrice: product.sellingPrice ?? '',
          unit: product.unit || '',
          thumbnailUrl: product.thumbnailUrl || '',
          isFeatured: product.isFeatured || false,
        }
      : { ...EMPTY_FORM }
  );
  const [saving, setSaving] = useState(false);

  const set = (key, val) =>
    setForm(prev => {
      const next = { ...prev, [key]: val };
      if (key === 'name') next.slug = slugify(val);
      return next;
    });

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.name.trim() || !form.categoryId || !form.mrp || !form.sellingPrice) {
      toast.error('Please fill all required fields');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        ...form,
        mrp: parseFloat(form.mrp),
        sellingPrice: parseFloat(form.sellingPrice),
        images: form.thumbnailUrl ? [form.thumbnailUrl] : [],
      };
      if (isEdit) {
        await adminApi.updateProduct(product.productId, payload);
        toast.success('Product updated');
      } else {
        await adminApi.createProduct(payload);
        toast.success('Product created');
      }
      onSaved();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to save product');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 className="text-base font-bold text-gray-900">{isEdit ? 'Edit Product' : 'Add Product'}</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="col-span-2">
              <label className="block text-xs font-semibold text-gray-600 mb-1">Name *</label>
              <input
                value={form.name}
                onChange={e => set('name', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
                placeholder="Product name"
                required
              />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-semibold text-gray-600 mb-1">Slug</label>
              <input
                value={form.slug}
                onChange={e => set('slug', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
                placeholder="auto-generated"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-semibold text-gray-600 mb-1">Description</label>
              <textarea
                value={form.description}
                onChange={e => set('description', e.target.value)}
                rows={2}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 resize-none"
                placeholder="Short description"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-semibold text-gray-600 mb-1">Category *</label>
              <select
                value={form.categoryId}
                onChange={e => set('categoryId', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 bg-white"
                required
              >
                <option value="">Select category</option>
                {categories.map(c => (
                  <option key={c.categoryId || c.id} value={c.categoryId || c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">MRP (₹) *</label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.mrp}
                onChange={e => set('mrp', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
                placeholder="0.00"
                required
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Selling Price (₹) *</label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.sellingPrice}
                onChange={e => set('sellingPrice', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
                placeholder="0.00"
                required
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Unit</label>
              <input
                value={form.unit}
                onChange={e => set('unit', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
                placeholder="e.g. 500g, 1L"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1">Thumbnail URL</label>
              <input
                value={form.thumbnailUrl}
                onChange={e => set('thumbnailUrl', e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
                placeholder="https://..."
              />
            </div>
            <div className="col-span-2 flex items-center gap-3">
              <input
                type="checkbox"
                id="isFeatured"
                checked={form.isFeatured}
                onChange={e => set('isFeatured', e.target.checked)}
                className="w-4 h-4 accent-yellow-400 rounded"
              />
              <label htmlFor="isFeatured" className="text-sm font-medium text-gray-700">
                Featured product
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

export default function ProductsSection() {
  const navigate = useNavigate();
  const [products, setProducts]     = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading]       = useState(true);
  const [page, setPage]             = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch]         = useState('');
  const [modal, setModal]           = useState(null); // null | 'add' | product object
  const [deleting, setDeleting]     = useState(null);
  const [toggling, setToggling]     = useState(null);
  const timerRef                    = useRef(null);

  useEffect(() => {
    loadCategories();
    loadProducts(0, '');
  }, []);

  const handleSearchChange = (e) => {
    const q = e.target.value;
    setSearch(q);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => loadProducts(0, q), 400);
  };

  const loadCategories = async () => {
    try {
      const res = await productApi.getCategories();
      setCategories(res.data?.data || []);
    } catch {}
  };

  const loadProducts = async (p, q) => {
    setLoading(true);
    try {
      const res = (q ?? '').trim()
        ? await productApi.searchProducts(q.trim(), { page: p, size: 20 })
        : await productApi.getProducts({ page: p, size: 20 });
      const data = res.data?.data;
      setProducts(prev => p === 0 ? (data?.content || []) : [...prev, ...(data?.content || [])]);
      setTotalPages(data?.totalPages || 1);
      setPage(p);
    } catch {
      toast.error('Failed to load products');
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (e) => {
    e.preventDefault();
    loadProducts(0, search);
  };

  const handleDelete = async (productId) => {
    if (!window.confirm('Delete this product? This cannot be undone.')) return;
    setDeleting(productId);
    try {
      await adminApi.deleteProduct(productId);
      toast.success('Product deleted');
      setProducts(prev => prev.filter(p => p.productId !== productId));
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to delete product');
    } finally {
      setDeleting(null);
    }
  };

  const handleToggle = async (productId) => {
    setToggling(productId);
    try {
      await adminApi.toggleProduct(productId);
      setProducts(prev =>
        prev.map(p => p.productId === productId ? { ...p, isAvailable: !p.isAvailable } : p)
      );
      toast.success('Product availability updated');
    } catch {
      toast.error('Failed to toggle product');
    } finally {
      setToggling(null);
    }
  };

  const handleSaved = () => {
    setModal(null);
    loadProducts(0);
  };

  return (
    <div className="space-y-5">
      {modal && (
        <ProductModal
          product={modal === 'add' ? null : modal}
          categories={categories}
          onClose={() => setModal(null)}
          onSaved={handleSaved}
        />
      )}

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Products</h2>
          <p className="text-sm text-gray-500">Manage your product catalog</p>
        </div>
        <button
          onClick={() => setModal('add')}
          className="flex items-center gap-2 bg-yellow-400 text-gray-900 font-bold px-4 py-2.5 rounded-xl text-sm hover:bg-yellow-500 transition-colors"
        >
          <Plus size={16} /> Add Product
        </button>
      </div>

      {/* Search */}
      <form onSubmit={handleSearch} className="flex gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            value={search}
            onChange={handleSearchChange}
            placeholder="Search products..."
            className="w-full pl-9 pr-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
          />
        </div>
        <button
          type="submit"
          className="bg-gray-900 text-white font-semibold px-4 py-2.5 rounded-xl text-sm hover:bg-gray-700 transition-colors"
        >
          Search
        </button>
        {search && (
          <button
            type="button"
            onClick={() => { clearTimeout(timerRef.current); setSearch(''); loadProducts(0, ''); }}
            className="border border-gray-200 text-gray-500 px-3 py-2.5 rounded-xl text-sm hover:bg-gray-50"
          >
            <X size={15} />
          </button>
        )}
      </form>

      {/* Table */}
      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {loading && products.length === 0 ? (
          <div className="p-6 space-y-3">
            {[1, 2, 3, 4, 5].map(i => (
              <div key={i} className="h-14 bg-gray-100 rounded-xl animate-pulse" />
            ))}
          </div>
        ) : products.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <Package size={40} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No products found</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-4 py-3 text-left">Product</th>
                  <th className="px-4 py-3 text-left">Category</th>
                  <th className="px-4 py-3 text-left">MRP</th>
                  <th className="px-4 py-3 text-left">Price</th>
                  <th className="px-4 py-3 text-left">Status</th>
                  <th className="px-4 py-3 text-left">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {products.map(product => {
                  const initial = product.name ? product.name[0].toUpperCase() : 'P';
                  const isReal = product.thumbnailUrl &&
                    product.thumbnailUrl.startsWith('https://') &&
                    !product.thumbnailUrl.includes('placeholder');

                  return (
                    <tr key={product.productId} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 cursor-pointer"
                        onClick={() => navigate(`/admin/products/${product.productId}`)}>
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-xl bg-gray-100 flex-shrink-0 overflow-hidden flex items-center justify-center">
                            {isReal
                              ? <img src={product.thumbnailUrl} alt={product.name} className="w-full h-full object-cover" onError={e => e.currentTarget.style.display = 'none'} />
                              : <span className="text-lg font-bold text-gray-400">{initial}</span>
                            }
                          </div>
                          <div>
                            <p className="font-semibold text-gray-900 line-clamp-1 hover:text-yellow-600 transition-colors">{product.name}</p>
                            <p className="text-xs text-gray-400">{product.unit}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-gray-500 text-xs">
                        {categories.find(c => (c.categoryId || c.id) === product.categoryId)?.name || product.categoryId || '—'}
                      </td>
                      <td className="px-4 py-3 text-gray-500 line-through text-xs">₹{product.mrp}</td>
                      <td className="px-4 py-3 font-semibold text-gray-900">₹{product.sellingPrice}</td>
                      <td className="px-4 py-3">
                        <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${product.isAvailable ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
                          {product.isAvailable ? 'Available' : 'Unavailable'}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1">
                          <button
                            onClick={() => handleToggle(product.productId)}
                            disabled={toggling === product.productId}
                            title="Toggle availability"
                            className="p-1.5 text-gray-400 hover:text-yellow-500 transition-colors disabled:opacity-40"
                          >
                            {product.isAvailable
                              ? <ToggleRight size={18} className="text-green-500" />
                              : <ToggleLeft size={18} />
                            }
                          </button>
                          <button
                            onClick={() => setModal(product)}
                            className="p-1.5 text-gray-400 hover:text-blue-500 transition-colors"
                            title="Edit"
                          >
                            <Edit2 size={15} />
                          </button>
                          <button
                            onClick={() => handleDelete(product.productId)}
                            disabled={deleting === product.productId}
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
              onClick={() => loadProducts(page + 1)}
              className="w-full border border-yellow-400 text-yellow-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-yellow-50 transition-colors"
            >
              Load More
            </button>
          </div>
        )}
        {loading && products.length > 0 && (
          <div className="text-center py-3 text-gray-400 text-sm">Loading...</div>
        )}
      </div>
    </div>
  );
}
