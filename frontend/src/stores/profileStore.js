import { create } from 'zustand';

export const useProfileStore = create((set) => ({
  profileImageUrl: null,
  setProfile: (profile) => set({ profileImageUrl: profile?.profileImageUrl || null }),
  clearProfile: () => set({ profileImageUrl: null }),
}));
