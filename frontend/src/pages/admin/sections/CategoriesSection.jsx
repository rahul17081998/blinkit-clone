import { useEffect, useState } from 'react';
import { Plus, Tag, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../../api/admin.api';
import { productApi } from '../../../api/product.api';

const EMPTY_FORM = {
  name: '',
  slug: '',
  description: '',
  imageUrl: '',
  displayOrder: 0,
};

function slugify(str) {
  return str.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '');
}

function CategoryModal({ onClose, onSaved }) {
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [saving, setSaving] = useState(false);

  const set = (key, val) =>
    setForm(prev => {
      const next = { ...prev, [key]: val };
      if (key === 'name') next.slug = slugify(val);
      return next;
    });

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.name.trim()) {
      toast.error('Category name is required');
      return;
    }
    setSaving(true);
    try {
      await adminApi.createCategory({
        ...form,
        displayOrder: parseInt(form.displayOrder) || 0,
      });
      toast.success('Category created');
      onSaved();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to create category');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 className="text-base font-bold text-gray-900">Add Category</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Name *</label>
            <input
              value={form.name}
              onChange={e => set('name', e.target.value)}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
              placeholder="Category name"
              required
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Slug</label>
            <input
              value={form.slug}
              onChange={e => set('slug', e.target.value)}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
              placeholder="auto-generated"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Description</label>
            <textarea
              value={form.description}
              onChange={e => set('description', e.target.value)}
              rows={2}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 resize-none"
              placeholder="Short description"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Image URL</label>
            <input
              value={form.imageUrl}
              onChange={e => set('imageUrl', e.target.value)}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
              placeholder="https://..."
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Display Order</label>
            <input
              type="number"
              min="0"
              value={form.displayOrder}
              onChange={e => set('displayOrder', e.target.value)}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
            />
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
              {saving ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function CategoriesSection() {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading]       = useState(true);
  const [showModal, setShowModal]   = useState(false);

  useEffect(() => {
    load();
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await productApi.getCategories();
      setCategories(res.data?.data || []);
    } catch {
      toast.error('Failed to load categories');
    } finally {
      setLoading(false);
    }
  };

  const handleSaved = () => {
    setShowModal(false);
    load();
  };

  return (
    <div className="space-y-5">
      {showModal && <CategoryModal onClose={() => setShowModal(false)} onSaved={handleSaved} />}

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Categories</h2>
          <p className="text-sm text-gray-500">{categories.length} categories total</p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 bg-yellow-400 text-gray-900 font-bold px-4 py-2.5 rounded-xl text-sm hover:bg-yellow-500 transition-colors"
        >
          <Plus size={16} /> Add Category
        </button>
      </div>

      {loading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4, 5, 6, 7, 8].map(i => (
            <div key={i} className="h-32 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : categories.length === 0 ? (
        <div className="text-center py-20 text-gray-400">
          <Tag size={40} className="mx-auto mb-3 opacity-30" />
          <p className="font-medium">No categories yet</p>
          <p className="text-sm mt-1">Create your first category to get started</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {categories.map(cat => {
            const id = cat.categoryId || cat.id;
            const isReal = cat.imageUrl &&
              cat.imageUrl.startsWith('https://') &&
              !cat.imageUrl.includes('placeholder');

            return (
              <div key={id} className="bg-white rounded-2xl p-4 shadow-sm hover:shadow-md transition-shadow flex flex-col items-center text-center gap-3">
                {isReal ? (
                  <img
                    src={cat.imageUrl}
                    alt={cat.name}
                    className="w-16 h-16 object-cover rounded-xl"
                    onError={e => e.currentTarget.style.display = 'none'}
                  />
                ) : (
                  <div className="w-16 h-16 bg-yellow-100 rounded-xl flex items-center justify-center">
                    <Tag size={24} className="text-yellow-500" />
                  </div>
                )}
                <div>
                  <p className="font-bold text-gray-900 text-sm">{cat.name}</p>
                  {cat.slug && (
                    <p className="text-xs text-gray-400 mt-0.5">/{cat.slug}</p>
                  )}
                  {cat.description && (
                    <p className="text-xs text-gray-500 mt-1 line-clamp-2">{cat.description}</p>
                  )}
                  {cat.displayOrder !== undefined && (
                    <p className="text-xs text-gray-400 mt-1">Order: {cat.displayOrder}</p>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
