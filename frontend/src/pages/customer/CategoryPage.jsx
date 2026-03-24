import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ChevronLeft, SlidersHorizontal } from 'lucide-react';
import { productApi } from '../../api/product.api';
import Header from '../../components/layout/Header';
import ProductGrid from '../../components/product/ProductGrid';
import FloatingCartBar from '../../components/cart/FloatingCartBar';

const SORT_OPTIONS = [
  { label: 'Relevance', value: '' },
  { label: 'Price: Low to High', value: 'sellingPrice,asc' },
  { label: 'Price: High to Low', value: 'sellingPrice,desc' },
  { label: 'Discount', value: 'discountPercent,desc' },
];

export default function CategoryPage() {
  const { slug } = useParams();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sort, setSort] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [categoryName, setCategoryName] = useState('');

  useEffect(() => {
    setPage(0);
    setProducts([]);
  }, [slug, sort]);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        // Build sort params
        const params = { page, size: 12 };
        if (sort) {
          const [sortBy, sortDir] = sort.split(',');
          params.sortBy = sortBy;
          params.sortDir = sortDir || 'asc';
        }

        let res;
        if (slug === 'all') {
          res = await productApi.getProducts(params);
          setCategoryName('All Products');
        } else {
          res = await productApi.getProductsByCategory(slug, params);
          if (res.data.data?.content?.[0]?.categoryName) {
            setCategoryName(res.data.data.content[0].categoryName);
          }
        }

        const data = res.data.data;
        setProducts(prev => page === 0 ? data.content : [...prev, ...data.content]);
        setTotalPages(data.totalPages);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [slug, sort, page]);

  return (
    <div className="min-h-screen bg-blinkit-bg">
      <Header />
      <main className="max-w-7xl mx-auto px-4 py-4 pb-24">

        {/* Top bar */}
        <div className="flex items-center gap-3 mb-4">
          <Link to="/" className="text-gray-400 hover:text-gray-600">
            <ChevronLeft size={22} />
          </Link>
          <h1 className="text-lg font-bold text-gray-900 flex-1">
            {categoryName || slug}
          </h1>
        </div>

        {/* Sort bar */}
        <div className="flex items-center gap-2 mb-4 overflow-x-auto pb-1 scrollbar-hide">
          <SlidersHorizontal size={15} className="text-gray-400 flex-shrink-0" />
          {SORT_OPTIONS.map(opt => (
            <button
              key={opt.value}
              onClick={() => setSort(opt.value)}
              className={`flex-shrink-0 text-xs font-semibold px-3 py-1.5 rounded-full border transition-all
                ${sort === opt.value
                  ? 'bg-primary border-primary text-dark'
                  : 'bg-white border-gray-200 text-gray-600 hover:border-primary'}`}
            >
              {opt.label}
            </button>
          ))}
        </div>

        <ProductGrid products={products} loading={loading && page === 0} cols="wide" />

        {/* Load more */}
        {page < totalPages - 1 && !loading && (
          <div className="text-center mt-6">
            <button
              onClick={() => setPage(p => p + 1)}
              className="border border-primary text-primary font-semibold px-6 py-2.5 rounded-xl hover:bg-primary hover:text-dark transition-all text-sm"
            >
              Load More
            </button>
          </div>
        )}
        {loading && page > 0 && (
          <div className="text-center mt-4 text-gray-400 text-sm">Loading...</div>
        )}
      </main>
      <FloatingCartBar />
    </div>
  );
}
