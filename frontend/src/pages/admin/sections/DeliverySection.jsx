import { useEffect, useState } from 'react';
import { Truck, UserPlus, X, ToggleLeft, ToggleRight, Package, MapPin, User, Phone, Star, ShieldCheck, ShieldOff } from 'lucide-react';
import toast from 'react-hot-toast';
import { adminApi } from '../../../api/admin.api';

const TASK_STATUS_STYLE = {
  UNASSIGNED:       'bg-gray-100 text-gray-600',
  ASSIGNED:         'bg-blue-100 text-blue-700',
  PICKED_UP:        'bg-orange-100 text-orange-700',
  OUT_FOR_DELIVERY: 'bg-purple-100 text-purple-700',
  DELIVERED:        'bg-green-100 text-green-700',
  FAILED:           'bg-red-100 text-red-600',
  CANCELLED:        'bg-gray-100 text-gray-400',
};

// ── Task Detail Modal ────────────────────────────────────────────────────────

function TaskDetailModal({ task, customerName, partner, onClose }) {
  const [order, setOrder]     = useState(null);
  const [address, setAddress] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      const [orderRes, addrRes] = await Promise.allSettled([
        adminApi.getOrder(task.orderId),
        task.addressId ? adminApi.getAddressById(task.addressId) : Promise.resolve(null),
      ]);
      if (orderRes.status === 'fulfilled') setOrder(orderRes.value?.data?.data);
      if (addrRes.status  === 'fulfilled') setAddress(addrRes.value?.data?.data);
      setLoading(false);
    };
    load();
  }, [task.orderId, task.addressId]);

  const statusClass = TASK_STATUS_STYLE[task.status] || 'bg-gray-100 text-gray-600';

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-lg shadow-2xl max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 flex-shrink-0">
          <div>
            <h3 className="text-base font-bold text-gray-900">Delivery Detail</h3>
            <p className="text-xs text-gray-400 font-mono mt-0.5">{task.orderId?.slice(0, 16)}...</p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>

        <div className="overflow-y-auto flex-1 p-6 space-y-5">
          {/* Status */}
          <div className="flex items-center gap-3">
            <span className={`text-xs font-bold px-3 py-1 rounded-full ${statusClass}`}>
              {task.status?.replace(/_/g, ' ')}
            </span>
            {order && (
              <span className="text-xs text-gray-400">Order #{order.orderNumber}</span>
            )}
          </div>

          {/* Customer */}
          <div className="bg-gray-50 rounded-xl p-4 space-y-1">
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5">
              <User size={12} /> Customer
            </p>
            <p className="text-sm font-semibold text-gray-800">{customerName || '—'}</p>
            <p className="text-xs text-gray-400 font-mono">{task.userId?.slice(0, 12)}...</p>
          </div>

          {/* Delivery Address */}
          {address && (
            <div className="bg-gray-50 rounded-xl p-4 space-y-1">
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5">
                <MapPin size={12} /> Delivery Address
              </p>
              <p className="text-sm text-gray-800">
                {[address.flat, address.building, address.street, address.area, address.city, address.state]
                  .filter(Boolean).join(', ')}
              </p>
              {address.pincode && <p className="text-xs text-gray-400">Pincode: {address.pincode}</p>}
            </div>
          )}

          {/* Delivery Partner */}
          <div className="bg-gray-50 rounded-xl p-4 space-y-1">
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5">
              <Truck size={12} /> Delivery Partner
            </p>
            {partner ? (
              <>
                <p className="text-sm font-semibold text-gray-800">{partner.name || '—'}</p>
                {partner.phone && (
                  <p className="text-xs text-gray-600 flex items-center gap-1">
                    <Phone size={11} /> {partner.phone}
                  </p>
                )}
                {partner.vehicleType && (
                  <p className="text-xs text-gray-400">{partner.vehicleType} · {partner.vehicleNumber || 'N/A'}</p>
                )}
              </>
            ) : (
              <p className="text-sm text-gray-400">Not assigned</p>
            )}
          </div>

          {/* Order Items */}
          <div>
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5 mb-3">
              <Package size={12} /> Items
            </p>
            {loading ? (
              <div className="space-y-2">
                {[1, 2, 3].map(i => <div key={i} className="h-8 bg-gray-100 rounded-lg animate-pulse" />)}
              </div>
            ) : order?.items?.length ? (
              <div className="space-y-2">
                {order.items.map((item, i) => (
                  <div key={i} className="flex items-center justify-between bg-gray-50 rounded-xl px-4 py-2.5">
                    <div>
                      <p className="text-sm font-medium text-gray-800">{item.productName || item.name || `Item ${i + 1}`}</p>
                      <p className="text-xs text-gray-400">Qty: {item.quantity}</p>
                    </div>
                    <p className="text-sm font-semibold text-gray-700">₹{item.totalPrice?.toFixed(0) ?? (item.price * item.quantity)?.toFixed(0) ?? '—'}</p>
                  </div>
                ))}
                <div className="flex justify-between pt-2 border-t border-gray-100 px-1">
                  <span className="text-sm font-semibold text-gray-700">Total</span>
                  <span className="text-sm font-bold text-gray-900">₹{order.totalAmount?.toFixed(0)}</span>
                </div>
              </div>
            ) : (
              <p className="text-sm text-gray-400">No items found</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Assign Modal ─────────────────────────────────────────────────────────────

function AssignModal({ task, partners, onClose, onAssigned }) {
  const [partnerId, setPartnerId] = useState('');
  const [saving, setSaving]       = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!partnerId) { toast.error('Select a delivery partner'); return; }
    setSaving(true);
    try {
      await adminApi.assignDeliveryTask(task.taskId, partnerId);
      toast.success('Task assigned');
      onAssigned();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to assign task');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-sm shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 className="text-base font-bold text-gray-900">Assign Delivery Partner</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <select
            value={partnerId}
            onChange={e => setPartnerId(e.target.value)}
            className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 bg-white"
            required
          >
            <option value="">Choose a partner</option>
            {partners.map(p => (
              <option key={p.partnerId} value={p.partnerId}>
                {p.name || p.email || p.partnerId?.slice(0, 12)}
                {p.phone ? ` · ${p.phone}` : ''}
              </option>
            ))}
          </select>
          <div className="flex gap-3">
            <button type="button" onClick={onClose}
              className="flex-1 border border-gray-200 text-gray-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-gray-50">
              Cancel
            </button>
            <button type="submit" disabled={saving}
              className="flex-1 bg-yellow-400 text-gray-900 font-bold py-2.5 rounded-xl text-sm hover:bg-yellow-500 disabled:opacity-60">
              {saving ? 'Assigning...' : 'Assign'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Create Agent Modal ───────────────────────────────────────────────────────

function CreateAgentModal({ onClose, onCreated }) {
  const [form, setForm] = useState({ email: '', password: '' });
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.email.trim() || !form.password.trim()) { toast.error('Email and password are required'); return; }
    setSaving(true);
    try {
      await adminApi.createDeliveryAgent(form);
      toast.success('Delivery agent created');
      onCreated();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to create agent');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-sm shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 className="text-base font-bold text-gray-900">Create Delivery Agent</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <input type="email" placeholder="agent@example.com" required
            value={form.email} onChange={e => setForm(p => ({ ...p, email: e.target.value }))}
            className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />
          <input type="password" placeholder="Password (min 8 chars)" required
            value={form.password} onChange={e => setForm(p => ({ ...p, password: e.target.value }))}
            className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />
          <div className="flex gap-3">
            <button type="button" onClick={onClose}
              className="flex-1 border border-gray-200 text-gray-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-gray-50">
              Cancel
            </button>
            <button type="submit" disabled={saving}
              className="flex-1 bg-yellow-400 text-gray-900 font-bold py-2.5 rounded-xl text-sm hover:bg-yellow-500 disabled:opacity-60">
              {saving ? 'Creating...' : 'Create Agent'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Tasks Tab ────────────────────────────────────────────────────────────────

function TasksTab() {
  const [tasks, setTasks]             = useState([]);
  const [partners, setPartners]       = useState([]);
  const [partnerMap, setPartnerMap]   = useState({}); // partnerId → partner object
  const [customerNames, setCustomerNames] = useState({}); // userId → name
  const [loading, setLoading]         = useState(true);
  const [page, setPage]               = useState(0);
  const [totalPages, setTotalPages]   = useState(1);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [assignModal, setAssignModal] = useState(null);
  const [detailTask, setDetailTask]   = useState(null);

  useEffect(() => { loadTasks(0); loadAllPartners(); }, [statusFilter]);

  const loadTasks = async (p) => {
    setLoading(true);
    try {
      const params = { page: p, size: 20 };
      if (statusFilter !== 'ALL') params.status = statusFilter;
      const res = await adminApi.getDeliveryTasks(params);
      const data = res.data?.data;
      const newTasks = data?.content || data || [];
      setTasks(prev => p === 0 ? newTasks : [...prev, ...newTasks]);
      setTotalPages(data?.totalPages || 1);
      setPage(p);

      // Fetch partner details for tasks that have a partner assigned
      const partnerIds = [...new Set(newTasks.map(t => t.deliveryPartnerId).filter(Boolean))];
      if (partnerIds.length) {
        const results = await Promise.allSettled(
          partnerIds.map(id => adminApi.getDeliveryPartner(id).then(r => ({ id, partner: r.data?.data })))
        );
        setPartnerMap(prev => {
          const next = { ...prev };
          results.forEach(r => { if (r.status === 'fulfilled' && r.value.partner) next[r.value.id] = r.value.partner; });
          return next;
        });
      }

      // Fetch customer names
      const userIds = [...new Set(newTasks.map(t => t.userId).filter(Boolean))];
      if (userIds.length) {
        const results = await Promise.allSettled(
          userIds.map(id => adminApi.getUserById(id).then(r => ({ id, profile: r.data?.data })))
        );
        const missing = results.filter(r => r.status !== 'fulfilled' || !r.value?.profile).map(r => r.value?.id).filter(Boolean);
        const authResults = missing.length
          ? await Promise.allSettled(missing.map(id => adminApi.getAuthUserById(id).then(r => ({ id, authUser: r.data?.data }))))
          : [];
        setCustomerNames(prev => {
          const next = { ...prev };
          results.forEach(r => {
            if (r.status === 'fulfilled' && r.value?.profile) {
              const p = r.value.profile;
              next[r.value.id] = [p.firstName, p.lastName].filter(Boolean).join(' ') || p.email || r.value.id;
            }
          });
          authResults.forEach(r => {
            if (r.status === 'fulfilled' && r.value?.authUser) next[r.value.id] = r.value.authUser.email;
          });
          return next;
        });
      }
    } catch {
      toast.error('Failed to load tasks');
    } finally {
      setLoading(false);
    }
  };

  const loadAllPartners = async () => {
    try {
      const res = await adminApi.getDeliveryPartners({ page: 0, size: 100 });
      const data = res.data?.data;
      setPartners(data?.content || data || []);
    } catch {}
  };

  return (
    <div className="space-y-4">
      {assignModal && (
        <AssignModal task={assignModal} partners={partners}
          onClose={() => setAssignModal(null)}
          onAssigned={() => { setAssignModal(null); loadTasks(0); }} />
      )}
      {detailTask && (
        <TaskDetailModal
          task={detailTask}
          customerName={customerNames[detailTask.userId]}
          partner={partnerMap[detailTask.deliveryPartnerId]}
          onClose={() => setDetailTask(null)} />
      )}

      <div className="flex items-center gap-3">
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
          className="border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 bg-white">
          <option value="ALL">All Statuses</option>
          {Object.keys(TASK_STATUS_STYLE).map(s => (
            <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
          ))}
        </select>
      </div>

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {loading && tasks.length === 0 ? (
          <div className="p-6 space-y-3">
            {[1, 2, 3].map(i => <div key={i} className="h-12 bg-gray-100 rounded-xl animate-pulse" />)}
          </div>
        ) : tasks.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <Truck size={40} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No delivery tasks</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-5 py-3 text-left">Order ID</th>
                  <th className="px-5 py-3 text-left">Customer</th>
                  <th className="px-5 py-3 text-left">Delivery Partner</th>
                  <th className="px-5 py-3 text-left">Status</th>
                  <th className="px-5 py-3 text-left">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {tasks.map((task) => {
                  const partner = partnerMap[task.deliveryPartnerId];
                  const statusClass = TASK_STATUS_STYLE[task.status] || 'bg-gray-100 text-gray-600';
                  const canAssign = task.status === 'UNASSIGNED' || !task.deliveryPartnerId;
                  return (
                    <tr key={task.taskId}
                      className="hover:bg-gray-50 transition-colors cursor-pointer"
                      onClick={() => setDetailTask(task)}>
                      <td className="px-5 py-3 font-mono text-xs text-gray-600">
                        {task.orderId?.slice(0, 8)}...
                      </td>
                      <td className="px-5 py-3">
                        <p className="text-sm font-medium text-gray-800">
                          {customerNames[task.userId] || <span className="text-gray-300">—</span>}
                        </p>
                      </td>
                      <td className="px-5 py-3">
                        {partner ? (
                          <div>
                            <p className="text-sm font-medium text-gray-800">{partner.name || '—'}</p>
                            {partner.phone && (
                              <p className="text-xs text-gray-400 flex items-center gap-1">
                                <Phone size={10} /> {partner.phone}
                              </p>
                            )}
                          </div>
                        ) : (
                          <span className="text-xs text-gray-300">Unassigned</span>
                        )}
                      </td>
                      <td className="px-5 py-3">
                        <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${statusClass}`}>
                          {task.status?.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="px-5 py-3" onClick={e => e.stopPropagation()}>
                        {canAssign && (
                          <button onClick={() => setAssignModal(task)}
                            className="text-xs font-semibold text-blue-600 hover:text-blue-800 underline">
                            Assign
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {page < totalPages - 1 && !loading && (
          <div className="px-6 py-4 border-t border-gray-50">
            <button onClick={() => loadTasks(page + 1)}
              className="w-full border border-yellow-400 text-yellow-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-yellow-50 transition-colors">
              Load More
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Partner Detail Modal ─────────────────────────────────────────────────────

function PartnerDetailModal({ partner, onClose }) {
  const joined = partner.createdAt
    ? new Date(partner.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
    : '—';
  const lastLoc = partner.lastLocationUpdatedAt
    ? new Date(partner.lastLocationUpdatedAt).toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
    : null;

  const Row = ({ label, value }) => (
    <div className="flex justify-between items-center py-2.5 border-b border-gray-50 last:border-0">
      <span className="text-xs text-gray-400 font-medium">{label}</span>
      <span className="text-sm text-gray-800 font-semibold text-right">{value || '—'}</span>
    </div>
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-sm shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 className="text-base font-bold text-gray-900">Partner Details</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>

        <div className="p-6 space-y-4">
          {/* Avatar + name */}
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-2xl bg-yellow-100 flex items-center justify-center flex-shrink-0">
              <User size={26} className="text-yellow-600" />
            </div>
            <div>
              <p className="text-lg font-black text-gray-900">{partner.name || '—'}</p>
              <div className="flex items-center gap-2 mt-0.5">
                <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${partner.isActive ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                  {partner.isActive ? 'Active' : 'Inactive'}
                </span>
                <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${partner.isAvailable ? 'bg-blue-100 text-blue-700' : 'bg-orange-100 text-orange-600'}`}>
                  {partner.isAvailable ? 'Available' : 'Busy'}
                </span>
              </div>
            </div>
          </div>

          {/* Stats row */}
          <div className="grid grid-cols-2 gap-3">
            <div className="bg-gray-50 rounded-xl p-3 text-center">
              <p className="text-xl font-black text-gray-900">{partner.totalDeliveries ?? 0}</p>
              <p className="text-xs text-gray-400 mt-0.5">Total Deliveries</p>
            </div>
            <div className="bg-gray-50 rounded-xl p-3 text-center">
              <p className="text-xl font-black text-yellow-500 flex items-center justify-center gap-1">
                <Star size={16} className="fill-yellow-400 text-yellow-400" />
                {partner.avgRating ? partner.avgRating.toFixed(1) : 'N/A'}
              </p>
              <p className="text-xs text-gray-400 mt-0.5">Avg Rating</p>
            </div>
          </div>

          {/* Details */}
          <div className="bg-gray-50 rounded-xl px-4 py-1">
            <Row label="Email"   value={partner.email} />
            <Row label="Phone"   value={partner.phone} />
            <Row label="Vehicle" value={partner.vehicleType} />
            <Row label="Vehicle No." value={partner.vehicleNumber} />
            <Row label="Joined"  value={joined} />
            {lastLoc && <Row label="Last Location" value={lastLoc} />}
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Partners Tab ─────────────────────────────────────────────────────────────

function PartnersTab() {
  const [partners, setPartners]       = useState([]);
  const [loading, setLoading]         = useState(true);
  const [page, setPage]               = useState(0);
  const [totalPages, setTotalPages]   = useState(1);
  const [toggling, setToggling]       = useState(null);
  const [showCreate, setShowCreate]   = useState(false);
  const [detailPartner, setDetailPartner] = useState(null);

  useEffect(() => { loadPartners(0); }, []);

  const loadPartners = async (p) => {
    setLoading(true);
    try {
      const res = await adminApi.getDeliveryPartners({ page: p, size: 20 });
      const data = res.data?.data;
      setPartners(prev => p === 0 ? (data?.content || data || []) : [...prev, ...(data?.content || data || [])]);
      setTotalPages(data?.totalPages || 1);
      setPage(p);
    } catch { toast.error('Failed to load partners'); }
    finally { setLoading(false); }
  };

  const handleToggle = async (partnerId) => {
    setToggling(partnerId);
    try {
      await adminApi.toggleDeliveryPartner(partnerId);
      setPartners(prev => prev.map(p => p.partnerId === partnerId ? { ...p, isActive: !p.isActive } : p));
      toast.success('Partner status updated');
    } catch { toast.error('Failed to update partner'); }
    finally { setToggling(null); }
  };

  return (
    <div className="space-y-4">
      {showCreate && (
        <CreateAgentModal onClose={() => setShowCreate(false)}
          onCreated={() => { setShowCreate(false); loadPartners(0); }} />
      )}
      {detailPartner && (
        <PartnerDetailModal partner={detailPartner} onClose={() => setDetailPartner(null)} />
      )}

      <div className="flex justify-end">
        <button onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 bg-yellow-400 text-gray-900 font-bold px-4 py-2.5 rounded-xl text-sm hover:bg-yellow-500 transition-colors">
          <UserPlus size={16} /> Create Delivery Agent
        </button>
      </div>

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {loading && partners.length === 0 ? (
          <div className="p-6 space-y-3">
            {[1, 2, 3].map(i => <div key={i} className="h-12 bg-gray-100 rounded-xl animate-pulse" />)}
          </div>
        ) : partners.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <Truck size={40} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No delivery partners yet</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-5 py-3 text-left">Name</th>
                  <th className="px-5 py-3 text-left">Phone</th>
                  <th className="px-5 py-3 text-left">Vehicle</th>
                  <th className="px-5 py-3 text-left">Status</th>
                  <th className="px-5 py-3 text-left">Toggle</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {partners.map((partner) => (
                  <tr key={partner.partnerId}
                    className="hover:bg-gray-50 transition-colors cursor-pointer"
                    onClick={() => setDetailPartner(partner)}>
                    <td className="px-5 py-3">
                      <p className="text-sm font-medium text-gray-800">{partner.name || '—'}</p>
                      <p className="text-xs text-gray-400">{partner.email}</p>
                    </td>
                    <td className="px-5 py-3 text-sm text-gray-600">{partner.phone || '—'}</td>
                    <td className="px-5 py-3 text-xs text-gray-500">
                      {partner.vehicleType || '—'}{partner.vehicleNumber ? ` · ${partner.vehicleNumber}` : ''}
                    </td>
                    <td className="px-5 py-3">
                      <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${partner.isActive ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                        {partner.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="px-5 py-3" onClick={e => e.stopPropagation()}>
                      <button onClick={() => handleToggle(partner.partnerId)} disabled={toggling === partner.partnerId}
                        className="text-gray-400 hover:text-yellow-500 transition-colors disabled:opacity-40">
                        {partner.isActive
                          ? <ToggleRight size={20} className="text-green-500" />
                          : <ToggleLeft size={20} />}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {page < totalPages - 1 && !loading && (
          <div className="px-6 py-4 border-t border-gray-50">
            <button onClick={() => loadPartners(page + 1)}
              className="w-full border border-yellow-400 text-yellow-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-yellow-50 transition-colors">
              Load More
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Main ─────────────────────────────────────────────────────────────────────

export default function DeliverySection() {
  const [activeTab, setActiveTab] = useState('tasks');

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-xl font-bold text-gray-900">Delivery</h2>
        <p className="text-sm text-gray-500">Manage delivery tasks and partners</p>
      </div>

      <div className="flex gap-1 bg-gray-100 p-1 rounded-xl w-fit">
        {[{ key: 'tasks', label: 'Delivery Tasks' }, { key: 'partners', label: 'Delivery Partners' }].map(tab => (
          <button key={tab.key} onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
              activeTab === tab.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
            }`}>
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'tasks' ? <TasksTab /> : <PartnersTab />}
    </div>
  );
}
