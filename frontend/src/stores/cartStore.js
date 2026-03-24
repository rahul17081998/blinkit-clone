import { create } from 'zustand';
import { cartApi } from '../api/cart.api';

export const useCartStore = create((set, get) => ({
  cartId: null,
  items: [],        // [{ productId, productName, thumbnailUrl, sellingPrice, mrp, unit, quantity }]
  couponCode: null,         // discount coupon (FLAT / PERCENT)
  discount: 0,
  deliveryCouponCode: null, // free-delivery coupon (FREE_DELIVERY)
  freeDelivery: false,

  // ── Derived ─────────────────────────────────────────────────────────
  getItemCount: () => get().items.reduce((sum, i) => sum + i.quantity, 0),
  getItemQty:   (productId) => get().items.find(i => i.productId === productId)?.quantity || 0,
  getSubtotal:  () => get().items.reduce((sum, i) => sum + i.sellingPrice * i.quantity, 0),
  getDeliveryFee: () => {
    if (get().freeDelivery) return 0;
    const subtotal = get().getSubtotal();
    return subtotal >= 199 ? 0 : 20;
  },
  getTotal: () => {
    const subtotal = get().getSubtotal();
    const fee = get().getDeliveryFee();
    return subtotal - get().discount + fee;
  },

  // ── Local + backend operations ───────────────────────────────────────
  addItem: async (product) => {
    const items = get().items;
    const existing = items.find(i => i.productId === product.productId);
    const newQty = existing ? existing.quantity + 1 : 1;

    // Optimistic local update
    if (existing) {
      set({ items: items.map(i =>
        i.productId === product.productId ? { ...i, quantity: newQty } : i
      )});
    } else {
      set({ items: [...items, {
        productId:    product.productId,
        productName:  product.name,
        thumbnailUrl: product.thumbnailUrl,
        sellingPrice: product.sellingPrice,
        mrp:          product.mrp,
        unit:         product.unit,
        quantity:     1,
      }]});
    }

    // Backend sync
    try {
      if (newQty === 1) {
        await cartApi.addItem(product.productId, 1);
      } else {
        await cartApi.updateItem(product.productId, newQty);
      }
    } catch (e) {
      console.error('Cart add sync error:', e);
    }
  },

  removeItem: async (productId) => {
    const items = get().items;
    const existing = items.find(i => i.productId === productId);
    if (!existing) return;
    const newQty = existing.quantity - 1;

    // Optimistic local update
    if (newQty === 0) {
      set({ items: items.filter(i => i.productId !== productId) });
    } else {
      set({ items: items.map(i =>
        i.productId === productId ? { ...i, quantity: newQty } : i
      )});
    }

    // Backend sync
    try {
      if (newQty === 0) {
        await cartApi.removeItem(productId);
      } else {
        await cartApi.updateItem(productId, newQty);
      }
    } catch (e) {
      console.error('Cart remove sync error:', e);
    }
  },

  deleteItem: async (productId) => {
    set({ items: get().items.filter(i => i.productId !== productId) });
    try { await cartApi.removeItem(productId); } catch (e) { console.error(e); }
  },

  clearCart: async () => {
    set({ cartId: null, items: [], couponCode: null, discount: 0, deliveryCouponCode: null, freeDelivery: false });
    try { await cartApi.clearCart(); } catch (e) { console.error(e); }
  },

  applyPromo: async (couponCode) => {
    try {
      const res = await cartApi.applyPromo(couponCode);
      if (!res.data.success) {
        return { success: false, message: res.data.message || 'Invalid coupon' };
      }
      const cart = res.data.data;
      if (!cart) return { success: false, message: 'Invalid coupon' };
      set({
        couponCode:         cart.couponCode         || null,
        discount:           cart.couponDiscount      || 0,
        deliveryCouponCode: cart.deliveryCouponCode  || null,
        freeDelivery:       cart.freeDelivery        || false,
      });
      return { success: true };
    } catch (e) {
      if (e.isAuthError) return { success: false, message: '' };
      const msg = e.response?.data?.message || e.message || 'Invalid coupon';
      return { success: false, message: msg };
    }
  },

  removePromo: async () => {
    try {
      await cartApi.removePromo();
      set({ couponCode: null, discount: 0 });
    } catch (e) { console.error(e); }
  },

  removeDeliveryPromo: async () => {
    try {
      await cartApi.removeDeliveryPromo();
      set({ deliveryCouponCode: null, freeDelivery: false });
    } catch (e) { console.error(e); }
  },

  // ── Load from backend (called on login / page refresh) ───────────────
  loadFromBackend: async () => {
    try {
      const res = await cartApi.getCart();
      const cart = res.data.data;
      if (!cart) return;
      set({
        items:      (cart.items || []).map(i => ({
          productId:    i.productId,
          productName:  i.name,          // backend field: name
          thumbnailUrl: i.imageUrl,      // backend field: imageUrl
          sellingPrice: i.unitPrice,     // backend field: unitPrice
          mrp:          i.mrp,
          unit:         i.unit,
          quantity:     i.quantity,
          isAvailable:  i.isAvailable,
        })),
        couponCode:         cart.couponCode        || null,
        discount:           cart.couponDiscount    || 0,
        deliveryCouponCode: cart.deliveryCouponCode || null,
        freeDelivery:       cart.freeDelivery      || false,
      });
    } catch (e) {
      console.error('Cart load error:', e);
    }
  },

  // Hydrate from backend data directly (for order success reset)
  setCart: (cartData) => set({
    cartId:     cartData.cartId,
    items:      cartData.items || [],
    couponCode: cartData.couponCode || null,
    discount:   cartData.discount   || 0,
    deliveryFee: cartData.deliveryFee || 0,
  }),
}));
