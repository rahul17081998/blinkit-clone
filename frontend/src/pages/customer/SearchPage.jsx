import { useEffect, useState, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Search, ChevronLeft } from 'lucide-react';
import { productApi } from '../../api/product.api';
import Header from '../../components/layout/Header';
import ProductGrid from '../../components/product/ProductGrid';
import FloatingCartBar from '../../components/cart/FloatingCartBar';

function useDebounce(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialQ = searchParams.get('q') || '';
  const [query, setQuery] = useState(initialQ);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const debouncedQuery = useDebounce(query, 300);

  useEffect(() => {
    if (!debouncedQuery.trim()) {
      setProducts([]);
      setSearched(false);
      return;
    }
    setSearchParams({ q: debouncedQuery });
    const load = async () => {
      setLoading(true);
      setSearched(true);
      try {
        const res = await productApi.searchProducts(debouncedQuery, { page: 0, size: 20 });
        setProducts(res.data.data.content);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [debouncedQuery]);

  return (
    <div className="min-h-screen bg-blinkit-bg">
      <Header />
      <main className="max-w-7xl mx-auto px-4 py-4 pb-24">

        {/* Search input */}
        <div className="flex items-center gap-3 mb-4">
          <Link to="/" className="text-gray-400 hover:text-gray-600 flex-shrink-0">
            <ChevronLeft size={22} />
          </Link>
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              autoFocus
              type="text"
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder='Search "atta, dal, rice, eggs..."'
              className="w-full pl-9 pr-4 py-3 bg-white border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
        </div>

        {/* Results */}
        {!query.trim() ? (
          <div className="text-center py-16 text-gray-400">
            <Search size={40} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">Search for products</p>
            <p className="text-sm mt-1">Try "milk", "bread", "amul"</p>
          </div>
        ) : (
          <>
            {searched && !loading && (
              <p className="text-sm text-gray-500 mb-3">
                {products.length > 0
                  ? `${products.length} result${products.length > 1 ? 's' : ''} for "${debouncedQuery}"`
                  : `No results for "${debouncedQuery}"`}
              </p>
            )}
            <ProductGrid products={products} loading={loading} cols="wide" />
          </>
        )}
      </main>
      <FloatingCartBar />
    </div>
  );
}
