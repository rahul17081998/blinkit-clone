import { useEffect, useState } from 'react';
import { Warehouse, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../../api/admin.api';
import { productApi } from '../../../api/product.api';

function StockStatusBadge({ qty, threshold }) {
  if (qty === 0)          return <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-red-100 text-red-600">OUT OF STOCK</span>;
  if (qty <= threshold)   return <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-orange-100 text-orange-600">LOW STOCK</span>;
  return                         <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-green-100 text-green-600">IN STOCK</span>;
}

function rowBg(qty, threshold) {
  if (qty === 0)        return 'bg-red-50';
  if (qty <= threshold) return 'bg-orange-50';
  return '';
}

function UpdateStockModal({ item, productName, onClose, onUpdated }) {
  const [quantityToAdd, setQuantityToAdd] = useState('');
  const [reason, setReason]              = useState('');
  const [saving, setSaving]             = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const delta = parseInt(quantityToAdd);
    if (!quantityToAdd || isNaN(delta) || delta === 0) {
      toast.error('Enter a non-zero quantity');
      return;
    }
    setSaving(true);
    try {
      await adminApi.updateStock(item.productId, delta, reason.trim() || 'Manual stock update');
      toast.success(`Stock ${delta > 0 ? 'increased' : 'decreased'} by ${Math.abs(delta)}`);
      onUpdated();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to update stock');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-sm shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 className="text-base font-bold text-gray-900">Update Stock</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div className="bg-gray-50 rounded-xl p-3">
            <p className="text-xs text-gray-500">Product</p>
            <p className="text-sm font-semibold text-gray-900">{productName}</p>
            <p className="text-xs text-gray-500 mt-1">Available: <span className="font-bold text-gray-700">{item.availableQty ?? 0}</span> · Reserved: <span className="font-bold text-gray-700">{item.reservedQty ?? 0}</span></p>
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Quantity to Add *</label>
            <input
              type="number"
              value={quantityToAdd}
              onChange={e => setQuantityToAdd(e.target.value)}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
              placeholder="Enter quantity (can be negative to deduct)"
              required
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Reason</label>
            <input
              value={reason}
              onChange={e => setReason(e.target.value)}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
              placeholder="e.g. Restocked, Damaged goods..."
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
              {saving ? 'Updating...' : 'Update'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function InventorySection() {
  const [inventory, setInventory]   = useState([]);
  const [productMap, setProductMap] = useState({});
  const [loading, setLoading]       = useState(true);
  const [page, setPage]             = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [selectedItem, setSelectedItem] = useState(null);

  useEffect(() => {
    loadInventory(0);
  }, []);

  const loadInventory = async (p) => {
    setLoading(true);
    try {
      const res = await adminApi.getInventory({ page: p, size: 50 });
      const data = res.data?.data;
      const items = data?.content || data || [];
      setInventory(prev => p === 0 ? items : [...prev, ...items]);
      setTotalPages(data?.totalPages || 1);
      setPage(p);

      // fetch product names for any new productIds
      const ids = [...new Set(items.map(i => i.productId).filter(Boolean))];
      if (ids.length > 0) {
        const fetches = ids.map(id =>
          productApi.getProduct(id).then(r => ({ id, name: r.data?.data?.name })).catch(() => ({ id, name: null }))
        );
        const results = await Promise.all(fetches);
        setProductMap(prev => {
          const next = { ...prev };
          results.forEach(({ id, name }) => { if (name) next[id] = name; });
          return next;
        });
      }
    } catch {
      toast.error('Failed to load inventory');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdated = () => {
    setSelectedItem(null);
    loadInventory(0);
  };

  return (
    <div className="space-y-5">
      {selectedItem && (
        <UpdateStockModal
          item={selectedItem}
          productName={productMap[selectedItem.productId] || selectedItem.productId}
          onClose={() => setSelectedItem(null)}
          onUpdated={handleUpdated}
        />
      )}

      <div>
        <h2 className="text-xl font-bold text-gray-900">Inventory</h2>
        <p className="text-sm text-gray-500">Monitor and update product stock levels</p>
      </div>

      {/* Legend */}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs font-medium">
        <span className="flex items-center gap-1.5 whitespace-nowrap"><span className="w-3 h-3 rounded-full bg-green-400 flex-shrink-0 inline-block" />OK — Above threshold</span>
        <span className="flex items-center gap-1.5 whitespace-nowrap"><span className="w-3 h-3 rounded-full bg-orange-400 flex-shrink-0 inline-block" />LOW — Below threshold</span>
        <span className="flex items-center gap-1.5 whitespace-nowrap"><span className="w-3 h-3 rounded-full bg-red-400 flex-shrink-0 inline-block" />OUT — Out of stock</span>
      </div>

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {loading && inventory.length === 0 ? (
          <div className="p-6 space-y-3">
            {[1, 2, 3, 4, 5].map(i => (
              <div key={i} className="h-12 bg-gray-100 rounded-xl animate-pulse" />
            ))}
          </div>
        ) : inventory.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <Warehouse size={40} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No inventory data</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-5 py-3 text-left">Product</th>
                  <th className="px-5 py-3 text-left">Available</th>
                  <th className="px-5 py-3 text-left">Reserved</th>
                  <th className="px-5 py-3 text-left">Total</th>
                  <th className="px-5 py-3 text-left">Threshold</th>
                  <th className="px-5 py-3 text-left">Status</th>
                  <th className="px-5 py-3 text-left">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {inventory.map((item, idx) => {
                  const availableQty  = item.availableQty  ?? 0;
                  const reservedQty   = item.reservedQty   ?? 0;
                  const totalQty      = item.totalQty      ?? (availableQty + reservedQty);
                  const threshold     = item.lowStockThreshold ?? 10;
                  return (
                    <tr key={item.productId || idx} className={`transition-colors ${rowBg(availableQty, threshold)}`}>
                      <td className="px-5 py-3">
                        <p className="font-semibold text-gray-900 line-clamp-1">
                          {productMap[item.productId] || item.productName || item.productId}
                        </p>
                        <p className="text-xs text-gray-400 font-mono">{item.productId?.slice(0, 10)}...</p>
                      </td>
                      <td className="px-5 py-3 font-bold text-gray-900">{availableQty}</td>
                      <td className="px-5 py-3 text-orange-500 font-semibold">{reservedQty}</td>
                      <td className="px-5 py-3 text-gray-500">{totalQty}</td>
                      <td className="px-5 py-3 text-gray-500">{threshold}</td>
                      <td className="px-5 py-3">
                        <StockStatusBadge qty={availableQty} threshold={threshold} />
                      </td>
                      <td className="px-5 py-3">
                        <button
                          onClick={() => setSelectedItem(item)}
                          className="text-xs font-semibold text-blue-600 hover:text-blue-800 underline"
                        >
                          Update Stock
                        </button>
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
              onClick={() => loadInventory(page + 1)}
              className="w-full border border-yellow-400 text-yellow-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-yellow-50 transition-colors"
            >
              Load More
            </button>
          </div>
        )}
        {loading && inventory.length > 0 && (
          <div className="text-center py-3 text-gray-400 text-sm">Loading...</div>
        )}
      </div>
    </div>
  );
}
