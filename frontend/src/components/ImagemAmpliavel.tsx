import { useState } from 'react';
import { Lightbox } from './Lightbox';

interface Props {
  src: string;
  alt: string;
  width?: number;
  height?: number;
  className?: string;
}

// Imagem que, ao ser clicada, abre em um lightbox ampliado.
export function ImagemAmpliavel({ src, alt, width, height, className }: Props) {
  const [aberto, setAberto] = useState(false);

  return (
    <>
      <button
        type="button"
        className={`img-ampliavel ${className ?? ''}`}
        onClick={() => setAberto(true)}
        aria-label={`Ampliar imagem: ${alt}`}
        title="Clique para ampliar"
      >
        <img src={src} alt={alt} width={width} height={height} />
      </button>
      {aberto && <Lightbox src={src} alt={alt} onClose={() => setAberto(false)} />}
    </>
  );
}
