# Seldon Fleet — Sistema de Monitoramento de Frotas de Dispositivos

Desafio Técnico para o processo seletivo do **Projeto SELDON** (EST/UEA & Motorola) — **Trilha de Segurança de Dispositivos Móveis e Automação de Validações**.

---

## 1. Sobre o Projeto
O **Seldon Fleet** é um aplicativo Android desenvolvido em **Jetpack Compose** e **Kotlin**, estruturado sob os princípios de **Clean Architecture** e **Domain-Driven Design (DDD)**. O sistema opera em modo estritamente **offline** (consome o arquivo local em `assets/frota.json`[span_0](start_span)[span_0](end_span)), sem nenhuma permissão de rede configurada no manifesto (**REQ-01**), garantindo total privacidade e conformidade com a LGPD.

---

## 2. Conformidade com os Requisitos do Desafio

* **REQ-01 (Leitura Local / Offline):** O arquivo de telemetria da frota é carregado diretamente do armazenamento de assets local (`AssetFleetSource`), permitindo funcionamento completo em modo avião[span_1](start_span)[span_1](end_span).
* **REQ-02 (Normalização de Energia):** Conversão determinística de `mWh` para `mAh` utilizando a tensão nominal (`nominalVoltage`) em registros heterogêneos (ex.: `DEV-0204`).
* **REQ-03 & REQ-05 (Tratamento de Anomalias e Exibição):** Registros com capacidade zero ou tempo de tela nulo (ex.: `DEV-0311`, `DEV-0745`) são rotulados de forma segura como `INDETERMINADO` (`SEM DADOS`), preservando a contagem íntegra dos 30 dispositivos na frota.
* **REQ-04 (Isolamento do Domínio):** A camada `domain/` é puramente em Kotlin, sem nenhuma dependência ou importação do framework Android (`import android.*`), validada estaticamente por testes de integração contínua.
* **REQ-06 & REQ-07 (Interface Compose e Favoritos):** Telas de listagem com filtros rápidos por status, crachás coloridos por gravidade, tela de detalhe com justificativa analítica e persistência local de favoritos via **Jetpack DataStore**[span_2](start_span)[span_2](end_span).
* **REQ-08 (Testes Unitários JVM):** Suíte robusta de testes cobrindo equivalência de unidades, divisores nulos, fronteiras exatas de limiares e precedência de sinais críticos.
* **REQ-09 & Trilha B (CI/CD e Segurança):** Pipeline configurado no **GitHub Actions** executando análise estática (Lint), testes unitários JVM e auditoria automatizada de hardening do manifesto e LGPD.
* **REQ-10 (Protocolo de Decisão):** Pré-registro documentado no arquivo `PROTOCOLO.md` contendo as premissas analíticas e ameaças à validade do modelo.

---

## 3. Arquitetura do Projeto

O código está segregado em camadas desacopladas (Anexo B do edital):
```text
br.edu.uea.seldonfleet/
├── data/       # DTOs, Mappers (normalizacao mWh/mAh), AssetSource e Repositorio DataStore
├── domain/     # Modelos puros, Contratos de Repositorio e Casos de Uso (ClassifyDevice)
└── ui/         # Telas Jetpack Compose (Lista, Detalhe, Temas e Formatadores de Justificativa)
