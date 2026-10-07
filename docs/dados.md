# Dados fornecidos

Os arquivos abaixo foram fornecidos na pasta do caso. São **fictícios** (sistema de
origem: Solare / MPF) e estão em `data/` (cópia de referência) e `backend/data/`
(consumidos pela API). Formato: CSV com cabeçalho, campos entre aspas, separador `,`.

| Arquivo | Linhas (dados) | Conteúdo (cada linha é...) |
|---------|---------------:|----------------------------|
| `dados-ambiente.csv` | 12 | Um ambiente físico (sala, auditório), com hierarquia pai/filho |
| `dados-disposicao.csv` | 7 | Um layout/disposição de sala (ex: mesas em U) |
| `dados-grupo-recurso.csv` | 3 | Um grupo de recurso: Serviço, Equipamento, Estrutura |
| `dados-recurso.csv` | 20 | Um recurso/serviço (água, café, projetor, notebook...) |
| `dados-envolvido.csv` | 4 | Um setor responsável, com e-mail (SMSG, SEART, SELOG, SESOT) |
| `dados-envolvido-ambiente.csv` | 22 | Vínculo: setor responsável por um ambiente |
| `dados-envolvido-recurso.csv` | 21 | Vínculo: setor responsável por um recurso |
| `dados-vinculo-recurso.csv` | 15 | Recurso fixo disponível em um ambiente |
| `dados-periodo-reserva.csv` | 199 | Período (início/término) de uma reserva |
| `dados-solicitacao.csv` | 477 | Recurso solicitado em uma reserva, com quantidade |

## Limitações conhecidas

- Campos vazios (ex: `AMBI_ID_PAI`, `SOLI_QTD`, `ENVO` sem linha).
- IDs em `periodo-reserva` e `solicitacao` vêm com separador de milhar (`14.207`) —
  precisam ser normalizados na importação (remover o ponto).
- Datas em formato `DD/MM/AAAA HH:MM:SS`.
- Não há tabela de usuários/solicitantes nos dados — será mockada no backend.
- A entidade "reserva" (RESE) não vem como arquivo próprio; é inferida pelos
  `RESE_ID` referenciados em `periodo-reserva` e `solicitacao`.

## Privacidade (LGPD)

Dados fictícios, sem PII real e sem informação sigilosa. E-mails de setores são
institucionais genéricos.
