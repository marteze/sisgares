import { useEffect } from 'react';

interface Props {
  src: string;
  alt: string;
  onClose: () => void;
}

// Overlay que exibe a imagem ampliada. Fecha com clique, botão ou tecla Esc.
export function Lightbox({ src, alt, onClose }: Props) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    // Impede rolagem do fundo enquanto aberto.
    const anterior = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = anterior;
    };
  }, [onClose]);

  return (
    <div
      className="lightbox"
      role="dialog"
      aria-modal="true"
      aria-label={`Imagem ampliada: ${alt}`}
      onClick={onClose}
    >
      <button
        type="button"
        className="lightbox__fechar"
        aria-label="Fechar imagem ampliada"
        onClick={onClose}
      >
        ×
      </button>
      <img
        className="lightbox__img"
        src={src}
        alt={alt}
        onClick={(e) => e.stopPropagation()}
      />
    </div>
  );
}
