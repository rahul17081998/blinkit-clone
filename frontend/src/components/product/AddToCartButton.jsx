import { Minus, Plus } from 'lucide-react';
import { useCartStore } from '../../stores/cartStore';

export default function AddToCartButton({ product, size = 'md', fullWidth = false }) {
  const qty = useCartStore(s => s.getItemQty(product.productId));
  const addItem = useCartStore(s => s.addItem);
  const removeItem = useCartStore(s => s.removeItem);

  const isSmall = size === 'sm';

  if (!product.isAvailable) {
    return (
      <span className={`text-xs font-bold text-gray-400 bg-gray-100 rounded-xl ${isSmall ? 'px-2 py-1' : 'px-3 py-2'}`}>
        Unavailable
      </span>
    );
  }

  if (qty === 0) {
    return (
      <button
        onClick={(e) => { e.preventDefault(); e.stopPropagation(); addItem(product); }}
        className={`bg-white border-2 border-primary text-primary font-bold rounded-lg hover:bg-primary hover:text-dark transition-all active:scale-95
          ${fullWidth ? 'w-full py-1 text-xs' : ''}
          ${!fullWidth && isSmall ? 'px-2 py-0.5 text-[10px] flex-shrink-0' : ''}
          ${!fullWidth && !isSmall ? 'px-4 py-1.5 text-sm' : ''}`}
      >
        + ADD
      </button>
    );
  }

  return (
    <div
      className={`flex items-center bg-primary rounded-lg overflow-hidden
        ${fullWidth ? 'w-full justify-between' : 'flex-shrink-0'}
        ${isSmall ? 'gap-0.5' : 'gap-2'}`}
      onClick={(e) => { e.preventDefault(); e.stopPropagation(); }}
    >
      <button
        onClick={() => removeItem(product.productId)}
        className={`text-dark font-bold hover:bg-yellow-400 transition-colors
          ${fullWidth ? 'px-3 py-1' : isSmall ? 'px-1.5 py-0.5' : 'px-3 py-1.5'}`}
      >
        <Minus size={isSmall ? 12 : 14} />
      </button>
      <span className={`font-bold text-dark min-w-[1rem] text-center
        ${isSmall ? 'text-xs' : 'text-sm'}`}>
        {qty}
      </span>
      <button
        onClick={() => addItem(product)}
        className={`text-dark font-bold hover:bg-yellow-400 transition-colors
          ${fullWidth ? 'px-3 py-1' : isSmall ? 'px-1.5 py-0.5' : 'px-3 py-1.5'}`}
      >
        <Plus size={isSmall ? 12 : 14} />
      </button>
    </div>
  );
}
