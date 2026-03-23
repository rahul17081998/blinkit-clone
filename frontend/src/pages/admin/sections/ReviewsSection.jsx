import { useEffect, useState } from 'react';
import { Star, Trash2, Search } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../../api/admin.api';

function StarDisplay({ rating }) {
  return (
    <div className="flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map(s => (
        <Star key={s} size={12}
          className={s <= Math.round(rating) ? 'text-yellow-400 fill-yellow-400' : 'text-gray-200 fill-gray-200'} />
      ))}
    </div>
  );
}

export default function ReviewsSection() {
  const [reviews, setReviews] = useState([]);
  const [page, setPage]       = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal]     = useState(0);
  const [loading, setLoading] = useState(true);
  const [search, setSearch]   = useState('');
  const [deletingId, setDeletingId] = useState(null);

  const load = async (p = 0) => {
    setLoading(true);
    try {
      const res = await adminApi.getAllReviews({ page: p, size: 20 });
      const d = res.data.data;
      setReviews(d?.content || []);
      setTotalPages(d?.totalPages || 1);
      setTotal(d?.totalElements || 0);
      setPage(p);
    } catch {
      toast.error('Failed to load reviews');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(0); }, []);

  const handleDelete = async (reviewId) => {
    if (!window.confirm('Delete this review?')) return;
    setDeletingId(reviewId);
    try {
      await adminApi.deleteReview(reviewId);
      setReviews(prev => prev.filter(r => r.reviewId !== reviewId));
      setTotal(t => t - 1);
      toast.success('Review deleted');
    } catch {
      toast.error('Failed to delete review');
    } finally {
      setDeletingId(null);
    }
  };

  const filtered = search.trim()
    ? reviews.filter(r =>
        r.productName?.toLowerCase().includes(search.toLowerCase()) ||
        r.userId?.toLowerCase().includes(search.toLowerCase()) ||
        r.comment?.toLowerCase().includes(search.toLowerCase())
      )
    : reviews;

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Reviews</h1>
          <p className="text-sm text-gray-500 mt-0.5">{total} total reviews</p>
        </div>
      </div>

      {/* Search */}
      <div className="relative max-w-xs">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="Search product, user, comment…"
          className="pl-9 pr-4 py-2 text-sm border border-gray-200 rounded-xl w-full focus:outline-none focus:ring-2 focus:ring-yellow-400"
        />
      </div>

      {/* Table */}
      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {loading ? (
          <div className="p-6 space-y-3">
            {[1,2,3,4,5].map(i => <div key={i} className="h-14 bg-gray-100 rounded-xl animate-pulse" />)}
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <Star size={36} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No reviews found</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Product</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">User</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Rating</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Review</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Date</th>
                <th className="px-5 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filtered.map(r => (
                <tr key={r.reviewId} className="hover:bg-gray-50 transition-colors">
                  <td className="px-5 py-3.5 font-medium text-gray-800 max-w-[140px] truncate">
                    {r.productName || r.productId}
                  </td>
                  <td className="px-5 py-3.5 text-gray-500 font-mono text-xs max-w-[120px] truncate">
                    {r.userId}
                  </td>
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-1.5">
                      <StarDisplay rating={r.rating} />
                      <span className="text-xs font-semibold text-gray-700">{r.rating}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3.5 max-w-[260px]">
                    {r.title && <p className="font-semibold text-gray-800 truncate text-xs">{r.title}</p>}
                    <p className="text-gray-500 truncate text-xs">{r.comment}</p>
                  </td>
                  <td className="px-5 py-3.5 text-gray-400 text-xs whitespace-nowrap">
                    {r.createdAt ? new Date(r.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'}
                  </td>
                  <td className="px-5 py-3.5 text-right">
                    <button
                      onClick={() => handleDelete(r.reviewId)}
                      disabled={deletingId === r.reviewId}
                      className="p-1.5 text-red-400 hover:text-red-600 hover:bg-red-50 rounded-lg disabled:opacity-40 transition-colors"
                    >
                      <Trash2 size={15} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && !loading && (
        <div className="flex items-center justify-between">
          <button onClick={() => load(page - 1)} disabled={page === 0}
            className="px-4 py-2 text-sm border border-gray-200 rounded-xl disabled:opacity-40 hover:bg-gray-50">
            Previous
          </button>
          <span className="text-sm text-gray-500">Page {page + 1} of {totalPages}</span>
          <button onClick={() => load(page + 1)} disabled={page >= totalPages - 1}
            className="px-4 py-2 text-sm border border-gray-200 rounded-xl disabled:opacity-40 hover:bg-gray-50">
            Next
          </button>
        </div>
      )}
    </div>
  );
}
