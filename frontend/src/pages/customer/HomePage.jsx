import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight, Tag } from 'lucide-react';
import { productApi } from '../../api/product.api';
import Header from '../../components/layout/Header';
import ProductGrid from '../../components/product/ProductGrid';
import CategoryPill from '../../components/product/CategoryPill';
import FloatingCartBar from '../../components/cart/FloatingCartBar';

export default function HomePage() {
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [coupons, setCoupons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [bannerIdx, setBannerIdx] = useState(0);

  useEffect(() => {
    const load = async () => {
      try {
        const [catRes, prodRes, couponRes] = await Promise.all([
          productApi.getCategories(),
          productApi.getProducts({ page: 0, size: 20, sortBy: 'createdAt', sortDir: 'desc' }),
          productApi.getActiveCoupons(),
        ]);
        setCategories(catRes.data.data.filter(c => !c.parentCategoryId));
        setProducts(prodRes.data.data.content);
        setCoupons(couponRes.data.data?.slice(0, 3) || []);
      } catch (err) {
        console.error('HomePage load error:', err);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  // Auto-advance banner
  useEffect(() => {
    if (coupons.length < 2) return;
    const t = setInterval(() => setBannerIdx(i => (i + 1) % coupons.length), 3500);
    return () => clearInterval(t);
  }, [coupons.length]);

  const coupon = coupons[bannerIdx];

  return (
    <div className="min-h-screen bg-blinkit-bg">
      <Header />

      <main className="max-w-7xl mx-auto px-4 py-4 pb-24 space-y-6">

        {/* Hero banner */}
        {coupons.length > 0 && (
          <div className="rounded-2xl bg-gradient-to-r from-primary to-yellow-300 p-5 flex items-center justify-between transition-all">
            <div>
              <p className="text-xs font-semibold text-dark/60 uppercase tracking-wide mb-1">
                Limited offer
              </p>
              <h2 className="text-xl font-black text-dark mb-1">
                {coupon.type === 'FLAT' ? `FLAT ₹${coupon.value} OFF` : `${coupon.value}% OFF`}
              </h2>
              <p className="text-sm text-dark/70">
                Use code: <span className="font-bold">{coupon.code}</span>
                {coupon.minOrderAmount > 0 && ` · Min ₹${coupon.minOrderAmount}`}
              </p>
            </div>
            <div className="flex flex-col items-center gap-1">
              <div className="bg-dark/10 rounded-xl p-3">
                <Tag size={28} className="text-dark" />
              </div>
              {coupons.length > 1 && (
                <div className="flex gap-1 mt-1">
                  {coupons.map((_, i) => (
                    <button key={i} onClick={() => setBannerIdx(i)}
                      className={`w-1.5 h-1.5 rounded-full transition-colors ${i === bannerIdx ? 'bg-dark' : 'bg-dark/30'}`} />
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Delivery badge */}
        <div className="flex items-center gap-2 text-sm font-semibold text-gray-700">
          <span className="text-primary text-lg">⚡</span>
          Delivery in 10 minutes
        </div>

        {/* Categories */}
        {categories.length > 0 && (
          <section>
            <h2 className="text-base font-bold text-gray-900 mb-3">Shop by Category</h2>
            <div className="flex gap-3 overflow-x-auto pb-2 scrollbar-hide">
              {categories.map(c => <CategoryPill key={c.categoryId} category={c} />)}
            </div>
          </section>
        )}

        {/* Best Sellers */}
        <section>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-base font-bold text-gray-900">Best Sellers</h2>
            <Link to="/category/all" className="text-sm text-primary font-semibold flex items-center gap-0.5 hover:underline">
              See All <ChevronRight size={14} />
            </Link>
          </div>
          <ProductGrid products={products.slice(0, 8)} loading={loading} />
        </section>

        {/* Per-category sections */}
        {!loading && categories.slice(0, 3).map(cat => {
          const catProducts = products.filter(p => p.categorySlug === cat.slug);
          if (!catProducts.length) return null;
          return (
            <section key={cat.categoryId}>
              <div className="flex items-center justify-between mb-3">
                <h2 className="text-base font-bold text-gray-900">{cat.name}</h2>
                <Link to={`/category/${cat.slug}`}
                  className="text-sm text-primary font-semibold flex items-center gap-0.5 hover:underline">
                  See All <ChevronRight size={14} />
                </Link>
              </div>
              <ProductGrid products={catProducts.slice(0, 4)} />
            </section>
          );
        })}
      </main>

      <FloatingCartBar />
    </div>
  );
}
