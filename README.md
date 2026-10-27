MONI-T (Seldon Fleet) — Monitoramento e Diagnóstico de Telemetria Mobile

Aplicativo Android nativo desenvolvido para o desafio técnico do Projeto SELDON (EST/UEA & Motorola), com foco em diagnóstico de telemetria, classificação de dispositivos e automação de validações de segurança.

Trilha escolhida: Trilha B — Segurança de Dispositivos Móveis e Automação de Validações.

1. Visão geral

O MONI-T (Seldon Fleet) é uma aplicação desenvolvida em Kotlin e Jetpack Compose para consultar e classificar uma frota de dispositivos móveis a partir de dados locais.

O projeto utiliza uma arquitetura em camadas, com separação entre interface, acesso a dados e regras de domínio. A classificação é determinística: os mesmos dados de entrada, avaliados pela mesma regra, produzem o mesmo resultado.

Principais funcionalidades:

- Leitura local da frota a partir de "app/src/main/assets/frota.json".
- Normalização de consumo energético entre mAh e mWh.
- Classificação dos dispositivos em "OK", "ATENCAO", "CRITICO" ou "INDETERMINADO".
- Listagem, filtros por status, contagens e consulta de detalhes.
- Justificativas de classificação com valores observados e limiares.
- Favoritos persistidos localmente por meio do Jetpack DataStore.
- Testes unitários na JVM e verificações automatizadas no pipeline de CI.

A aplicação não precisa de conexão de rede para carregar a frota fornecida. O manifesto não declara a permissão "android.permission.INTERNET", conforme o requisito de um app offline.

2. Capturas de tela

As imagens abaixo registram as telas do aplicativo executado em um dispositivo físico.

Lista da frota e filtros| Detalhe e diagnóstico
"Tela de lista" (docs/screenshots/tela_lista.png)| "Tela de detalhe" (docs/screenshots/tela_detalhe.png)

A tela de lista apresenta os dispositivos e permite filtrar os resultados por status e favoritos. A tela de detalhe exibe atributos do dispositivo, métricas de telemetria e a justificativa da classificação.

A frota de referência contém 30 registros. Para os dados fornecidos, a classificação esperada é de 16 dispositivos "OK", 8 "ATENCAO", 4 "CRITICO" e 2 "INDETERMINADO".

3. Arquitetura e decisões de projeto

O projeto está organizado em três camadas:

br.edu.uea.seldonfleet/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── data/
│   ├── dto/
│   ├── mapper/
│   ├── source/
│   └── repository/
└── ui/
    ├── list/
    ├── detail/
    ├── theme/
    └── StatusFormatter.kt

Domain

Contém os modelos, o contrato do repositório e a regra de classificação "ClassifyDevice", implementada em Kotlin sem dependências diretas de "android.*", JSON ou interface gráfica.

Data

É responsável pela leitura do arquivo local, conversão dos registros, normalização das unidades e persistência dos favoritos.

UI

Utiliza Jetpack Compose e ViewModel para apresentar a frota, os filtros e os detalhes. A tradução do estado interno "INDETERMINADO" para o rótulo visual "SEM DADOS" fica na camada de interface.

A dependência entre as camadas aponta para o domínio: a infraestrutura implementa o contrato definido pelo domínio, enquanto a regra de classificação permanece independente do Android.

4. Normalização e classificação

O consumo energético é normalizado para mAh antes do cálculo.

- Quando a unidade é "mAh", o valor é mantido.
- Quando a unidade é "mWh", o valor é dividido pela tensão nominal positiva e válida, em volts.
- Unidades inválidas, valores energéticos negativos ou dados necessários inválidos não devem produzir resultados numéricos arbitrários.

O dreno absoluto é calculado a partir do consumo normalizado e do tempo de tela ligada. O dreno relativo considera também a capacidade nominal da bateria.

A classificação considera três sinais:

Status| Condição de disparo
"CRITICO"| Dreno relativo ≥ 12%/h, ou pelo menos 3 falhas em 7 dias, ou jank ≥ 15%
"ATENCAO"| Dreno relativo ≥ 7%/h, ou pelo menos 1 falha em 7 dias, ou jank ≥ 8%
"OK"| Nenhuma condição de atenção ou crítica é atingida
"INDETERMINADO"| Métricas necessárias ausentes ou inválidas, ou impossibilidade de calcular com segurança

O estado "INDETERMINADO" é apresentado na interface como "SEM DADOS". Os dispositivos com telemetria insuficiente permanecem visíveis na frota.

A ordem de precedência, as decisões sobre dados incompletos, os exemplos e as ameaças à validade estão documentados em ""PROTOCOLO.md"" (PROTOCOLO.md).

5. Como compilar e testar

Pré-requisitos

- JDK 17.
- Android SDK configurado no ambiente de compilação.
- Gradle Wrapper incluído no repositório.

Verificar o isolamento do domínio

grep -r "import android" app/src/main/java/br/edu/uea/seldonfleet/domain/

