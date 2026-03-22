import { Link } from 'react-router-dom';
import { ShoppingCart, ChevronRight } from 'lucide-react';
import { useCartStore } from '../../stores/cartStore';

export default function FloatingCartBar() {
  const items = useCartStore(s => s.items);
  const getItemCount = useCartStore(s => s.getItemCount);
  const getSubtotal = useCartStore(s => s.getSubtotal);

  const count = getItemCount();
  if (count === 0) return null;

  return (
    <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-40 w-full max-w-sm px-4">
      <Link
        to="/cart"
        className="flex items-center justify-between bg-dark text-white rounded-2xl px-4 py-3 shadow-xl hover:bg-gray-800 transition-colors"
      >
        <div className="flex items-center gap-2">
          <div className="bg-primary rounded-lg p-1">
            <ShoppingCart size={16} className="text-dark" />
          </div>
          <span className="text-sm font-semibold">{count} item{count > 1 ? 's' : ''}</span>
        </div>
        <div className="flex items-center gap-1">
          <span className="text-sm font-bold">₹{getSubtotal().toFixed(0)}</span>
          <ChevronRight size={16} className="text-gray-400" />
          <span className="text-sm font-semibold">View Cart</span>
        </div>
      </Link>
    </div>
  );
}
