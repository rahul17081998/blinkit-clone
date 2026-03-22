import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ChevronLeft, Star, Package } from 'lucide-react';
import { productApi } from '../../api/product.api';
import { reviewApi } from '../../api/review.api';
import { useAuthStore } from '../../stores/authStore';
import Header from '../../components/layout/Header';
import AddToCartButton from '../../components/product/AddToCartButton';
import FloatingCartBar from '../../components/cart/FloatingCartBar';

function hashSeed(str = '') {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash * 31 + str.charCodeAt(i)) & 0xfffffff;
  }
  return hash;
}

function getAiImageUrl(name, productId) {
  const prompt = encodeURIComponent(
    `${name}, grocery product, isolated on white background, photorealistic, high quality`
  );
  return `https://image.pollinations.ai/prompt/${prompt}?width=400&height=400&nologo=true&seed=${hashSeed(productId)}`;
}

function StarDisplay({ rating, size = 14 }) {
  return (
    <div className="flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map(s => (
        <Star key={s} size={size}
          className={s <= Math.round(rating) ? 'text-yellow-400 fill-yellow-400' : 'text-gray-200 fill-gray-200'} />
      ))}
    </div>
  );
}

export default function ProductDetailPage() {
  const { productId } = useParams();
  const { isLoggedIn } = useAuthStore();
  const [product, setProduct] = useState(null);
  const [inventory, setInventory] = useState(null);
  const [reviews, setReviews] = useState([]);
  const [reviewSummary, setReviewSummary] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const [prodRes, invRes, revRes, sumRes] = await Promise.allSettled([
          productApi.getProduct(productId),
          productApi.getInventory(productId),
          reviewApi.getProductReviews(productId, { page: 0, size: 5 }),
          reviewApi.getProductSummary(productId),
        ]);
        if (prodRes.status === 'fulfilled') setProduct(prodRes.value.data.data);
        if (invRes.status === 'fulfilled') setInventory(invRes.value.data.data);
        if (revRes.status === 'fulfilled') setReviews(revRes.value.data.data?.content || []);
        if (sumRes.status === 'fulfilled') setReviewSummary(sumRes.value.data.data);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [productId]);

  if (loading) {
    return (
      <div className="min-h-screen bg-blinkit-bg">
        <Header />
        <div className="max-w-2xl mx-auto px-4 py-6 animate-pulse space-y-4">
          <div className="aspect-square bg-gray-200 rounded-2xl" />
          <div className="h-6 bg-gray-200 rounded w-2/3" />
          <div className="h-4 bg-gray-200 rounded w-1/3" />
        </div>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="min-h-screen bg-blinkit-bg">
        <Header />
        <div className="text-center py-20 text-gray-400">
          <p className="text-4xl mb-3">😕</p>
          <p>Product not found</p>
          <Link to="/" className="text-primary font-semibold mt-2 inline-block">← Back to Home</Link>
        </div>
      </div>
    );
  }

  const isDirectUrl = product.thumbnailUrl &&
    product.thumbnailUrl.startsWith('https://') &&
    !product.thumbnailUrl.includes('source.unsplash') &&
    !product.thumbnailUrl.includes('placehold') &&
    !product.thumbnailUrl.includes('example.com');
  const imgSrc = isDirectUrl ? product.thumbnailUrl : getAiImageUrl(product.name, product.productId);
  const stockQty = inventory?.availableQty ?? 0;
  const inStock = product.isAvailable && stockQty > 0;

  return (
    <div className="min-h-screen bg-blinkit-bg">
      <Header />

      <main className="max-w-2xl mx-auto px-4 py-4 pb-24">
        {/* Back */}
        <Link to="/" className="flex items-center gap-1 text-gray-500 hover:text-gray-700 text-sm mb-3">
          <ChevronLeft size={18} /> Back
        </Link>

        {/* Product card */}
        <div className="bg-white rounded-3xl border border-gray-100 overflow-hidden mb-4">
          {/* Image */}
          <div className="relative aspect-square max-h-64 w-full bg-gray-50 flex items-center justify-center">
            {product.discountPercent > 0 && (
              <span className="absolute top-3 left-3 bg-green-500 text-white text-xs font-bold px-2 py-1 rounded-lg z-10">
                {product.discountPercent}% OFF
              </span>
            )}
            <img src={imgSrc} alt={product.name} className="w-full h-full object-cover" />
          </div>

          {/* Details */}
          <div className="p-4">
            <p className="text-xs text-gray-400 mb-1">{product.unit} · {product.categoryName}</p>
            <h1 className="text-xl font-bold text-gray-900 mb-2">{product.name}</h1>

            {/* Stock */}
            <div className="flex items-center gap-2 mb-3">
              <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${inStock ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
                {inStock ? `✅ In Stock${stockQty < 20 ? ` (${stockQty} left)` : ''}` : '❌ Out of Stock'}
              </span>
              <span className="text-xs text-gray-400">⚡ 10 min delivery</span>
            </div>

            {/* Price + Add */}
            <div className="flex items-center justify-between mb-4">
              <div>
                <span className="text-2xl font-black text-gray-900">₹{product.sellingPrice}</span>
                {product.mrp > product.sellingPrice && (
                  <span className="text-sm text-gray-400 line-through ml-2">₹{product.mrp}</span>
                )}
              </div>
              <AddToCartButton product={product} />
            </div>

            {/* Description */}
            {product.description && (
              <div className="border-t border-gray-100 pt-3">
                <h3 className="text-sm font-bold text-gray-700 mb-1">About this product</h3>
                <p className="text-sm text-gray-500 leading-relaxed">{product.description}</p>
              </div>
            )}
          </div>
        </div>

        {/* Reviews section */}
        <div className="bg-white rounded-3xl border border-gray-100 p-4">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-base font-bold text-gray-900">Ratings & Reviews</h2>
            {reviewSummary && (
              <div className="flex items-center gap-1.5">
                <StarDisplay rating={reviewSummary.averageRating} />
                <span className="text-sm font-semibold text-gray-700">
                  {reviewSummary.averageRating?.toFixed(1)}
                </span>
                <span className="text-xs text-gray-400">({reviewSummary.totalReviews})</span>
              </div>
            )}
          </div>

          {reviews.length === 0 ? (
            <div className="text-center py-6 text-gray-400">
              <Star size={28} className="mx-auto mb-2 opacity-30" />
              <p className="text-sm">No reviews yet</p>
              {isLoggedIn && (
                <p className="text-xs mt-1">Buy this product to leave a review</p>
              )}
            </div>
          ) : (
            <div className="space-y-3">
              {reviews.map(r => (
                <div key={r.reviewId} className="border-b border-gray-50 pb-3 last:border-0">
                  <div className="flex items-center gap-2 mb-1">
                    <StarDisplay rating={r.rating} size={12} />
                    <span className="text-xs text-gray-400">
                      {new Date(r.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}
                    </span>
                  </div>
                  {r.title && <p className="text-sm font-semibold text-gray-800">{r.title}</p>}
                  <p className="text-sm text-gray-600">{r.comment}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      </main>

      <FloatingCartBar />
    </div>
  );
}