Resultado esperado: nenhuma ocorrência.

Executar os testes unitários

./gradlew test

A suíte inclui testes de normalização de unidades, registros incompletos, fronteiras dos limiares e verificações de segurança.

Executar o Android Lint

./gradlew lintDebug

Gerar o APK de debug

./gradlew assembleDebug

O arquivo gerado fica em:

"app/build/outputs/apk/debug/app-debug.apk"

O workflow do GitHub Actions também gera e publica o APK como artefato para download.

Nota sobre o ambiente: a execução local dos comandos Gradle depende da configuração do Android SDK. O pipeline de CI utiliza seu próprio ambiente de compilação, independente da configuração local do desenvolvedor.

6. Integração contínua

O workflow ".github/workflows/android-ci.yml" é executado em eventos de push para "main" e em pull requests direcionados a essa branch.

As etapas incluem:

1. Configuração do JDK 17.
2. Verificação de isolamento arquitetural do domínio.
3. Execução do Android Lint.
4. Execução dos testes unitários na JVM.
5. Compilação do APK de debug.
6. Publicação do APK como artefato.

A situação da última execução pode ser consultada na aba Actions do repositório.

7. Trilha B — Segurança e automação

Configurações do manifesto

O manifesto contém os seguintes controles:

- "android:allowBackup="false"".
- "android:usesCleartextTraffic="false"".
- Ausência da permissão "android.permission.INTERNET".
- Declaração explícita de "android:exported" para a atividade principal.

A atividade principal é exportada para permitir o lançamento normal do aplicativo pelo sistema Android. A configuração não deve ser interpretada como uma auditoria completa de toda a superfície de ataque.

Verificação automatizada de logs

A suíte "SecurityAuditTest.kt" verifica padrões de código associados à exposição de identificadores de dispositivos em logs e também verifica o isolamento arquitetural do domínio.

Essa verificação estática ajuda a detectar regressões nos padrões cobertos pelos testes. Entretanto, não substitui uma análise completa de fluxo de dados, uma auditoria dinâmica ou uma revisão de segurança independente.

Considerações sobre privacidade

Embora o arquivo de referência não contenha nomes civis ou CPFs, identificadores de dispositivos podem permitir identificação indireta quando associados a outras fontes. Horários de contato e métricas de uso também podem revelar padrões de atividade.

Por isso, o projeto evita a permissão de rede e inclui verificações voltadas à exposição de identificadores em logs. Esses controles representam medidas técnicas de redução de risco, não uma declaração de conformidade integral com a LGPD.

As decisões, os riscos e as limitações estão descritos em ""PROTOCOLO.md"" (PROTOCOLO.md).

8. Dificuldades e decisões de engenharia

Desenvolvimento em ambiente móvel

O projeto foi desenvolvido utilizando Termux em um smartphone, com Git e GitHub para versionamento e GitHub Actions para automação de testes e compilação.

Esse fluxo permitiu trabalhar sem depender de uma instalação local completa do Android Studio, utilizando o pipeline remoto para gerar o APK.

Separação entre domínio e interface

A regra de classificação permanece fora dos componentes visuais. Isso facilita testar os cálculos e a precedência dos sinais sem executar a interface Android.

Tratamento de dados incompletos

A implementação distingue métricas ausentes de valores numéricos iguais a zero. Essa distinção evita tratar automaticamente a ausência de telemetria como um valor medido e permite classificar explicitamente registros que não oferecem dados suficientes para uma conclusão segura.

9. Declaração de uso de inteligência artificial

As ferramentas e contribuições descritas nesta seção devem refletir o processo realizado durante o desenvolvimento.

- Google Gemini: utilizado como apoio ao desenvolvimento e à revisão do projeto.
- ChatGPT: utilizado como apoio à revisão técnica, análise de requisitos e identificação de casos de borda.

A assistência de IA foi utilizada em atividades de desenvolvimento e revisão. A condução do projeto, a integração dos arquivos, o versionamento, a execução do pipeline e a validação funcional do APK fazem parte do processo de desenvolvimento realizado por mim (Desenvolvedor).

As decisões técnicas e os resultados devem ser avaliados a partir do código, dos testes, da documentação e do histórico de commits do repositório.

10. O que eu, desenvolvedor do  MONI-T, faria em seguida

Se o projeto fosse ampliado após o escopo do desafio, eu priorizaria:

1. Ampliar os testes de dados incompletos: cobrir sistematicamente campos ausentes, valores inválidos e combinações de sinais.
2. Aprimorar a validação estática de segurança: reduzir as limitações da análise baseada em padrões e avaliar ferramentas capazes de acompanhar o fluxo de dados.
3. Investigar o desempenho da interface: medir o custo de renderização e de atualização dos dados antes de realizar otimizações.
4. Refinar o modelo de telemetria: separar consumo com tela ligada e consumo em segundo plano, quando esses dados estiverem disponíveis.

Essas melhorias são possibilidades futuras, não funcionalidades que o projeto atual afirma já possuir.

---
