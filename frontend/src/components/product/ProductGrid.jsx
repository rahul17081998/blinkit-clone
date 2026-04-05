import ProductCard from './ProductCard';

export default function ProductGrid({ products, loading, cols = 'default' }) {
  const gridClass = cols === 'wide'
    ? 'grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 gap-2 md:gap-3'
    : 'grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 gap-2 md:gap-3';

  if (loading) {
    return (
      <div className={gridClass}>
        {Array(8).fill(0).map((_, i) => (
          <div key={i} className="bg-white rounded-2xl border border-gray-100 p-3 animate-pulse">
            <div className="aspect-square bg-gray-100 rounded-xl mb-3" />
            <div className="h-3 bg-gray-100 rounded mb-1 w-1/2" />
            <div className="h-4 bg-gray-100 rounded mb-2" />
            <div className="h-6 bg-gray-100 rounded w-2/3" />
          </div>
        ))}
      </div>
    );
  }

  if (!products?.length) {
    return (
      <div className="text-center py-16 text-gray-400">
        <p className="text-4xl mb-3">🔍</p>
        <p className="font-medium">No products found</p>
      </div>
    );
  }

  return (
    <div className={gridClass}>
      {products.map(p => <ProductCard key={p.productId} product={p} />)}
    </div>
  );
}
