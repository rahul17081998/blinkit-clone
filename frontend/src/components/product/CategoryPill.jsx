import { useNavigate } from 'react-router-dom';

const CATEGORY_EMOJIS = {
  'dairy-eggs': '🥛',
  'milk': '🍼',
  'beverages': '🧃',
  'fruits-vegetables': '🥦',
  'snacks': '🍟',
  'bakery': '🍞',
  'cleaning': '🧹',
  'personal-care': '🧴',
  'meat-seafood': '🥩',
  'frozen': '🧊',
  'default': '🛒',
};

export default function CategoryPill({ category, active = false }) {
  const navigate = useNavigate();
  const emoji = CATEGORY_EMOJIS[category.slug] || CATEGORY_EMOJIS.default;

  return (
    <button
      onClick={() => navigate(`/category/${category.slug}`)}
      className={`flex-shrink-0 flex flex-col items-center gap-1 px-4 py-3 rounded-2xl border-2 transition-all
        ${active
          ? 'border-primary bg-primary/10 shadow-sm'
          : 'border-gray-100 bg-white hover:border-primary/50 hover:bg-primary/5'
        }`}
    >
      <span className="text-2xl">{emoji}</span>
      <span className={`text-xs font-semibold whitespace-nowrap ${active ? 'text-dark' : 'text-gray-600'}`}>
        {category.name}
      </span>
    </button>
  );
}
