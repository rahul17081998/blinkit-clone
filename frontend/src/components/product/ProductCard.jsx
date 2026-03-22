import { useState } from 'react';
import { Link } from 'react-router-dom';
import AddToCartButton from './AddToCartButton';

// Category background gradient (shown while AI image loads or on failure)
const CATEGORY_BG = {
  'dairy-eggs':        'from-blue-50 to-blue-100',
  'fruits-vegetables': 'from-green-50 to-green-100',
  'snacks':            'from-orange-50 to-orange-100',
  'cold-drinks':       'from-cyan-50 to-cyan-100',
  'beverages':         'from-cyan-50 to-cyan-100',
  'breakfast':         'from-yellow-50 to-yellow-100',
  'cleaning':          'from-purple-50 to-purple-100',
  'personal-care':     'from-pink-50 to-pink-100',
  'bakery':            'from-amber-50 to-amber-100',
  'staples':           'from-lime-50 to-lime-100',
};

const CATEGORY_EMOJI = {
  'dairy-eggs':        '🥛',
  'fruits-vegetables': '🥦',
  'snacks':            '🍿',
  'cold-drinks':       '🥤',
  'beverages':         '🧃',
  'breakfast':         '🥣',
  'cleaning':          '🧹',
  'personal-care':     '🧴',
  'bakery':            '🍞',
  'staples':           '🌾',
};


export default function ProductCard({ product }) {
  const {
    productId, name, unit, sellingPrice, mrp, discountPercent,
    thumbnailUrl, isAvailable, categorySlug,
  } = product;

  const bg    = CATEGORY_BG[categorySlug]    || 'from-gray-50 to-gray-100';
  const emoji = CATEGORY_EMOJI[categorySlug] || '🛒';

  // Show stored URL unless it's a known broken placeholder
  const isRealImg = thumbnailUrl &&
    thumbnailUrl.startsWith('https://') &&
    !thumbnailUrl.includes('source.unsplash') &&
    !thumbnailUrl.includes('placehold') &&
    !thumbnailUrl.includes('example.com');

  const imgSrc = isRealImg ? thumbnailUrl : null;

  const [imgFailed, setImgFailed] = useState(false);

  return (
    <Link
      to={`/product/${productId}`}
      className="bg-white rounded-2xl border border-gray-100 p-3 flex flex-col gap-2 hover:shadow-md transition-shadow group"
    >
      {/* Image area */}
      <div className="relative">
        {discountPercent > 0 && (
          <span className="absolute top-1 left-1 bg-green-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-md z-10">
            {discountPercent}% OFF
          </span>
        )}
        {!isAvailable && (
          <div className="absolute inset-0 bg-white/70 rounded-xl flex items-center justify-center z-10">
            <span className="text-xs font-semibold text-gray-500">Out of Stock</span>
          </div>
        )}
        <div className={`aspect-square w-full bg-gradient-to-br ${bg} rounded-xl overflow-hidden flex items-center justify-center`}>
          {imgSrc && !imgFailed ? (
            <img
              src={imgSrc}
              alt={name}
              className="w-full h-full object-cover"
              onError={() => setImgFailed(true)}
            />
          ) : (
            <span className="text-5xl">{emoji}</span>
          )}
        </div>
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0">
        <p className="text-xs text-gray-400 mb-0.5">{unit}</p>
        <p className="text-sm font-semibold text-gray-900 leading-tight line-clamp-2 group-hover:text-primary transition-colors">
          {name}
        </p>
      </div>

      {/* Price + Add button */}
      <div className="flex items-center justify-between gap-1 mt-auto">
        <div>
          <p className="text-sm font-bold text-gray-900">₹{sellingPrice}</p>
          {mrp > sellingPrice && (
            <p className="text-xs text-gray-400 line-through">₹{mrp}</p>
          )}
        </div>
        <AddToCartButton product={product} size="sm" />
      </div>
    </Link>
  );
}
