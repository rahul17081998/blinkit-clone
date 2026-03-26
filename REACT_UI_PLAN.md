# Blinkit Clone — React UI Plan

> **Stack:** React 18 + Vite + React Router v6 + Zustand + Axios + Tailwind CSS
> **Architecture:** Customer App + Admin Panel (same Vite project, role-gated routes)
> **Backend:** Spring Boot API Gateway at `http://localhost:8080`
> **Updated:** 2026-03-22 — reflects actual backend (wallet payment, not Razorpay; 13 services built)

---

## TABLE OF CONTENTS

1. [Tech Stack Decisions](#1-tech-stack)
2. [Folder Structure](#2-folder-structure)
3. [State Management (Zustand Stores)](#3-state-management)
4. [Routing Map](#4-routing-map)
5. [API Integration Map](#5-api-integration-map)
6. [Screens — Customer App](#6-screens--customer-app)
7. [Screens — Admin Panel](#7-screens--admin-panel)
8. [Screens — Delivery Agent App](#8-screens--delivery-agent-app)
9. [Shared Components](#9-shared-components)
10. [Auth Flow (Token Handling)](#10-auth-flow)
11. [Development Stages](#11-development-stages)

---

## 1. TECH STACK

| Layer | Choice | Reason |
|---|---|---|
| Bundler | Vite 5 | Fast HMR, fast build |
| Framework | React 18 | Concurrent features, hooks |
| Routing | React Router v6 | Nested routes, data loaders |
| State | Zustand | Simple, no boilerplate |
| HTTP | Axios | Interceptors for token refresh |
| Styling | Tailwind CSS v3 | Utility-first, fast to prototype |
| UI Components | shadcn/ui | Accessible, unstyled → Tailwind-friendly |
| Forms | React Hook Form + Zod | Type-safe validation |
| Charts | Recharts | Admin dashboard charts |
| Toast | react-hot-toast | Lightweight notifications |
| Icons | lucide-react | Consistent icon set |
| Date | date-fns | Lightweight date formatting |

**Color Palette (Blinkit brand):**
```
Primary:    #F8C200   Blinkit Yellow
Dark:       #0C0F0A   Near Black
Success:    #22C55E   Green
Error:      #EF4444   Red
Warning:    #F59E0B   Amber
Background: #F5F5F5   Light Grey
Card:       #FFFFFF   White
Text:       #1A1A1A   Dark
Muted:      #6B7280   Grey
```

---

## 2. FOLDER STRUCTURE

```
frontend/
├── public/
│   └── blinkit-logo.svg
├── src/
│   ├── api/                        ← Axios instances + typed API functions
│   │   ├── axios.js                ← Axios instance (base URL, interceptors)
│   │   ├── auth.api.js
│   │   ├── user.api.js
│   │   ├── product.api.js
│   │   ├── cart.api.js
│   │   ├── order.api.js
│   │   ├── payment.api.js
│   │   ├── delivery.api.js
│   │   ├── coupon.api.js
│   │   ├── review.api.js
│   │   └── admin.api.js
│   │
│   ├── stores/                     ← Zustand stores
│   │   ├── authStore.js            ← userId, role, accessToken, isLoggedIn
│   │   ├── cartStore.js            ← cartItems, totalAmount, itemCount
│   │   └── uiStore.js              ← loading, toast, modals
│   │
│   ├── components/                 ← Reusable components
│   │   ├── layout/
│   │   │   ├── Header.jsx          ← Logo, search, cart icon, user menu
│   │   │   ├── Footer.jsx
│   │   │   ├── Sidebar.jsx         ← Admin sidebar
│   │   │   └── BottomNav.jsx       ← Mobile: Home/Search/Orders/Profile
│   │   ├── product/
│   │   │   ├── ProductCard.jsx     ← Image, name, price, ADD button
│   │   │   ├── ProductGrid.jsx     ← Responsive grid of ProductCard
│   │   │   ├── AddToCartButton.jsx ← +/- inline qty control
│   │   │   └── RatingStars.jsx     ← 1–5 star display/input
│   │   ├── cart/
│   │   │   ├── CartItem.jsx
│   │   │   ├── BillSummary.jsx     ← Items total, delivery fee, discount, total
│   │   │   └── FloatingCartBar.jsx ← Sticky bottom bar when cart non-empty
│   │   ├── order/
│   │   │   ├── OrderCard.jsx       ← Order summary card for list
│   │   │   ├── OrderStatusBadge.jsx
│   │   │   └── OrderTimeline.jsx   ← Step-by-step delivery status
│   │   ├── ui/                     ← shadcn-based primitives
│   │   │   ├── Button.jsx
│   │   │   ├── Input.jsx
│   │   │   ├── Modal.jsx
│   │   │   ├── Badge.jsx
│   │   │   ├── Spinner.jsx
│   │   │   └── Toast.jsx
│   │   └── auth/
│   │       ├── ProtectedRoute.jsx  ← Redirects to /login if not authenticated
│   │       └── AdminRoute.jsx      ← Redirects if role !== ADMIN
│   │
│   ├── pages/
│   │   ├── auth/
│   │   │   ├── LoginPage.jsx
│   │   │   ├── SignupPage.jsx
│   │   │   ├── VerifyOtpPage.jsx
│   │   │   ├── ForgotPasswordPage.jsx
│   │   │   └── ResetPasswordPage.jsx
│   │   ├── customer/
│   │   │   ├── HomePage.jsx
│   │   │   ├── CategoryPage.jsx
│   │   │   ├── SearchPage.jsx
│   │   │   ├── ProductDetailPage.jsx
│   │   │   ├── CartPage.jsx
│   │   │   ├── CheckoutPage.jsx    ← Address select + wallet pay
│   │   │   ├── OrderConfirmPage.jsx
│   │   │   ├── OrderTrackingPage.jsx
│   │   │   ├── OrdersPage.jsx
│   │   │   ├── ProfilePage.jsx
│   │   │   ├── AddressesPage.jsx
│   │   │   └── WalletPage.jsx      ← Balance + transaction history
│   │   ├── admin/
│   │   │   ├── AdminDashboardPage.jsx
│   │   │   ├── AdminProductsPage.jsx
│   │   │   ├── AdminOrdersPage.jsx
│   │   │   ├── AdminUsersPage.jsx
│   │   │   ├── AdminInventoryPage.jsx
│   │   │   ├── AdminDeliveryPage.jsx
│   │   │   ├── AdminCouponsPage.jsx
│   │   │   └── AdminWalletsPage.jsx
│   │   └── delivery/
│   │       ├── AgentDashboard.jsx
│   │       ├── AgentProfilePage.jsx
│   │       └── AgentTasksPage.jsx
│   │
│   ├── hooks/
│   │   ├── useAuth.js              ← Login, logout, token refresh
│   │   ├── useCart.js              ← Add, remove, update, sync with backend
│   │   ├── useProducts.js          ← Paginated fetch, search, category filter
│   │   └── useOrderTracking.js     ← Polls GET /delivery/track/{orderId}
│   │
│   ├── utils/
│   │   ├── formatCurrency.js       ← ₹ formatting
│   │   ├── formatDate.js
│   │   └── orderStatusConfig.js    ← Status → label + color + icon
│   │
│   ├── App.jsx                     ← Route definitions
│   └── main.jsx                    ← ReactDOM.createRoot
│
├── index.html
├── vite.config.js
├── tailwind.config.js
└── package.json
```

---

## 3. STATE MANAGEMENT

### authStore (Zustand + persist to localStorage)
```js
{
  userId: null,
  email: null,
  role: null,           // 'CUSTOMER' | 'ADMIN' | 'DELIVERY_AGENT'
  accessToken: null,    // in memory only (NOT localStorage)
  refreshToken: null,   // localStorage
  isLoggedIn: false,
  // actions
  login(userData),
  logout(),
  setAccessToken(token),
}
```
> **Security note:** `accessToken` lives in memory (Zustand, not persisted). `refreshToken` in `localStorage`. On page reload, auto-refresh call runs to get new access token.

### cartStore (Zustand, synced with backend on login)
```js
{
  items: [],            // [{productId, name, imageUrl, unitPrice, quantity, totalPrice}]
  couponCode: null,
  couponDiscount: 0,
  itemsTotal: 0,
  deliveryFee: 0,
  totalAmount: 0,
  freeDelivery: false,
  // actions
  setCart(cartDto),
  clearCart(),
  getItemCount(),
}
```

### uiStore
```js
{
  isLoading: false,
  searchQuery: '',
  // actions
  setLoading(bool),
  setSearchQuery(q),
}
```

---

## 4. ROUTING MAP

```
/                           → HomePage (public)
/login                      → LoginPage (public)
/signup                     → SignupPage (public)
/verify-otp                 → VerifyOtpPage (public)
/forgot-password            → ForgotPasswordPage (public)
/reset-password/:token      → ResetPasswordPage (public)

/search                     → SearchPage (public)
/category/:slug             → CategoryPage (public)
/product/:productId         → ProductDetailPage (public)

/cart                       → CartPage (requires login)
/checkout                   → CheckoutPage (requires login)
/order-confirm/:orderId     → OrderConfirmPage (requires login)
/track/:orderId             → OrderTrackingPage (requires login)
/orders                     → OrdersPage (requires login)
/profile                    → ProfilePage (requires login)
/profile/addresses          → AddressesPage (requires login)
/wallet                     → WalletPage (requires login)

/admin                      → AdminDashboardPage (requires ADMIN)
/admin/products             → AdminProductsPage
/admin/orders               → AdminOrdersPage
/admin/users                → AdminUsersPage
/admin/inventory            → AdminInventoryPage
/admin/delivery             → AdminDeliveryPage
/admin/coupons              → AdminCouponsPage
/admin/wallets              → AdminWalletsPage

/agent                      → AgentDashboard (requires DELIVERY_AGENT)
/agent/tasks                → AgentTasksPage
/agent/profile              → AgentProfilePage
```

---

## 5. API INTEGRATION MAP

| Page | API Calls |
|---|---|
| LoginPage | `POST /api/auth/login` |
| SignupPage | `POST /api/auth/signup` |
| VerifyOtpPage | `GET /api/auth/verify?email=X&otp=Y` |
| ForgotPasswordPage | `POST /api/auth/forgot-password` |
| ResetPasswordPage | `GET /api/auth/reset-password/validate/:token`, `POST /api/auth/reset-password/:token` |
| HomePage | `GET /api/products`, `GET /api/categories`, `GET /api/coupons/active` |
| CategoryPage | `GET /api/products?category=X&page=Y` |
| SearchPage | `GET /api/products?keyword=X` |
| ProductDetailPage | `GET /api/products/:productId`, `GET /api/inventory/:productId`, `GET /api/reviews/product/:productId`, `POST /api/reviews` |
| CartPage | `GET /api/cart`, `POST/PUT/DELETE /api/cart/items`, `POST/DELETE /api/cart/promo`, `DELETE /api/cart` |
| CheckoutPage | `GET /api/users/addresses`, `POST /api/orders` |
| OrderConfirmPage | `GET /api/orders/:orderId`, `GET /api/payments/wallet` |
| OrderTrackingPage | `GET /api/delivery/track/:orderId` (polling every 10s) |
| OrdersPage | `GET /api/orders` |
| ProfilePage | `GET/PUT /api/users/profile`, `DELETE /api/auth/account` |
| AddressesPage | `GET/POST/PUT/DELETE /api/users/addresses`, `PUT /api/users/addresses/:id/default` |
| WalletPage | `GET /api/payments/wallet`, `GET /api/payments/history` |
| AdminDashboardPage | `GET /api/orders/admin`, `GET /api/payments/admin/transactions` |
| AdminProductsPage | `GET/POST/PUT/DELETE /api/products/admin`, `GET/PUT /api/inventory/admin` |
| AdminOrdersPage | `GET /api/orders/admin`, `PUT /api/orders/admin/:orderId/status` |
| AdminInventoryPage | `GET /api/inventory/admin`, `PUT /api/inventory/admin/:productId` |
| AdminDeliveryPage | `GET /api/delivery/admin/tasks`, `GET /api/delivery/admin/partners`, `PUT /api/delivery/admin/tasks/:id/assign` |
| AdminCouponsPage | `GET/POST/PUT/DELETE /api/coupons/admin` |
| AdminWalletsPage | `GET /api/payments/admin/wallets`, `POST /api/payments/admin/wallets/:userId/topup` |
| AgentDashboard | `GET /api/delivery/tasks/mine`, `PUT /api/delivery/partners/me/availability` |
| AgentProfilePage | `GET/PUT /api/delivery/partners/me`, `PUT /api/delivery/partners/me/location` |

---

## 6. SCREENS — CUSTOMER APP

---

### SCREEN 0a: LOGIN PAGE

```
┌──────────────────────────────────────────────────────┐
│              🟡 BlinkIt                              │
│         Delivery in 10 minutes                       │
├──────────────────────────────────────────────────────┤
│                                                      │
│  Welcome back!                                       │
│  Sign in to continue                                 │
│                                                      │
│  Email                                               │
│  ┌────────────────────────────────────────────────┐  │
│  │  rahul@email.com                               │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  Password                                            │
│  ┌────────────────────────────────────────────────┐  │
│  │  ••••••••                            [👁 show] │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│                         [Forgot Password?]           │
│                                                      │
│  [          Login          ]                         │
│                                                      │
│  ──────── or ────────                                │
│                                                      │
│  Don't have an account?  [Sign Up]                   │
└──────────────────────────────────────────────────────┘
```

**Behavior:**
- On success → role-based redirect:
  - `CUSTOMER` → `/`
  - `ADMIN` → `/admin`
  - `DELIVERY_AGENT` → `/agent`
- Error states: "Invalid credentials" toast, field highlighting

---

### SCREEN 0b: SIGNUP PAGE

```
┌──────────────────────────────────────────────────────┐
│  ← Back         Create Account                      │
├──────────────────────────────────────────────────────┤
│  First Name    [Rahul              ]                 │
│  Last Name     [Kumar              ]                 │
│  Email         [rahul@email.com    ]                 │
│  Phone         [+91  9876543210    ]                 │
│  Password      [••••••••     👁]                    │
│  ● ≥ 8 chars  ● 1 uppercase  ● 1 number             │
│                                                      │
│  [          Create Account          ]                │
│                                                      │
│  Already have an account?  [Login]                   │
└──────────────────────────────────────────────────────┘
```

**After signup → OTP Verification:**
```
┌──────────────────────────────────────────────────────┐
│  Verify your email                                   │
│  We sent a 6-digit code to rahul@email.com           │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ┌────┐  ┌────┐  ┌────┐  ┌────┐  ┌────┐  ┌────┐    │
│  │ 1  │  │ 2  │  │ 3  │  │ 4  │  │ 5  │  │ 6  │    │
│  └────┘  └────┘  └────┘  └────┘  └────┘  └────┘    │
│                                                      │
│  [       Verify Email       ]                        │
│                                                      │
│  Didn't receive?  [Resend OTP]  (after 30s timer)    │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 0c & 0d: FORGOT / RESET PASSWORD

_(unchanged from original plan — already well designed)_

---

### SCREEN 1: HOME PAGE

```
┌──────────────────────────────────────────────────────┐
│  HEADER                                              │
│  [🟡 Logo]  [📍 Koramangala, Blore ▼]   [🛒 (3)] [👤]│
├──────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────┐  │
│  │ 🔍 Search "atta, dal, rice, eggs..."           │  │
│  └────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────┤
│  ⚡ Delivery in 10 minutes                           │
├──────────────────────────────────────────────────────┤
│  HERO BANNER (Carousel — active coupons)             │
│  ┌──────────────────────────────────────────────┐    │
│  │  🎁  FLAT ₹50 OFF  •  Use code: FLAT50       │    │
│  │  Min order ₹299              [Shop Now]       │    │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  CATEGORIES (horizontal scroll)                      │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐  →  │
│  │ 🥛   │ │ 🥦   │ │ 🍎   │ │ 🍞   │ │ 🧹   │      │
│  │Dairy │ │Veggie│ │Fruits│ │Bakery│ │Clean │      │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘      │
├──────────────────────────────────────────────────────┤
│  SECTION: "Best Sellers"                             │
│  [ProductCard] [ProductCard] [ProductCard] ...       │
├──────────────────────────────────────────────────────┤
│  SECTION: "Dairy & Eggs"  [See All →]                │
│  [ProductCard] [ProductCard] [ProductCard] ...       │
├──────────────────────────────────────────────────────┤
│  FLOATING CART BAR (shows when cart > 0 items)       │
│  ┌──────────────────────────────────────────────┐    │
│  │ 🛒 3 items │ ₹156    [View Cart & Checkout →]│    │
│  └──────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────┘
```

**Data sources:**
- Categories: `GET /api/categories`
- Products per section: `GET /api/products?page=0&size=8&sort=sellingPrice`
- Hero banners: `GET /api/coupons/active` → render first 3 as banner cards

---

### SCREEN 2: CATEGORY / PRODUCT LISTING

```
┌──────────────────────────────────────────────────────┐
│  ← Dairy & Eggs                 [🔍] [Filter ⚙]    │
├──────────────────────────────────────────────────────┤
│  SORT: [Relevance ▼] [Price ▼] [Discount ▼]         │
├──────────────────────────────────────────────────────┤
│  PRODUCT GRID (2 col mobile, 4 col desktop)          │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐     │
│  │ [7% OFF]   │  │            │  │ OUT OF     │     │
│  │  [img]     │  │  [img]     │  │ STOCK 🚫  │     │
│  │ Amul Butter│  │ Cheese     │  │ Paneer     │     │
│  │ 500g       │  │ Slice 200g │  │ 200g ₹95  │     │
│  │ ₹52 ~~₹56~~│  │ ₹135       │  │[Notify Me] │     │
│  │  [+ ADD]   │  │  [+ ADD]   │  │            │     │
│  └────────────┘  └────────────┘  └────────────┘     │
│  ... infinite scroll ...                            │
└──────────────────────────────────────────────────────┘
```

**API:** `GET /api/products?category={slug}&page=N&size=12&keyword=X`

---

### SCREEN 3: PRODUCT DETAIL PAGE

```
┌──────────────────────────────────────────────────────┐
│  ← Back                       [🔍]  [🛒 3]          │
├──────────────────────────────────────────────────────┤
│  IMAGE (single image from images[0])                 │
│  ┌────────────────────────────────────────────────┐  │
│  │              [Product Image]                   │  │
│  └────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────┤
│  Amul Full Cream Milk                                │
│  500ml | Dairy                                       │
│  ₹28  ~~₹30~~  🏷 7% OFF                            │
│  ✅ In Stock (142 units)   ⚡ 10 min delivery        │
├──────────────────────────────────────────────────────┤
│  [ 🛒  Add to Cart  ]   or   [ ➖  2  ➕ ]          │
├──────────────────────────────────────────────────────┤
│  ABOUT                                               │
│  Amul Full Cream Milk is rich in protein and fat...  │
├──────────────────────────────────────────────────────┤
│  RATINGS & REVIEWS                                   │
│  ★★★★☆  4.2  (18 reviews)                           │
│                                                      │
│  ┌──────────────────────────────────────────────┐    │
│  │ Rahul K. ★★★★★  "Very fresh, loved it!"     │    │
│  │ Mar 2026                                     │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ Ananya S. ★★★★☆  "Good but slightly pricey" │    │
│  └──────────────────────────────────────────────┘    │
│  [Write a Review]  (only shown if DELIVERED order)   │
└──────────────────────────────────────────────────────┘
```

**API calls:**
- `GET /api/products/{productId}`
- `GET /api/inventory/{productId}` (stock level)
- `GET /api/reviews/product/{productId}?page=0&size=5`
- Check if user can review: `GET /api/reviews/me` → filter for this productId

---

### SCREEN 4: CART PAGE

```
┌──────────────────────────────────────────────────────┐
│  My Cart  (3 items)                      [🗑 Clear]  │
├──────────────────────────────────────────────────────┤
│  CART ITEMS                                          │
│  ┌──────────────────────────────────────────────┐    │
│  │ [img] Amul Butter 500g        ➖  2  ➕ [🗑] │    │
│  │       ₹52/unit                         ₹104  │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ [img] Brown Bread 400g        ➖  1  ➕ [🗑] │    │
│  │       ₹35/unit                          ₹35  │    │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  PROMO CODE                                          │
│  ┌────────────────────────┐  ┌──────────┐            │
│  │  SAVE10                │  │  APPLY   │            │
│  └────────────────────────┘  └──────────┘            │
│  ✅ SAVE10 applied: -₹13.90                          │
├──────────────────────────────────────────────────────┤
│  BILL SUMMARY                                        │
│  Items Total:              ₹139                      │
│  Delivery fee:             ₹20                       │
│  Discount (SAVE10 10%):    -₹13.90                   │
│  ─────────────────────────────                       │
│  To Pay:                   ₹145.10                   │
├──────────────────────────────────────────────────────┤
│  [      Proceed to Checkout      ]                   │
└──────────────────────────────────────────────────────┘
```

**Note:** Delivery free if items ≥ ₹199 or FREE_DELIVERY coupon applied.

---

### SCREEN 5: CHECKOUT PAGE

```
┌──────────────────────────────────────────────────────┐
│  ← Checkout                                          │
├──────────────────────────────────────────────────────┤
│  1. SELECT DELIVERY ADDRESS                          │
│  ┌──────────────────────────────────────────────┐    │
│  │ ● 🏠 Home (Default)              [Edit]       │    │
│  │   B204, Green Valley, Koramangala             │    │
│  │   Bangalore - 560034                          │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ ○ 💼 Work                        [Edit]       │    │
│  │   3rd Floor, Tech Park, Whitefield            │    │
│  └──────────────────────────────────────────────┘    │
│  [+ Add New Address]                                 │
├──────────────────────────────────────────────────────┤
│  2. PAYMENT METHOD                                   │
│  ┌──────────────────────────────────────────────┐    │
│  │ 💰 Blinkit Wallet                            │    │
│  │    Available Balance: ₹9,464.00              │    │
│  │    Wallet will be debited on order placement │    │
│  └──────────────────────────────────────────────┘    │
│  (Other methods: coming soon)                        │
├──────────────────────────────────────────────────────┤
│  ORDER SUMMARY                                       │
│  3 items · ₹145.10 · Delivering to Home             │
│  ⚡ Estimated in ~10 minutes                         │
├──────────────────────────────────────────────────────┤
│  [      Place Order  ₹145.10      ]                  │
│  🔒 Debited instantly from wallet                    │
└──────────────────────────────────────────────────────┘
```

**API:** `POST /api/orders` with `{ addressId, notes }`

---

### SCREEN 6: ORDER CONFIRMATION + TRACKING

```
┌──────────────────────────────────────────────────────┐
│  ✅ Order Placed!                                    │
│  Order #BLK-20260322-0015                            │
│  ₹145.10 debited from wallet                        │
├──────────────────────────────────────────────────────┤
│  DELIVERY STATUS TIMELINE                            │
│  ✅ Payment Confirmed      10:00 AM                  │
│  ✅ Order Confirmed        10:00 AM                  │
│  🔄 Being Packed           in progress...            │
│  ○  Out for Delivery       ~10:08 AM est.            │
│  ○  Delivered              ~10:15 AM est.            │
├──────────────────────────────────────────────────────┤
│  DELIVERY PARTNER (once assigned)                    │
│  ┌──────────────────────────────────────────────┐    │
│  │ [Avatar] Rahul Agent  ⭐ 4.8  Rating: 24 del│    │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  ITEMS                                               │
│  [img] Amul Butter 500g  ×2  ₹104                   │
│  [img] Brown Bread 400g  ×1   ₹35                   │
├──────────────────────────────────────────────────────┤
│  [Cancel Order]   (only before PACKED)               │
│  [Continue Shopping]                                 │
└──────────────────────────────────────────────────────┘
```

**Polling:** `GET /api/delivery/track/{orderId}` every 10s to update timeline.

---

### SCREEN 7: ORDER HISTORY

```
┌──────────────────────────────────────────────────────┐
│  ← My Orders                                         │
├──────────────────────────────────────────────────────┤
│  TABS: [All] [Active] [Delivered] [Cancelled]        │
├──────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────┐    │
│  │ #BLK-20260322-0015   22 Mar 2026 10:00 AM   │    │
│  │ 3 items · ₹145         ✅ DELIVERED          │    │
│  │ [img][img][img]         [Rate Items →]        │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ #BLK-20260322-0014   22 Mar 2026 9:00 AM    │    │
│  │ 2 items · ₹104         🔄 OUT FOR DELIVERY   │    │
│  │ [img][img]              [Track Order →]       │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ #BLK-20260322-0008   22 Mar 2026 8:00 AM    │    │
│  │ 1 item · ₹52           ❌ CANCELLED          │    │
│  │ [img]                   [Reorder →]           │    │
│  └──────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 8: USER PROFILE

```
┌──────────────────────────────────────────────────────┐
│  My Account                                          │
├──────────────────────────────────────────────────────┤
│  👤  Rahul Kumar                                     │
│      rahul2148kumar@gmail.com                        │
│      [Edit Profile]                                  │
├──────────────────────────────────────────────────────┤
│  🛍️  My Orders                               >      │
│  📍  Saved Addresses                          >      │
│  💰  Wallet  (Balance: ₹9,464)                >      │
│  🎁  Coupons & Offers                         >      │
│  ⭐  My Reviews                               >      │
│  🔔  Notifications                            >      │
│  ❓  Help & Support                           >      │
├──────────────────────────────────────────────────────┤
│  [  🚪 Logout  ]                                     │
│  [  🗑 Delete Account  ]  (confirmation modal)       │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 9: WALLET PAGE *(new — not in original plan)*

```
┌──────────────────────────────────────────────────────┐
│  ← My Wallet                                         │
├──────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────┐    │
│  │  💰 Available Balance                        │    │
│  │                                              │    │
│  │         ₹ 9,464.00                           │    │
│  │                                              │    │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  TRANSACTION HISTORY                                 │
│  ┌──────────────────────────────────────────────┐    │
│  │ ↓ Order Debit    22 Mar 2026  -₹145.10       │    │
│  │   #BLK-20260322-0015                         │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ ↑ Refund Credit  22 Mar 2026  +₹104.00       │    │
│  │   Order Cancelled: #BLK-20260322-0008        │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ ↑ Welcome Bonus  21 Mar 2026  +₹10,000.00    │    │
│  │   New account credit                         │    │
│  └──────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 10: WRITE REVIEW *(modal on Product Detail / Order History)*

```
┌──────────────────────────────────────────────────────┐
│  Rate: Amul Butter 500g                  [× Close]   │
├──────────────────────────────────────────────────────┤
│  Your rating                                         │
│  ☆ ☆ ☆ ☆ ☆  (click to rate 1–5)                    │
│                                                      │
│  Title (optional)                                    │
│  ┌────────────────────────────────────────────────┐  │
│  │  Great butter!                                 │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  Your review                                         │
│  ┌────────────────────────────────────────────────┐  │
│  │  Very fresh and tasty. Arrived quickly!        │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  [Cancel]                     [Submit Review]        │
└──────────────────────────────────────────────────────┘
```

---

## 7. SCREENS — ADMIN PANEL

### Admin Layout
```
┌─────────────────────────────────────────────────────────┐
│  🟡 BlinkIt Admin                   👤 Admin  [Logout]   │
├───────────────┬─────────────────────────────────────────┤
│  SIDEBAR      │  CONTENT AREA                            │
│               │                                          │
│  📊 Dashboard │                                          │
│  📦 Products  │                                          │
│  📋 Orders    │                                          │
│  📦 Inventory │                                          │
│  🚚 Delivery  │                                          │
│  🎁 Coupons   │                                          │
│  💰 Wallets   │                                          │
│               │                                          │
└───────────────┴─────────────────────────────────────────┘
```

### Admin Screen A: Dashboard
```
STATS CARDS:
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Today Orders│  │Today Revenue│  │Active Orders│  │ Low Stock   │
│    14       │  │  ₹3,240     │  │    2        │  │  0 items    │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘

RECENT ORDERS TABLE (orderId, customer, items, amount, status, action)
```

### Admin Screen B: Products
```
[+ Add Product]  [Search...]  [Category ▼]

TABLE: Image | Name | Category | Price | Stock | Status | Actions
                                                         [Edit] [Toggle]
```

### Admin Screen C: Orders
```
FILTERS: [Status ▼] [Date Range]

TABLE: Order# | Customer | Items | Amount | Status | Date | [Update Status]
```

### Admin Screen D: Delivery Tasks
```
TABS: [UNASSIGNED] [ASSIGNED] [IN PROGRESS] [COMPLETED]

TABLE: TaskId | OrderId | Partner | Status | Created | Actions
                                                    [Assign Partner]
```

### Admin Screen E: Coupons
```
[+ New Coupon]

TABLE: Code | Type | Discount | Min Order | Valid Until | Used | Status | [Edit] [Delete]
```

---

## 8. SCREENS — DELIVERY AGENT APP

### Agent Dashboard
```
┌──────────────────────────────────────────────────────┐
│  Good morning, Rahul!                                │
│  ● Available for deliveries     [Go Offline]         │
├──────────────────────────────────────────────────────┤
│  TODAY'S STATS                                       │
│  Deliveries: 5   |   Earnings: ₹500   |  Rating: 4.9│
├──────────────────────────────────────────────────────┤
│  ACTIVE TASK                                         │
│  ┌──────────────────────────────────────────────┐    │
│  │ Order #BLK-20260322-0015                     │    │
│  │ Status: ASSIGNED                             │    │
│  │ Customer: B204, Green Valley, Koramangala    │    │
│  │ Items: 3 · ₹145                              │    │
│  │                                              │    │
│  │ [Picked Up ✓]  [Out for Delivery]  [Delivered]│   │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  [Update My Location]                                │
└──────────────────────────────────────────────────────┘
```

---

## 9. SHARED COMPONENTS

### ProductCard
```jsx
<ProductCard
  product={{ productId, name, imageUrl, sellingPrice, mrp, isAvailable }}
  onAddToCart={handleAdd}
  cartQuantity={0}          // shows +/- if > 0
/>
```

### FloatingCartBar
```jsx
// Renders fixed bottom bar when cartStore.itemCount > 0
<FloatingCartBar
  itemCount={3}
  totalAmount={145.10}
  onViewCart={() => navigate('/cart')}
/>
```

### OrderStatusBadge
```jsx
// Maps status → color + label
const statusConfig = {
  PAYMENT_PENDING:    { label: 'Pending Payment',   color: 'yellow' },
  PAYMENT_PROCESSING: { label: 'Processing',         color: 'blue'   },
  PAYMENT_FAILED:     { label: 'Payment Failed',     color: 'red'    },
  CONFIRMED:          { label: 'Confirmed',           color: 'green'  },
  PACKED:             { label: 'Being Packed',        color: 'blue'   },
  OUT_FOR_DELIVERY:   { label: 'Out for Delivery',   color: 'blue'   },
  DELIVERED:          { label: 'Delivered',           color: 'green'  },
  CANCELLED:          { label: 'Cancelled',           color: 'red'    },
}
```

### ProtectedRoute
```jsx
const ProtectedRoute = ({ children, requiredRole }) => {
  const { isLoggedIn, role } = useAuthStore()
  if (!isLoggedIn) return <Navigate to="/login" />
  if (requiredRole && role !== requiredRole) return <Navigate to="/" />
  return children
}
```

---

## 10. AUTH FLOW (Token Handling)

```
Login:
  → POST /api/auth/login
  → Store accessToken in Zustand (memory, NOT localStorage)
  → Store refreshToken in localStorage
  → Axios interceptor adds: Authorization: Bearer {accessToken}

On page reload:
  → If refreshToken in localStorage: auto-call POST /api/auth/refresh
  → Set new accessToken in Zustand
  → If no refreshToken: redirect to /login

On 401 response:
  → Axios interceptor catches 401
  → Calls POST /api/auth/refresh
  → Retries original request with new token
  → If refresh also 401: logout + redirect to /login

Logout:
  → POST /api/auth/logout
  → Clear authStore
  → Remove refreshToken from localStorage
  → Redirect to /login
```

---

## 11. DEVELOPMENT STAGES

### Overview

| Stage | Focus | Pages | Est. Time |
|---|---|---|---|
| **1** | Project Setup + Auth | Login, Signup, OTP, Forgot/Reset Password | 3–4 days |
| **2** | Browse & Product Catalog | Home, Category, Search, Product Detail | 3–4 days |
| **3** | Cart & Checkout | Cart, Checkout, Addresses | 2–3 days |
| **4** | Orders & Tracking | Order Confirm, Tracking, History | 2 days |
| **5** | Profile, Wallet & Reviews | Profile, Wallet, Reviews | 1–2 days |
| **6** | Admin Panel | Dashboard + 7 admin pages | 4–5 days |
| **7** | Delivery Agent App | Agent dashboard, profile, tasks | 2 days |
| **8** | Polish & Mobile | Skeletons, toasts, bottom nav, empty states | 2 days |

---

### Stage 1 — Project Setup + Auth
**Goal:** Project boots, can log in with real backend, protected routes work, role-based redirect.

**Setup tasks:**
- [ ] `npm create vite@latest frontend -- --template react` inside blinkit-clone/
- [ ] Install: `tailwindcss`, `react-router-dom`, `zustand`, `axios`, `react-hook-form`, `zod`, `react-hot-toast`, `lucide-react`
- [ ] Tailwind config with Blinkit yellow `#F8C200` as `primary`
- [ ] Axios instance (`src/api/axios.js`) — base URL `http://localhost:8080`, auth interceptor, auto-refresh on 401
- [ ] `authStore` (Zustand) — userId, email, role, accessToken (memory), refreshToken (localStorage)
- [ ] `ProtectedRoute` — redirects to `/login` if not authenticated
- [ ] `AdminRoute` — redirects if role !== ADMIN
- [ ] `AgentRoute` — redirects if role !== DELIVERY_AGENT

**Pages:**
- [ ] `LoginPage` — email/password, show/hide toggle, role-based redirect on success
- [ ] `SignupPage` — first/last name, email, phone, password with strength indicator
- [ ] `VerifyOtpPage` — 6-digit boxes, auto-advance, 30s resend countdown
- [ ] `ForgotPasswordPage` — email field, success state
- [ ] `ResetPasswordPage` — reads token from URL params, validates, new password + confirm

**API functions (`src/api/auth.api.js`):**
```
POST /api/auth/login
POST /api/auth/signup
GET  /api/auth/verify?email=X&otp=Y
POST /api/auth/refresh
POST /api/auth/logout
POST /api/auth/forgot-password
GET  /api/auth/reset-password/validate/:token
POST /api/auth/reset-password/:token
```

**Done when:** Login → JWT stored → redirected to `/` (or `/admin`). Refresh token auto-renews access token. Logout clears everything.

---

### Stage 2 — Browse & Product Catalog
**Goal:** Any visitor can browse products by category, search, and view product details.

**Shared components:**
- [ ] `Header` — logo, location text, search bar, cart badge icon, user avatar dropdown
- [ ] `ProductCard` — image placeholder, discount badge, name, unit, price, MRP strikethrough, ADD button
- [ ] `AddToCartButton` — toggles between `[+ ADD]` and `[➖ qty ➕]`
- [ ] `ProductGrid` — responsive 2/3/4 col grid of ProductCards
- [ ] `CategoryPill` — emoji + label, clickable, active highlight

**Pages:**
- [ ] `HomePage`
  - Hero banner carousel (from `GET /api/coupons/active`)
  - Categories horizontal scroll row
  - "Best Sellers" section (first 8 products)
  - One category section per top-level category
  - FloatingCartBar fixed bottom (shows when cart > 0)
- [ ] `CategoryPage` — product grid, sort (price asc/desc), filter by availability
- [ ] `SearchPage` — debounced search input (300ms), results grid, "no results" empty state
- [ ] `ProductDetailPage`
  - Product image, name, price, MRP, discount %, unit
  - Stock badge (In Stock / Out of Stock)
  - Add to cart / qty control
  - Description section
  - Reviews section (paginated, public)
  - "Write a Review" button (only if user has DELIVERED order for this product)

**API functions (`src/api/product.api.js`, `review.api.js`):**
```
GET /api/products
GET /api/products/:productId
GET /api/categories
GET /api/coupons/active
GET /api/inventory/:productId
GET /api/reviews/product/:productId
```

**Done when:** Can browse home page, click a category, search "amul", open product detail — all with real data.

---

### Stage 3 — Cart & Checkout
**Goal:** Add items to cart, apply a coupon, place an order via wallet.

**State:**
- [ ] `cartStore` — items, couponCode, totals; synced with backend on login
- [ ] On login: call `GET /api/cart` and hydrate cartStore
- [ ] All ADD/REMOVE/UPDATE calls hit backend immediately

**Pages:**
- [ ] `CartPage`
  - Item list with qty controls and delete
  - Promo code input + APPLY → calls validate + `POST /api/cart/promo`
  - Bill summary (items total, delivery fee, discount, to pay)
  - Free delivery indicator if total ≥ ₹199
  - "Proceed to Checkout" button
- [ ] `CheckoutPage`
  - Section 1: saved addresses list (radio select); "+ Add New Address" link
  - Section 2: wallet payment card with live balance
  - Order summary (items, total)
  - "Place Order" button → `POST /api/orders` → redirect to `/order-confirm/:orderId`
- [ ] `AddressesPage` — list, add (form modal), edit, delete, set default

**API functions (`src/api/cart.api.js`, `order.api.js`):**
```
GET/POST/PUT/DELETE /api/cart/items
POST/DELETE /api/cart/promo
DELETE /api/cart
GET /api/users/addresses
POST/PUT/DELETE /api/users/addresses
PUT /api/users/addresses/:id/default
POST /api/orders
GET /api/payments/wallet   ← for balance on checkout
```

**Done when:** Add items → cart → apply SAVE10 → checkout → order placed → redirected to confirmation.

---

### Stage 4 — Orders & Tracking
**Goal:** Track order in real-time; view order history.

**Pages:**
- [ ] `OrderConfirmPage`
  - Green success banner with order number and amount debited
  - 7-step status timeline (animated active step)
  - Delivery partner info (once assigned)
  - Order items summary
  - Cancel button (visible only before PACKED)
  - Polls `GET /api/delivery/track/:orderId` every 10s to update timeline
- [ ] `OrderTrackingPage` — same as confirm but accessed from order history
- [ ] `OrdersPage`
  - Filter tabs: All / Active / Delivered / Cancelled
  - `OrderCard` per order: number, date, items thumbnails, status badge, CTA button
  - Active → "Track Order" button
  - Delivered → "Rate Items" button
  - Cancelled → "Reorder" button (pre-fills cart)

**Components:**
- [ ] `OrderStatusBadge` — status → color + label mapping
- [ ] `OrderTimeline` — vertical stepper, completed/active/pending states

**API:**
```
GET /api/orders
GET /api/orders/:orderId
POST /api/orders/:orderId/cancel
GET /api/delivery/track/:orderId
```

**Done when:** After placing order, status timeline updates live. Order history shows all past orders with correct status.

---

### Stage 5 — Profile, Wallet & Reviews
**Goal:** Account management, see wallet balance, write reviews.

**Pages:**
- [ ] `ProfilePage`
  - Avatar, name, email display
  - Edit profile form (first/last name, phone)
  - Menu links: Orders, Addresses, Wallet, Reviews, Logout
  - Delete Account (confirmation modal → `DELETE /api/auth/account`)
- [ ] `WalletPage`
  - Balance card (yellow gradient)
  - Transaction history list (credit green ↑ / debit red ↓)
  - Pagination
- [ ] Review modal (triggered from ProductDetailPage or OrdersPage)
  - Star rating selector (1–5, interactive)
  - Title (optional) + comment fields
  - Submit → `POST /api/reviews`
  - Update if already reviewed (same endpoint, idempotent)
- [ ] `MyReviewsPage` — list of own reviews with edit/delete

**API:**
```
GET/PUT /api/users/profile
GET /api/payments/wallet
GET /api/payments/history
POST /api/reviews
GET /api/reviews/me
DELETE /api/reviews/:reviewId
DELETE /api/auth/account
```

**Done when:** User can edit profile, see wallet balance + history, write a review for a delivered product.

---

### Stage 6 — Admin Panel
**Goal:** Admin can manage the full platform from a dashboard.

**Layout:**
- [ ] `AdminLayout` — dark sidebar (`#1A1A1A`), active link yellow highlight, hamburger on mobile
- [ ] `AdminRoute` wrapper for all `/admin/*` routes

**Pages:**
- [ ] `AdminDashboardPage`
  - 4 stat cards: Today's Orders, Today's Revenue, Active Orders, Total Users
  - Recent orders table (order#, customer, items, amount, status, action)
  - Low stock alerts table
- [ ] `AdminProductsPage`
  - Searchable, filterable table (category, status)
  - Add Product modal (name, category, MRP, selling price, unit, description, image URL)
  - Edit Product modal
  - Toggle availability button
- [ ] `AdminOrdersPage`
  - Filter by status + date range
  - Table with status update dropdown per row
- [ ] `AdminInventoryPage`
  - Table: product name, current stock, low stock threshold
  - Inline edit stock quantity
- [ ] `AdminDeliveryPage`
  - Tabs: UNASSIGNED / ASSIGNED / IN PROGRESS / COMPLETED
  - Assign partner button on UNASSIGNED tasks
- [ ] `AdminCouponsPage`
  - Full CRUD: create, view, edit, deactivate, delete
  - Usage count per coupon
- [ ] `AdminWalletsPage`
  - All user wallets table (userId, balance)
  - Manual topup modal

**API:**
```
GET /api/products?admin + POST/PUT/DELETE /api/products/admin
GET /api/orders/admin + PUT /api/orders/admin/:id/status
GET /api/inventory/admin + PUT /api/inventory/admin/:productId
GET /api/delivery/admin/tasks + POST /api/delivery/admin/tasks/:id/assign
GET /api/delivery/admin/partners + PUT /api/delivery/admin/partners/:id/toggle
GET/POST/PUT/DELETE /api/coupons/admin
GET /api/payments/admin/wallets + POST /api/payments/admin/wallets/:userId/topup
```

**Done when:** Admin can create products, update order statuses, assign delivery partners, manage coupons, topup wallets — all through the UI.

---

### Stage 7 — Delivery Agent App
**Goal:** Delivery agent can manage their tasks and profile.

**Layout:**
- [ ] `AgentLayout` — simple top nav (no sidebar), yellow accent

**Pages:**
- [ ] `AgentDashboard`
  - Availability toggle (Go Online / Go Offline) → `PUT /api/delivery/partners/me/availability`
  - Active task card (if assigned): order details + customer address
  - Status update buttons: `[Picked Up]` → `[Out for Delivery]` → `[Delivered]`
  - Failed delivery button (with reason input)
- [ ] `AgentProfilePage`
  - View/edit profile (name, phone, vehicle type, vehicle number)
  - Update location button → uses browser `navigator.geolocation` → `PUT /api/delivery/partners/me/location`
- [ ] `AgentTasksPage`
  - Past completed / failed tasks list

**API:**
```
GET/PUT /api/delivery/partners/me
PUT /api/delivery/partners/me/availability
PUT /api/delivery/partners/me/location
GET /api/delivery/tasks/mine
PUT /api/delivery/tasks/:taskId/status
```

**Done when:** Agent can log in, toggle availability, pick up order, mark as delivered.

---

### Stage 8 — Polish & Mobile
**Goal:** Production-quality finish across all pages.

- [ ] **Mobile bottom nav** — 4 tabs: Home / Search / Orders / Profile (hidden on desktop)
- [ ] **Loading skeletons** — for product grids, order lists, profile page
- [ ] **Empty states** — "Your cart is empty 🛒", "No orders yet", "No reviews yet"
- [ ] **Error boundary** — catches JS crashes, shows friendly error page
- [ ] **Toast notifications** — success/error feedback on every action (react-hot-toast)
- [ ] **Form validation messages** — inline field errors everywhere
- [ ] **Disabled states** — buttons disabled during API calls (prevent double submit)
- [ ] **Page transitions** — subtle fade on route change
- [ ] **Responsive audit** — test all pages on 375px (mobile), 768px (tablet), 1280px (desktop)

---

## WHAT WE DON'T BUILD (scope cut)

| Feature | Reason |
|---|---|
| Live map tracking (Google Maps) | Requires Maps API key + WebSocket — polling used instead |
| SMS / Push notifications | Backend not wired; email-only currently |
| Reorder button | Nice-to-have, not blocking |
| Product image upload in admin | Requires Cloudinary; URLs can be pasted for now |
| Ratings update on product document | review-service doesn't call product-service yet |
