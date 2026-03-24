import { useRef, useState } from 'react';
import { X, Upload, ImageIcon } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../../api/axios';

export default function ImageUploader({ value, onChange, label = 'Image' }) {
  const [uploading, setUploading] = useState(false);
  const [dragOver,  setDragOver]  = useState(false);
  const inputRef = useRef(null);

  const uploadFile = async (file) => {
    if (!file) return;
    if (!file.type.startsWith('image/')) { toast.error('Please select an image file'); return; }
    if (file.size > 5 * 1024 * 1024)    { toast.error('Image must be under 5 MB'); return; }

    setUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);

      // POST to our own backend — backend forwards to Cloudinary
      const res  = await api.post('/api/images/upload', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });

      const url = res.data?.data?.url;
      if (url) {
        onChange(url);
        toast.success('Image uploaded');
      } else {
        toast.error('Upload failed — no URL returned');
      }
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Image upload failed');
    } finally {
      setUploading(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    uploadFile(e.dataTransfer.files[0]);
  };

  return (
    <div>
      <label className="block text-xs font-semibold text-gray-600 mb-1">{label}</label>

      {value ? (
        <div className="relative w-full h-36 rounded-xl overflow-hidden border border-gray-200 bg-gray-50">
          <img src={value} alt="preview" className="w-full h-full object-contain" />
          <button
            type="button"
            onClick={() => onChange('')}
            className="absolute top-2 right-2 bg-white/90 hover:bg-red-50 text-gray-600 hover:text-red-500 rounded-full p-1 shadow transition-colors"
          >
            <X size={14} />
          </button>
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            className="absolute bottom-2 right-2 bg-white/90 text-gray-600 hover:text-yellow-600 text-xs font-semibold px-2 py-1 rounded-lg shadow transition-colors flex items-center gap-1"
          >
            <Upload size={11} /> Change
          </button>
        </div>
      ) : (
        <div
          onClick={() => !uploading && inputRef.current?.click()}
          onDragOver={e => { e.preventDefault(); setDragOver(true); }}
          onDragLeave={() => setDragOver(false)}
          onDrop={handleDrop}
          className={`w-full h-36 rounded-xl border-2 border-dashed flex flex-col items-center justify-center gap-2 transition-colors ${
            uploading  ? 'border-gray-200 bg-gray-50 cursor-not-allowed' :
            dragOver   ? 'border-yellow-400 bg-yellow-50 cursor-copy' :
                         'border-gray-200 bg-gray-50 hover:border-yellow-400 hover:bg-yellow-50 cursor-pointer'
          }`}
        >
          {uploading ? (
            <>
              <div className="w-6 h-6 border-2 border-yellow-400 border-t-transparent rounded-full animate-spin" />
              <p className="text-xs text-gray-400 font-medium">Uploading…</p>
            </>
          ) : (
            <>
              <ImageIcon size={24} className="text-gray-300" />
              <p className="text-xs text-gray-500 font-medium">Click or drag image here</p>
              <p className="text-[11px] text-gray-400">PNG, JPG, WEBP · max 5 MB</p>
            </>
          )}
        </div>
      )}

      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={e => { uploadFile(e.target.files[0]); e.target.value = ''; }}
      />
    </div>
  );
}
