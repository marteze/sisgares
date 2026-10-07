import express from 'express';
import cors from 'cors';
import { env } from './config/env.js';
import { autenticar } from './middleware/auth.js';
import { router } from './routes/index.js';

export function createApp() {
  const app = express();

  app.use(cors({ origin: env.corsOrigin }));
  app.use(express.json());
  app.use(autenticar);

  app.use('/api', router);

  // Handler de erro padrão (regras de negócio já tratadas nos controllers).
  app.use(
    (
      err: unknown,
      _req: express.Request,
      res: express.Response,
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      _next: express.NextFunction
    ) => {
      console.error(err);
      res.status(500).json({ erro: 'Erro interno do servidor.' });
    }
  );

  return app;
}
