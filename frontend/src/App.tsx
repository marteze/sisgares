import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Home } from './pages/Home';
import { NovaReserva } from './pages/NovaReserva';
import { MinhasReservas } from './pages/MinhasReservas';
import { PainelGrade } from './pages/PainelGrade';
import { PainelAtendente } from './pages/PainelAtendente';
import { Config } from './pages/Config';

export function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/painel" element={<PainelGrade />} />
        <Route path="/nova-reserva" element={<NovaReserva />} />
        <Route path="/minhas-reservas" element={<MinhasReservas />} />
        <Route path="/atendente" element={<PainelAtendente />} />
        <Route path="/config" element={<Config />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}
