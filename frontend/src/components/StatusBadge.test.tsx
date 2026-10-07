import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { StatusBadge } from './StatusBadge';

describe('StatusBadge', () => {
  it('mostra o rótulo em português para cada status', () => {
    render(<StatusBadge status="EM_ANDAMENTO" />);
    expect(screen.getByText('Em andamento')).toBeInTheDocument();
  });

  it('aplica a classe de modificador correta', () => {
    const { container } = render(<StatusBadge status="PREVISTA" />);
    expect(container.querySelector('.badge--prevista')).not.toBeNull();
  });
});
