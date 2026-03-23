import { useEffect, useState, useCallback } from 'react';
import {
  Truck, Star, CheckCircle, XCircle, Package, MapPin,
  ToggleLeft, ToggleRight, LogOut, ChevronDown, ChevronUp, AlertCircle,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { agentApi } from '../../api/agent.api';
import { authApi } from '../../api/auth.api';
import { useAuthStore } from '../../stores/authStore';
import { useNavigate } from 'react-router-dom';

// ── Status config ─────────────────────────────────────────────────────────────

const STATUS_META = {
  UNASSIGNED:       { label: 'Unassigned',       color: 'bg-gray-100 text-gray-500' },
  ASSIGNED:         { label: 'Assigned',          color: 'bg-blue-100 text-blue-700' },
  PICKED_UP:        { label: 'Picked Up',         color: 'bg-orange-100 text-orange-700' },
  OUT_FOR_DELIVERY: { label: 'Out for Delivery',  color: 'bg-purple-100 text-purple-700' },
  DELIVERED:        { label: 'Delivered',         color: 'bg-green-100 text-green-700' },
  FAILED:           { label: 'Failed',            color: 'bg-red-100 text-red-600' },
  CANCELLED:        { label: 'Cancelled',         color: 'bg-gray-100 text-gray-400' },
};

const ACTIVE_STATUSES    = ['ASSIGNED', 'PICKED_UP', 'OUT_FOR_DELIVERY'];
const COMPLETED_STATUSES = ['DELIVERED', 'FAILED', 'CANCELLED'];

// ── Failure Reason Modal ──────────────────────────────────────────────────────

function FailureModal({ onConfirm, onClose, loading }) {
  const [reason, setReason] = useState('');
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="bg-white rounded-2xl w-full max-w-sm shadow-2xl p-6 space-y-4">
        <div className="flex items-center gap-2 text-red-500">
          <AlertCircle size={20} />
          <h3 className="font-bold text-gray-900">Mark as Failed</h3>
        </div>
        <textarea
          value={reason}
          onChange={e => setReason(e.target.value)}
          rows={3}
          placeholder="Reason for failure (e.g. customer not available, wrong address...)"
          className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-300 resize-none"
        />
        <div className="flex gap-3">
          <button onClick={onClose}
            className="flex-1 border border-gray-200 text-gray-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-gray-50">
            Cancel
          </button>
          <button
            onClick={() => { if (!reason.trim()) { toast.error('Reason is required'); return; } onConfirm(reason.trim()); }}
            disabled={loading}
            className="flex-1 bg-red-500 text-white font-bold py-2.5 rounded-xl text-sm hover:bg-red-600 disabled:opacity-60">
            {loading ? 'Saving...' : 'Confirm Failed'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Task Card ─────────────────────────────────────────────────────────────────

function TaskCard({ task, onStatusUpdated }) {
  const [expanded, setExpanded]         = useState(false);
  const [address, setAddress]           = useState(null);
  const [updating, setUpdating]         = useState(false);
  const [showFailModal, setShowFailModal] = useState(false);

  useEffect(() => {
    if (expanded && task.addressId && !address) {
      agentApi.getAddressById(task.addressId)
        .then(r => setAddress(r.data?.data))
        .catch(() => {});
    }
  }, [expanded, task.addressId]);

  const doUpdate = async (status, failureReason = undefined) => {
    setUpdating(true);
    try {
      await agentApi.updateTaskStatus(task.taskId, status, failureReason);
      toast.success(`Marked as ${status.replace(/_/g, ' ')}`);
      onStatusUpdated();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to update status');
    } finally {
      setUpdating(false);
      setShowFailModal(false);
    }
  };

  const meta   = STATUS_META[task.status] || STATUS_META.UNASSIGNED;
  const isActive = ACTIVE_STATUSES.includes(task.status);

  const addressLine = address
    ? [address.flat, address.building, address.street, address.area, address.city]
        .filter(Boolean).join(', ')
    : null;

  return (
    <>
      {showFailModal && (
        <FailureModal
          loading={updating}
          onClose={() => setShowFailModal(false)}
          onConfirm={(reason) => doUpdate('FAILED', reason)}
        />
      )}

      <div className={`bg-white rounded-2xl shadow-sm border ${isActive ? 'border-yellow-200' : 'border-gray-100'}`}>
        {/* Card header — always visible */}
        <div className="p-4 flex items-center justify-between cursor-pointer"
          onClick={() => setExpanded(e => !e)}>
          <div className="flex items-center gap-3">
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${isActive ? 'bg-yellow-50' : 'bg-gray-50'}`}>
              <Truck size={18} className={isActive ? 'text-yellow-500' : 'text-gray-400'} />
            </div>
            <div>
              <p className="text-sm font-bold text-gray-900 font-mono">
                #{task.orderId?.slice(0, 8).toUpperCase()}
              </p>
              <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${meta.color}`}>
                {meta.label}
              </span>
            </div>
          </div>
          {expanded ? <ChevronUp size={16} className="text-gray-400" /> : <ChevronDown size={16} className="text-gray-400" />}
        </div>

        {/* Expanded detail */}
        {expanded && (
          <div className="border-t border-gray-50 p-4 space-y-3">
            {/* Pickup */}
            <div className="flex gap-2 text-xs text-gray-500">
              <Package size={13} className="mt-0.5 flex-shrink-0 text-yellow-500" />
              <div>
                <p className="font-semibold text-gray-700">Pickup from</p>
                <p>{task.storeName}</p>
                <p>{task.storeAddress}</p>
              </div>
            </div>

            {/* Delivery address */}
            <div className="flex gap-2 text-xs text-gray-500">
              <MapPin size={13} className="mt-0.5 flex-shrink-0 text-blue-500" />
              <div>
                <p className="font-semibold text-gray-700">Deliver to</p>
                {addressLine
                  ? <p>{addressLine}</p>
                  : task.addressId
                    ? <p className="text-gray-300 italic">Loading address...</p>
                    : <p className="text-gray-300 italic">No address</p>
                }
                {address?.pincode && <p>Pincode: {address.pincode}</p>}
                {address?.recipientName && <p className="font-medium text-gray-600">{address.recipientName} · {address.recipientPhone}</p>}
              </div>
            </div>

            {/* ETA */}
            {task.estimatedDeliveryAt && (
              <p className="text-xs text-gray-400">
                ETA: {new Date(task.estimatedDeliveryAt).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}
              </p>
            )}

            {/* Failure reason if failed */}
            {task.status === 'FAILED' && task.failureReason && (
              <div className="bg-red-50 rounded-xl px-3 py-2 text-xs text-red-600">
                Reason: {task.failureReason}
              </div>
            )}

            {/* Action buttons */}
            {task.status === 'ASSIGNED' && (
              <button onClick={() => doUpdate('PICKED_UP')} disabled={updating}
                className="w-full bg-yellow-400 text-gray-900 font-bold py-3 rounded-xl text-sm hover:bg-yellow-500 disabled:opacity-60 flex items-center justify-center gap-2">
                <Package size={16} /> {updating ? 'Updating...' : 'Picked Up from Store'}
              </button>
            )}

            {task.status === 'PICKED_UP' && (
              <button onClick={() => doUpdate('OUT_FOR_DELIVERY')} disabled={updating}
                className="w-full bg-purple-500 text-white font-bold py-3 rounded-xl text-sm hover:bg-purple-600 disabled:opacity-60 flex items-center justify-center gap-2">
                <Truck size={16} /> {updating ? 'Updating...' : 'Out for Delivery'}
              </button>
            )}

            {task.status === 'OUT_FOR_DELIVERY' && (
              <div className="flex gap-2">
                <button onClick={() => doUpdate('DELIVERED')} disabled={updating}
                  className="flex-1 bg-green-500 text-white font-bold py-3 rounded-xl text-sm hover:bg-green-600 disabled:opacity-60 flex items-center justify-center gap-2">
                  <CheckCircle size={16} /> {updating ? '...' : 'Delivered'}
                </button>
                <button onClick={() => setShowFailModal(true)} disabled={updating}
                  className="flex-1 bg-red-100 text-red-600 font-bold py-3 rounded-xl text-sm hover:bg-red-200 disabled:opacity-60 flex items-center justify-center gap-2">
                  <XCircle size={16} /> Failed
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </>
  );
}

// ── Setup Profile Form ────────────────────────────────────────────────────────

function SetupProfile({ onDone }) {
  const [form, setForm]   = useState({ name: '', phone: '', vehicleType: 'MOTORCYCLE', vehicleNumber: '' });
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      await agentApi.registerProfile(form);
      toast.success('Profile created!');
      onDone();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to create profile');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-sm w-full max-w-sm p-6 space-y-5">
        <div className="text-center">
          <div className="w-16 h-16 bg-yellow-100 rounded-2xl flex items-center justify-center mx-auto mb-3">
            <Truck size={30} className="text-yellow-500" />
          </div>
          <h2 className="text-xl font-black text-gray-900">Set Up Your Profile</h2>
          <p className="text-sm text-gray-400 mt-1">Complete your profile to start receiving deliveries</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3">
          <input required placeholder="Full Name" value={form.name}
            onChange={e => setForm(p => ({ ...p, name: e.target.value }))}
            className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />

          <input required placeholder="Phone (10 digits)" value={form.phone}
            onChange={e => setForm(p => ({ ...p, phone: e.target.value }))}
            className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />

          <select value={form.vehicleType}
            onChange={e => setForm(p => ({ ...p, vehicleType: e.target.value }))}
            className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 bg-white">
            <option value="BICYCLE">Bicycle</option>
            <option value="MOTORCYCLE">Motorcycle</option>
            <option value="SCOOTER">Scooter</option>
            <option value="CAR">Car</option>
          </select>

          <input placeholder="Vehicle Number (optional)" value={form.vehicleNumber}
            onChange={e => setForm(p => ({ ...p, vehicleNumber: e.target.value }))}
            className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400" />

          <button type="submit" disabled={saving}
            className="w-full bg-yellow-400 text-gray-900 font-bold py-3 rounded-xl text-sm hover:bg-yellow-500 disabled:opacity-60">
            {saving ? 'Saving...' : 'Start Delivering'}
          </button>
        </form>
      </div>
    </div>
  );
}

// ── Main Dashboard ────────────────────────────────────────────────────────────

export default function AgentDashboardPage() {
  const navigate               = useNavigate();
  const { logout }             = useAuthStore();
  const [profile, setProfile]  = useState(null);
  const [tasks, setTasks]      = useState([]);
  const [loading, setLoading]  = useState(true);
  const [tab, setTab]          = useState('active');
  const [toggling, setToggling] = useState(false);
  const [needsSetup, setNeedsSetup] = useState(false);

  const loadAll = useCallback(async () => {
    try {
      const [profRes, tasksRes] = await Promise.allSettled([
        agentApi.getMyProfile(),
        agentApi.getMyTasks(),
      ]);
      if (profRes.status === 'fulfilled') setProfile(profRes.value.data?.data);
      else setNeedsSetup(true);
      if (tasksRes.status === 'fulfilled') setTasks(tasksRes.value.data?.data || []);
    } catch {
      toast.error('Failed to load dashboard');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const handleToggleAvailability = async () => {
    if (!profile) return;
    setToggling(true);
    try {
      const res = await agentApi.setAvailability(!profile.isAvailable);
      setProfile(res.data?.data);
      toast.success(res.data?.data?.isAvailable ? 'You are now available' : 'You are now offline');
    } catch {
      toast.error('Failed to update availability');
    } finally {
      setToggling(false);
    }
  };

  const handleLogout = async () => {
    try { await authApi.logout(); } catch {}
    logout();
    navigate('/login');
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="space-y-3 w-80">
          {[1, 2, 3].map(i => <div key={i} className="h-20 bg-gray-100 rounded-2xl animate-pulse" />)}
        </div>
      </div>
    );
  }

  if (needsSetup) {
    return <SetupProfile onDone={() => { setNeedsSetup(false); loadAll(); }} />;
  }

  const activeTasks    = tasks.filter(t => ACTIVE_STATUSES.includes(t.status));
  const completedTasks = tasks.filter(t => COMPLETED_STATUSES.includes(t.status));

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-100 sticky top-0 z-30">
        <div className="max-w-lg mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-yellow-400 rounded-xl flex items-center justify-center flex-shrink-0">
              <Truck size={18} className="text-gray-900" />
            </div>
            <div>
              <p className="text-sm font-black text-gray-900 leading-tight">{profile?.name || 'Agent'}</p>
              <p className="text-xs text-gray-400">{profile?.vehicleType} · {profile?.phone}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {/* Availability toggle */}
            <button onClick={handleToggleAvailability} disabled={toggling}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold border transition-all disabled:opacity-50 ${
                profile?.isAvailable
                  ? 'bg-green-50 border-green-200 text-green-700'
                  : 'bg-gray-50 border-gray-200 text-gray-500'
              }`}>
              {profile?.isAvailable
                ? <><ToggleRight size={14} className="text-green-500" /> Online</>
                : <><ToggleLeft size={14} /> Offline</>
              }
            </button>
            <button onClick={handleLogout}
              className="p-2 text-gray-400 hover:text-red-500 transition-colors">
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </header>

      <div className="max-w-lg mx-auto px-4 py-4 space-y-4">
        {/* Stats */}
        <div className="grid grid-cols-3 gap-3">
          <div className="bg-white rounded-2xl p-3 text-center shadow-sm">
            <p className="text-2xl font-black text-gray-900">{profile?.totalDeliveries ?? 0}</p>
            <p className="text-xs text-gray-400 mt-0.5">Deliveries</p>
          </div>
          <div className="bg-white rounded-2xl p-3 text-center shadow-sm">
            <p className="text-2xl font-black text-yellow-500 flex items-center justify-center gap-1">
              <Star size={14} className="fill-yellow-400" />
              {profile?.avgRating ? profile.avgRating.toFixed(1) : '—'}
            </p>
            <p className="text-xs text-gray-400 mt-0.5">Rating</p>
          </div>
          <div className="bg-white rounded-2xl p-3 text-center shadow-sm">
            <p className={`text-2xl font-black ${activeTasks.length > 0 ? 'text-blue-500' : 'text-gray-300'}`}>
              {activeTasks.length}
            </p>
            <p className="text-xs text-gray-400 mt-0.5">Active</p>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex gap-1 bg-gray-100 p-1 rounded-xl">
          {[
            { key: 'active',    label: `Active (${activeTasks.length})` },
            { key: 'completed', label: `Completed (${completedTasks.length})` },
          ].map(t => (
            <button key={t.key} onClick={() => setTab(t.key)}
              className={`flex-1 py-2 rounded-lg text-sm font-semibold transition-all ${
                tab === t.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
              }`}>
              {t.label}
            </button>
          ))}
        </div>

        {/* Task list */}
        {tab === 'active' ? (
          activeTasks.length === 0 ? (
            <div className="text-center py-16 text-gray-300">
              <Truck size={48} className="mx-auto mb-3 opacity-40" />
              <p className="font-semibold text-gray-400">No active tasks</p>
              <p className="text-sm mt-1">
                {profile?.isAvailable
                  ? 'Waiting for new assignments...'
                  : 'Go online to receive tasks'}
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {activeTasks.map(task => (
                <TaskCard key={task.taskId} task={task} onStatusUpdated={loadAll} />
              ))}
            </div>
          )
        ) : (
          completedTasks.length === 0 ? (
            <div className="text-center py-16 text-gray-300">
              <CheckCircle size={48} className="mx-auto mb-3 opacity-40" />
              <p className="font-semibold text-gray-400">No completed tasks yet</p>
            </div>
          ) : (
            <div className="space-y-3">
              {completedTasks.map(task => (
                <TaskCard key={task.taskId} task={task} onStatusUpdated={loadAll} />
              ))}
            </div>
          )
        )}
      </div>
    </div>
  );
}
