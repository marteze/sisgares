# Descrições das imagens

Padrão de nome: `<referência original>_<descrição-em-kebab-case>.<extensão>`. A referência original (ex.: `disp_001`, `equip_005`) é a mesma dos CSVs em `dados/` (`DISP_ICONE_ARQUIVO` e `RECU_ICONE_ARQUIVO`). Para resolver o arquivo, basta localizar pelo prefixo antes de `_` + descrição.

O texto alternativo (alt) deve ser usado nos atributos `alt` do Frontend (eMAG/WCAG).

## Disposições (`icones-disposicao/`, JPG 400×240, fundo cinza com desenho branco em planta baixa)

| Referência original | Novo nome | Texto alternativo | Descrição |
|---|---|---|---|
| disp_001.jpg | disp_001_auditorio.jpg | Disposição auditório | Mesa diretora com 3 cadeiras à frente e plateia de cadeiras em 4 fileiras retas de 11, sem mesas. |
| disp_002.jpg | disp_002_auditorio-com-mesas.jpg | Disposição auditório com mesas | Mesa diretora com 3 cadeiras à frente e 9 mesas retangulares em 3 fileiras de 3, cada uma com 3 cadeiras voltadas para a frente. |
| disp_003.jpg | disp_003_espinha-de-peixe.jpg | Disposição espinha de peixe | Mesa diretora com 3 cadeiras à frente e dois blocos de cadeiras inclinados em diagonal, voltados para o centro, sem mesas. |
| disp_004.jpg | disp_004_espinha-de-peixe-com-mesas.jpg | Disposição espinha de peixe com mesas | Mesa diretora com 3 cadeiras à frente e dois blocos de mesas inclinadas em diagonal, com 3 cadeiras cada, voltadas para o centro. |
| disp_005.jpg | disp_005_mesa-unica.jpg | Disposição mesa única | Uma mesa retangular grande com 8 cadeiras em cada lado maior e 3 em cada cabeceira (22 lugares). |
| disp_006.jpg | disp_006_mesas-em-retangulo.jpg | Disposição mesas em retângulo | Mesas formando um retângulo vazado no centro, com 8 cadeiras em cada lado maior e 5 em cada lado menor, todas voltadas para dentro. |
| disp_007.jpg | disp_007_mesas-em-u.jpg | Disposição mesas em U | Mesas em formato de "U" com 8 cadeiras em cada braço e 5 na base, e mesa de presidência separada na abertura do U, com 3 cadeiras. |
| indefinido.jpg | indefinido_disposicao-nao-definida.jpg | Disposição não definida | Ponto de interrogação branco sobre fundo cinza; imagem padrão quando não há disposição ou ícone. |

## Recursos (`icones-recurso/`, PNG 24×24, ícones em estilo glossy)

| Referência original | Novo nome | Recursos que usam (RECU_ID) | Texto alternativo | Descrição |
|---|---|---|---|---|
| equip_001.png | equip_001_computador-desktop.png | 7 Microcomputador, 56 Computador Desktop | Computador desktop | Monitor com gabinete de computador ao lado. |
| equip_002.png | equip_002_notebook.png | 8 Notebook (c/ leitor DVD) | Notebook | Notebook aberto com tela e teclado. |
| equip_003.png | equip_003_netbook.png | 9 Netbook (s/ leitor DVD) | Netbook | Notebook compacto aberto, visto em perspectiva. |
| equip_004.png | equip_004_impressora.png | 10 Impressora | Impressora | Impressora com folha de papel saindo. |
| equip_005.png | equip_005_projetor-multimidia.png | 6 Projetor Multimídia Portátil, 13 Projetor Multimídia Fixo | Projetor multimídia | Projetor branco com lente frontal. |
| equip_006.png | equip_006_videoconferencia.png | 11 Videoconferência (CODEC 01), 76 Videoconferência (CODEC 02), 96 Videoconferência | Videoconferência | Monitor com câmera acoplada sobre a tela. |
| equip_007.png | equip_007_quadro-flip-chart.png | 12 Quadro / Flip chart | Quadro ou flip chart | Quadro branco em cavalete com gráfico de barras. |
| equip_008.png | equip_008_scanner.png | 14 Scanner | Scanner | Scanner de mesa com tampa. |
| equip_009.png | equip_009_material-de-escritorio.png | 15 Material de Escritório | Material de escritório | Caneta e lápis cruzados. |
| equip_010.png | equip_010_webcam.png | 16 Webcam | Webcam | Webcam esférica preta sobre base. |
| equip_011.png | equip_011_apresentador-multimidia.png | 36 Apresentador Multimídia | Apresentador multimídia | Controle passador de slides (apontador) em diagonal. |
| estrut_001.png | estrut_001_som-ambiente.png | 4 Som Ambiente | Som ambiente | Alto-falante preto redondo. |
| estrut_002.png | estrut_002_som-ambiente-com-microfone.png | 5 Som Ambiente c/ Microfone | Som ambiente com microfone | Microfone à frente de um alto-falante. |
| indefinido.png | indefinido_recurso-nao-definido.png | — | Recurso sem ícone definido | Ponto de interrogação branco sobre fundo escuro; ícone padrão quando o recurso não tem imagem. |
| serv_001.png | serv_001_copa-agua-e-cafe.png | 3 Serviço de Copa - Água e Café | Serviço de copa: água e café | Xícara de café ao lado de um copo de água. |
| serv_002.png | serv_002_copa-cafe.png | 2 Serviço de Copa - Café | Serviço de copa: café | Xícara de café sobre pires. |
| serv_003.png | serv_003_copa-agua.png | 1 Serviço de Copa - Água | Serviço de copa: água | Duas taças/copos de água. |
