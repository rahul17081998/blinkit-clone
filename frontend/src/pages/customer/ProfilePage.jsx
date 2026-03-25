import { useEffect, useState } from 'react';
import DatePicker from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';
import {
  User, MapPin, Plus, Trash2, Star, Edit2, Check, X,
  Home, Briefcase, Navigation, Phone, Calendar, Users, Mail, Shield, Camera, AlertTriangle,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { userApi } from '../../api/user.api';
import { useAuthStore } from '../../stores/authStore';
import { useProfileStore } from '../../stores/profileStore';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/layout/Header';
import FloatingCartBar from '../../components/cart/FloatingCartBar';

// ── Shared constants ──────────────────────────────────────────────────────────

const LABEL_ICONS = { HOME: Home, WORK: Briefcase, OTHER: Navigation };

const EMPTY_ADDR = {
  label: 'HOME', recipientName: '', recipientPhone: '',
  flatNo: '', building: '', street: '', area: '',
  city: '', state: '', pincode: '', landmark: '',
  lat: 0.0, lng: 0.0,
};

// ── Address Form Modal ────────────────────────────────────────────────────────

// Field defined OUTSIDE any component so its identity is stable across renders
function AddrField({ label, name, form, onChange, placeholder, required }) {
  return (
    <div>
      <label className="block text-xs font-semibold text-gray-500 mb-1">
        {label}{required && ' *'}
      </label>
      <input
        value={form[name] || ''}
        onChange={e => onChange(name, e.target.value)}
        placeholder={placeholder}
        required={required}
        className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
      />
    </div>
  );
}

function AddressModal({ initial, onClose, onSaved }) {
  const [form, setForm]   = useState(initial ? { ...initial } : { ...EMPTY_ADDR });
  const [saving, setSaving] = useState(false);
  const isEdit = !!initial?.addressId;

  const set = (k, v) => setForm(p => ({ ...p, [k]: v }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (isEdit) {
        await userApi.updateAddress(initial.addressId, form);
        toast.success('Address updated');
      } else {
        await userApi.addAddress(form);
        toast.success('Address added');
      }
      onSaved();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to save address');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 flex-shrink-0">
          <h3 className="text-base font-bold text-gray-900">
            {isEdit ? 'Edit Address' : 'Add New Address'}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="overflow-y-auto flex-1 p-6 space-y-4">
          {/* Label selector */}
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-2">Label *</label>
            <div className="flex gap-2">
              {['HOME', 'WORK', 'OTHER'].map(l => {
                const Icon = LABEL_ICONS[l];
                return (
                  <button type="button" key={l} onClick={() => set('label', l)}
                    className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-bold border transition-all ${
                      form.label === l
                        ? 'bg-yellow-400 border-yellow-400 text-gray-900'
                        : 'border-gray-200 text-gray-500 hover:border-gray-300'
                    }`}>
                    <Icon size={13} /> {l}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="col-span-2">
              <AddrField label="Recipient Name" name="recipientName" form={form} onChange={set}
                placeholder="Full name" required />
            </div>
            <div className="col-span-2">
              <AddrField label="Recipient Phone" name="recipientPhone" form={form} onChange={set}
                placeholder="10-digit mobile" required />
            </div>
            <AddrField label="Flat / House No." name="flatNo" form={form} onChange={set}
              placeholder="A-101" required />
            <AddrField label="Building / Society" name="building" form={form} onChange={set}
              placeholder="Green Park" required />
            <div className="col-span-2">
              <AddrField label="Street" name="street" form={form} onChange={set}
                placeholder="Street name (optional)" />
            </div>
            <AddrField label="Area / Locality" name="area" form={form} onChange={set}
              placeholder="Koramangala" required />
            <AddrField label="City" name="city" form={form} onChange={set}
              placeholder="Bangalore" required />
            <AddrField label="State" name="state" form={form} onChange={set}
              placeholder="Karnataka" required />
            <AddrField label="Pincode" name="pincode" form={form} onChange={set}
              placeholder="560001" required />
            <div className="col-span-2">
              <AddrField label="Landmark" name="landmark" form={form} onChange={set}
                placeholder="Near Metro Station (optional)" />
            </div>
          </div>

          <div className="flex gap-3 pt-2 sticky bottom-0 bg-white pb-1">
            <button type="button" onClick={onClose}
              className="flex-1 border border-gray-200 text-gray-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-gray-50">
              Cancel
            </button>
            <button type="submit" disabled={saving}
              className="flex-1 bg-yellow-400 text-gray-900 font-bold py-2.5 rounded-xl text-sm hover:bg-yellow-500 disabled:opacity-60">
              {saving ? 'Saving...' : isEdit ? 'Update' : 'Add Address'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Delete Account Confirmation Modal ────────────────────────────────────────

function DeleteAccountModal({ onClose, onConfirm, deleting }) {
  const [inputValue, setInputValue] = useState('');
  const confirmed = inputValue === 'DELETE';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl">
        {/* Header */}
        <div className="flex items-center gap-3 px-6 py-5 border-b border-red-100 bg-red-50 rounded-t-2xl">
          <div className="w-10 h-10 bg-red-100 rounded-full flex items-center justify-center flex-shrink-0">
            <AlertTriangle size={20} className="text-red-600" />
          </div>
          <div>
            <h3 className="text-base font-black text-red-700">Delete Account</h3>
            <p className="text-xs text-red-500 mt-0.5">This action cannot be undone</p>
          </div>
        </div>

        {/* Body */}
        <div className="px-6 py-5 space-y-4">
          <p className="text-sm text-gray-700 leading-relaxed">
            Permanently deleting your account will remove:
          </p>
          <ul className="text-sm text-gray-600 space-y-1.5 pl-1">
            <li className="flex items-start gap-2"><span className="text-red-400 mt-0.5">✕</span> Your profile and personal data</li>
            <li className="flex items-start gap-2"><span className="text-red-400 mt-0.5">✕</span> All saved addresses</li>
            <li className="flex items-start gap-2"><span className="text-red-400 mt-0.5">✕</span> Order history and wallet balance</li>
            <li className="flex items-start gap-2"><span className="text-red-400 mt-0.5">✕</span> All login sessions</li>
          </ul>

          <div className="bg-yellow-50 border border-yellow-200 rounded-xl px-4 py-3">
            <p className="text-xs font-semibold text-yellow-800">
              To confirm, type <span className="font-black tracking-widest">DELETE</span> below:
            </p>
            <input
              value={inputValue}
              onChange={e => setInputValue(e.target.value)}
              placeholder="Type DELETE to confirm"
              className="mt-2 w-full border border-yellow-300 rounded-lg px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-red-400"
              autoFocus
            />
          </div>
        </div>

        {/* Footer */}
        <div className="flex gap-3 px-6 pb-6">
          <button
            onClick={onClose}
            disabled={deleting}
            className="flex-1 border border-gray-200 text-gray-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-gray-50 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={!confirmed || deleting}
            className="flex-1 bg-red-600 text-white font-bold py-2.5 rounded-xl text-sm hover:bg-red-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            {deleting ? 'Deleting...' : 'Delete My Account'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Left panel: profile summary card + addresses list ─────────────────────────

function ProfileCard({ profile, email }) {
  const initials = (profile?.firstName || email || 'U')[0].toUpperCase();
  const fullName = [profile?.firstName, profile?.lastName].filter(Boolean).join(' ') || 'Your Name';
  return (
    <div className="bg-white rounded-2xl p-5 shadow-sm flex items-center gap-4">
      <div className="w-14 h-14 rounded-2xl bg-yellow-400 flex items-center justify-center flex-shrink-0 overflow-hidden">
        {profile?.profileImageUrl
          ? <img src={profile.profileImageUrl} alt="Profile" className="w-full h-full object-cover" />
          : <span className="text-2xl font-black text-gray-900">{initials}</span>
        }
      </div>
      <div className="min-w-0">
        <p className="text-base font-black text-gray-900 truncate">{fullName}</p>
        <p className="text-xs text-gray-400 truncate">{email}</p>
        {profile?.phone && (
          <p className="text-xs text-gray-500 mt-0.5">{profile.phone}</p>
        )}
      </div>
    </div>
  );
}

function AddressesList({ addresses, loading, onAdd, onEdit, onDelete, onSetDefault, deleting, settingDefault }) {
  if (loading) return (
    <div className="space-y-2">
      {[1, 2].map(i => <div key={i} className="h-20 bg-gray-100 rounded-2xl animate-pulse" />)}
    </div>
  );

  return (
    <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
        <h3 className="text-sm font-bold text-gray-700 flex items-center gap-1.5">
          <MapPin size={14} className="text-yellow-500" /> Saved Addresses
        </h3>
        <button onClick={onAdd}
          className="flex items-center gap-1 text-xs font-semibold text-yellow-600 hover:text-yellow-700">
          <Plus size={13} /> Add
        </button>
      </div>

      {addresses.length === 0 ? (
        <div className="text-center py-8 text-gray-400 px-4">
          <MapPin size={28} className="mx-auto mb-2 opacity-30" />
          <p className="text-xs font-medium">No saved addresses</p>
          <button onClick={onAdd}
            className="mt-2 text-xs font-semibold text-yellow-600 hover:underline">
            + Add your first address
          </button>
        </div>
      ) : (
        <div className="divide-y divide-gray-50">
          {addresses.map(addr => {
            const Icon = LABEL_ICONS[addr.label] || MapPin;
            const lines = [addr.flatNo, addr.building, addr.area, addr.city]
              .filter(Boolean).join(', ');
            return (
              <div key={addr.addressId} className="px-4 py-3">
                <div className="flex items-start gap-2">
                  <div className={`w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5 ${
                    addr.isDefault ? 'bg-yellow-100' : 'bg-gray-100'
                  }`}>
                    <Icon size={13} className={addr.isDefault ? 'text-yellow-600' : 'text-gray-500'} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5">
                      <span className="text-xs font-bold text-gray-700">{addr.label}</span>
                      {addr.isDefault && (
                        <span className="text-[10px] font-bold bg-yellow-400 text-gray-900 px-1.5 py-0.5 rounded-full">
                          Default
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-gray-500 mt-0.5 leading-snug line-clamp-2">{lines}</p>
                  </div>
                  <div className="flex items-center gap-1 flex-shrink-0">
                    <button onClick={() => onEdit(addr)}
                      className="p-1 text-gray-400 hover:text-blue-500 transition-colors">
                      <Edit2 size={13} />
                    </button>
                    <button onClick={() => onDelete(addr.addressId)}
                      disabled={deleting === addr.addressId}
                      className="p-1 text-gray-400 hover:text-red-500 transition-colors disabled:opacity-40">
                      <Trash2 size={13} />
                    </button>
                  </div>
                </div>
                {!addr.isDefault && (
                  <button onClick={() => onSetDefault(addr.addressId)}
                    disabled={settingDefault === addr.addressId}
                    className="mt-1.5 ml-9 flex items-center gap-1 text-[11px] font-semibold text-yellow-600 hover:text-yellow-700 disabled:opacity-50">
                    <Star size={11} />
                    {settingDefault === addr.addressId ? 'Setting...' : 'Set as default'}
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ── Date of Birth picker — calendar popup with month/year dropdowns ───────────

function DateOfBirthPicker({ value, onChange }) {
  const currentYear = new Date().getFullYear();

  // Convert stored "YYYY-MM-DD" string ↔ Date object
  const toDate   = str => str ? new Date(str + 'T00:00:00') : null;
  const fromDate = dt  => dt
    ? `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`
    : '';

  return (
    <div>
      <label className="block text-xs font-semibold text-gray-500 mb-1 flex items-center gap-1">
        <Calendar size={11} /> Date of Birth
      </label>
      <DatePicker
        selected={toDate(value)}
        onChange={dt => onChange('dateOfBirth', fromDate(dt))}
        dateFormat="dd MMM yyyy"
        showMonthDropdown
        showYearDropdown
        dropdownMode="select"
        maxDate={new Date(currentYear - 10, 11, 31)}
        minDate={new Date(currentYear - 100, 0, 1)}
        placeholderText="Select date of birth"
        className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
        wrapperClassName="w-full"
        popperPlacement="bottom-start"
      />
    </div>
  );
}

// ── Profile edit form fields — defined OUTSIDE to keep stable identity ────────

function ProfileField({ label, name, type, form, onChange, options, icon: Icon }) {
  return (
    <div>
      <label className="block text-xs font-semibold text-gray-500 mb-1 flex items-center gap-1">
        {Icon && <Icon size={11} />} {label}
      </label>
      {options ? (
        <select
          value={form[name] || ''}
          onChange={e => onChange(name, e.target.value)}
          className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400 bg-white"
        >
          <option value="">Select {label}</option>
          {options.map(o => (
            <option key={o} value={o}>{o.replace(/_/g, ' ')}</option>
          ))}
        </select>
      ) : (
        <input
          type={type || 'text'}
          value={form[name] || ''}
          onChange={e => onChange(name, e.target.value)}
          className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-400"
        />
      )}
    </div>
  );
}

// ── Right panel: edit profile form ───────────────────────────────────────────

const ROLE_LABELS = {
  CUSTOMER:       { label: 'Customer',       color: 'bg-blue-100 text-blue-700' },
  ADMIN:          { label: 'Admin',           color: 'bg-red-100 text-red-700' },
  DELIVERY_AGENT: { label: 'Delivery Agent', color: 'bg-green-100 text-green-700' },
};

function EditProfilePanel({ profile, email, role, onSaved }) {
  const [form, setForm]     = useState({
    firstName: '', lastName: '', phone: '', dateOfBirth: '', gender: '',
  });
  const [saving, setSaving]         = useState(false);
  const [dirty, setDirty]           = useState(false);
  const [photoUploading, setPhotoUploading] = useState(false);

  // Sync form when profile loads
  useEffect(() => {
    if (profile) {
      setForm({
        firstName:   profile.firstName   || '',
        lastName:    profile.lastName    || '',
        phone:       profile.phone       || '',
        dateOfBirth: profile.dateOfBirth || '',
        gender:      profile.gender      || '',
      });
      setDirty(false);
    }
  }, [profile]);

  const handleChange = (name, value) => {
    setForm(prev => ({ ...prev, [name]: value }));
    setDirty(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const res = await userApi.updateProfile(form);
      onSaved(res.data?.data);
      setDirty(false);
      toast.success('Profile updated!');
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to update profile');
    } finally {
      setSaving(false);
    }
  };

  const handlePhotoChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setPhotoUploading(true);
    try {
      const res = await userApi.uploadProfilePhoto(file);
      onSaved(res.data?.data);
      toast.success('Profile photo updated!');
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Photo upload failed');
    } finally {
      setPhotoUploading(false);
      e.target.value = '';
    }
  };

  const handleReset = () => {
    if (profile) {
      setForm({
        firstName:   profile.firstName   || '',
        lastName:    profile.lastName    || '',
        phone:       profile.phone       || '',
        dateOfBirth: profile.dateOfBirth || '',
        gender:      profile.gender      || '',
      });
      setDirty(false);
    }
  };

  if (!profile) return (
    <div className="bg-white rounded-2xl p-6 shadow-sm space-y-4">
      {[1,2,3,4,5].map(i => <div key={i} className="h-12 bg-gray-100 rounded-xl animate-pulse" />)}
    </div>
  );

  return (
    <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
      <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
        <h3 className="text-sm font-bold text-gray-800 flex items-center gap-2">
          <User size={15} className="text-yellow-500" /> Edit Profile
        </h3>
        {dirty && (
          <span className="text-[11px] text-yellow-600 font-semibold bg-yellow-50 px-2 py-1 rounded-full">
            Unsaved changes
          </span>
        )}
      </div>

      <div className="p-6 space-y-4">

        {/* 0. Profile photo */}
        <div className="flex items-center gap-4">
          <div className="relative flex-shrink-0">
            <div className="w-16 h-16 rounded-2xl bg-yellow-400 overflow-hidden flex items-center justify-center">
              {profile?.profileImageUrl
                ? <img src={profile.profileImageUrl} alt="Profile" className="w-full h-full object-cover" />
                : <span className="text-2xl font-black text-gray-900">
                    {(profile?.firstName || email || 'U')[0].toUpperCase()}
                  </span>
              }
            </div>
            <label className={`absolute -bottom-1 -right-1 w-6 h-6 rounded-full flex items-center justify-center cursor-pointer shadow-md transition-colors ${
              photoUploading ? 'bg-gray-300 cursor-not-allowed' : 'bg-gray-900 hover:bg-gray-700'
            }`}>
              {photoUploading
                ? <div className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                : <Camera size={12} className="text-white" />
              }
              <input type="file" accept="image/*" className="hidden" onChange={handlePhotoChange} disabled={photoUploading} />
            </label>
          </div>
          <div>
            <p className="text-sm font-semibold text-gray-800">Profile Photo</p>
            <p className="text-xs text-gray-400 mt-0.5">JPG, PNG or WebP · Max 5 MB</p>
          </div>
        </div>

        <div className="border-t border-gray-100" />

        {/* 1. Name */}
        <div className="grid grid-cols-2 gap-4">
          <ProfileField label="First Name" name="firstName" form={form} onChange={handleChange} />
          <ProfileField label="Last Name"  name="lastName"  form={form} onChange={handleChange} />
        </div>

        {/* 2. Email + Account Type (read-only) */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1 flex items-center gap-1">
              <Mail size={11} /> Email
            </label>
            <div className="border border-gray-100 bg-gray-50 rounded-xl px-3 py-2.5 text-sm text-gray-500 truncate">
              {email}
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1 flex items-center gap-1">
              <Shield size={11} /> Account Type
            </label>
            <div className="flex items-center border border-gray-100 bg-gray-50 rounded-xl px-3 py-2.5">
              {role && (
                <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${ROLE_LABELS[role]?.color || 'bg-gray-100 text-gray-600'}`}>
                  {ROLE_LABELS[role]?.label || role}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* 3. Phone + Date of Birth */}
        <div className="grid grid-cols-2 gap-4">
          <ProfileField label="Phone Number" name="phone" type="tel" form={form} onChange={handleChange} icon={Phone} />
          <DateOfBirthPicker value={form.dateOfBirth} onChange={handleChange} />
        </div>

        {/* 4. Gender */}
        <ProfileField label="Gender" name="gender" form={form} onChange={handleChange} icon={Users}
          options={['MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY']} />

        <div className="flex gap-3 pt-2">
          <button
            onClick={handleReset}
            disabled={!dirty}
            className="flex-1 border border-gray-200 text-gray-600 font-semibold py-2.5 rounded-xl text-sm hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            Reset
          </button>
          <button
            onClick={handleSave}
            disabled={saving || !dirty}
            className="flex-1 flex items-center justify-center gap-2 bg-yellow-400 text-gray-900 font-bold py-2.5 rounded-xl text-sm hover:bg-yellow-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <Check size={15} />
            {saving ? 'Saving...' : 'Save Changes'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function ProfilePage() {
  const { email, role, logout } = useAuthStore();
  const setProfile = useProfileStore(s => s.setProfile);
  const clearProfile = useProfileStore(s => s.clearProfile);
  const navigate = useNavigate();

  const [profile,            setProfileState]     = useState(null);
  const [profileLoading,     setProfileLoading]   = useState(true);
  const [addresses,          setAddresses]        = useState([]);
  const [addrLoading,        setAddrLoading]      = useState(true);
  const [modal,              setModal]            = useState(null);
  const [deleting,           setDeleting]         = useState(null);
  const [settingDefault,     setSettingDefault]   = useState(null);
  const [showDeleteModal,    setShowDeleteModal]  = useState(false);
  const [deletingAccount,    setDeletingAccount]  = useState(false);

  // Load profile
  useEffect(() => {
    userApi.getProfile()
      .then(r => {
        const p = r.data?.data;
        setProfile(p);       // update header avatar
        setProfileState(p);
      })
      .catch(() => toast.error('Failed to load profile'))
      .finally(() => setProfileLoading(false));
  }, []);

  // Load addresses
  const loadAddresses = () => {
    setAddrLoading(true);
    userApi.getAddresses()
      .then(r => setAddresses(r.data?.data || []))
      .catch(() => toast.error('Failed to load addresses'))
      .finally(() => setAddrLoading(false));
  };
  useEffect(() => { loadAddresses(); }, []);

  const handleDelete = async (addressId) => {
    setDeleting(addressId);
    try {
      await userApi.deleteAddress(addressId);
      toast.success('Address removed');
      setAddresses(p => p.filter(a => a.addressId !== addressId));
    } catch {
      toast.error('Failed to delete');
    } finally {
      setDeleting(null);
    }
  };

  const handleSetDefault = async (addressId) => {
    setSettingDefault(addressId);
    try {
      await userApi.setDefaultAddress(addressId);
      toast.success('Default address set');
      loadAddresses();
    } catch {
      toast.error('Failed to set default');
    } finally {
      setSettingDefault(null);
    }
  };

  const handleDeleteAccount = async () => {
    setDeletingAccount(true);
    try {
      await userApi.deleteAccount();
      toast.success('Account deleted. Goodbye!');
      clearProfile();
      logout();
      navigate('/login', { replace: true });
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to delete account');
      setDeletingAccount(false);
    }
  };

  return (
    <div className="min-h-screen bg-blinkit-bg">
      <Header />

      {modal && (
        <AddressModal
          initial={modal === 'new' ? null : modal}
          onClose={() => setModal(null)}
          onSaved={() => { setModal(null); loadAddresses(); }}
        />
      )}

      {showDeleteModal && (
        <DeleteAccountModal
          onClose={() => setShowDeleteModal(false)}
          onConfirm={handleDeleteAccount}
          deleting={deletingAccount}
        />
      )}

      <main className="max-w-5xl mx-auto px-4 py-6 pb-24">
        <h1 className="text-xl font-black text-gray-900 mb-5">My Account</h1>

        <div className="flex flex-col lg:flex-row gap-5 items-start">

          {/* ── Left column: profile summary + addresses ── */}
          <div className="w-full lg:w-72 flex-shrink-0 space-y-4">
            {profileLoading ? (
              <div className="h-24 bg-white rounded-2xl animate-pulse shadow-sm" />
            ) : (
              <ProfileCard profile={profile} email={email} />
            )}

            <AddressesList
              addresses={addresses}
              loading={addrLoading}
              onAdd={() => setModal('new')}
              onEdit={addr => setModal(addr)}
              onDelete={handleDelete}
              onSetDefault={handleSetDefault}
              deleting={deleting}
              settingDefault={settingDefault}
            />
          </div>

          {/* ── Right column: edit profile form ── */}
          <div className="flex-1 min-w-0">
            <EditProfilePanel
              profile={profileLoading ? null : profile}
              email={email}
              role={role}
              onSaved={updated => { setProfileState(updated); setProfile(updated); }}
            />
          </div>

        </div>

        {/* ── Danger Zone ── */}
        <div className="mt-8 border border-red-200 rounded-2xl overflow-hidden">
          <div className="bg-red-50 px-5 py-3 flex items-center gap-2">
            <AlertTriangle size={15} className="text-red-500" />
            <span className="text-sm font-bold text-red-700">Danger Zone</span>
          </div>
          <div className="px-5 py-4 flex items-center justify-between gap-4 bg-white">
            <div>
              <p className="text-sm font-semibold text-gray-800">Delete my account</p>
              <p className="text-xs text-gray-500 mt-0.5">
                Permanently removes your account, profile, addresses, and all data. This cannot be undone.
              </p>
            </div>
            <button
              onClick={() => setShowDeleteModal(true)}
              className="flex-shrink-0 bg-red-600 hover:bg-red-700 text-white text-sm font-bold px-4 py-2 rounded-xl transition-colors"
            >
              Delete Account
            </button>
          </div>
        </div>

      </main>

      <FloatingCartBar />
    </div>
  );
}
