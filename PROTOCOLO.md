# Protocolo de Decisões e Especificação de Domínio — Seldon Fleet

Este documento estabelece o pré-registro dos critérios de normalização, tratamento de anomalias de dados, regras de classificação de saúde da frota e ameaças à validade adotados na arquitetura do **Seldon Fleet** (REQ-10).

---

## 1. Tratamento de Defeitos na Ingestão e Normalização (Seção 3.3)

O arquivo `frota.json` representa telemetria real e contém inconsistências estruturais que devem ser tratadas de forma determinística antes ou durante a avaliação de domínio.

### 1.1. Defeito 1 — Inconsistência na Unidade de Energia (`mAh` vs. `mWh`)
* **Problema observado:** Parte dos registros reporta `energyConsumed` em miliampere-hora (`mAh`) e parte em miliwatt-hora (`mWh`, com variações de caixa como `"mwh"`). Comparar ou calcular derivadas sem conversão introduz distorção proporcional à tensão nominal (~3,80x a 3,88x).
* **Decisão adotada:** Na camada de mapeamento (`data/mapper`), a unidade é normalizada (ignorando maiúsculas/minúsculas).
  * Quando `energyUnit` equivale a `mAh`: $\text{energiaMah} = \text{energyConsumed}$.
  * Quando `energyUnit` equivale a `mWh` e $\text{nominalVoltage} > 0$:
    $$\text{energiaMah} = \frac{\text{energyConsumed}}{\text{nominalVoltage}}$$
  * Caso a unidade seja `mWh` e `nominalVoltage` seja ausente ou $\le 0$, ou caso a unidade seja desconhecida, a normalização é marcada como inválida e o dispositivo recebe status `INDETERMINADO`.

### 1.2. Defeito 2 — Registros Incompletos e Divisores Nulos
* **Problema observado:** Existem registros com `batteryCapacityMah <= 0` ou ausente (ex.: `DEV-0311` com capacidade `0`) e registros com `screenOnMinutes <= 0` (ex.: `DEV-0745` com tempo de tela `0`), além da possibilidade de `energyConsumed < 0`. Ambos atuam como divisores nas fórmulas de dreno, o que produziria `NaN` (`0.0 / 0.0`) ou `Infinity` em ponto flutuante IEEE 754.
* **Decisão adotada:** **Classificar explicitamente como `INDETERMINADO`** (apresentado na camada de UI como `SEM DADOS`), mantendo o dispositivo visível na listagem da frota.
* **Raciocínio de engenharia:** Em um painel de monitoramento de frota (PD&I), descartar silenciosamente registros defeituosos oculta aparelhos físicos que perderam calibração de bateria ou reportaram telemetria zerada. Manter o registro visível sob o estado `INDETERMINADO` preserva a rastreabilidade total dos 30 dispositivos (`REQ-05`), impede falhas aritméticas (`NaN`/`Infinity`) e informa na tela de detalhe exatamente qual parâmetro inviabilizou o cálculo.

---

## 2. Grandezas Derivadas e Regra de Classificação (Seção 4)

Para todos os registros que superam a validação de completude ($\text{batteryCapacityMah} > 0$, $\text{screenOnMinutes} > 0$ e energia normalizada válida $\ge 0$), são computadas duas grandezas na camada `domain/`:

1. **Dreno Absoluto ($\text{mAh/h}$):**
   $$\text{drenoAbsoluto} = \frac{\text{energiaMah}}{\text{screenOnMinutes} / 60.0}$$

2. **Dreno Relativo ($\%/\text{h}$):**
   $$\text{drenoRelativo} = \left(\frac{\text{drenoAbsoluto}}{\text{batteryCapacityMah}}\right) \times 100.0$$

* **Raciocínio de uso do Dreno Relativo:** Dispositivos da frota possuem capacidades nominais distintas (de $3900\text{ mAh}$ a $6000\text{ mAh}$). O dreno relativo parametriza o desgaste horário em função do tanque energético de cada modelo, permitindo aplicar limiares uniformes a toda a frota.

### 2.1. Ordem de Precedência e Limiares de Decisão
A função pura de classificação avalia as condições estritamente na ordem abaixo. **A primeira condição verdadeira define o estado final** (e quando múltiplos sinais do mesmo nível ocorrem, todos são reportados na justificativa):

| Ordem | Estado no Domínio (`DeviceStatus`) | Rótulo na UI | Condição de Disparo (qualquer uma basta) |
| :---: | :--- | :--- | :--- |
| **1º** | `INDETERMINADO` | `SEM DADOS` | `batteryCapacityMah <= 0` (ou ausente) **OU** `screenOnMinutes <= 0` **OU** `energyConsumed < 0` **OU** falha de conversão `mWh` (ex.: `nominalVoltage <= 0`) |
| **2º** | `CRITICO` | `CRÍTICO` | `drenoRelativo >= 12.0` ($\%/\text{h}$) **OU** `crashCount7d >= 3` **OU** `jankFramesPct >= 15.0` ($\%$) |
| **3º** | `ATENCAO` | `ATENÇÃO` | `drenoRelativo >= 7.0` ($\%/\text{h}$) **OU** `crashCount7d >= 1` **OU** `jankFramesPct >= 8.0` ($\%$) |
| **4º** | `OK` | `OK` | Nenhuma das condições anteriores foi acionada |

### 2.2. Previsibilidade por Inspeção (Exemplos de Verificação)
* **`DEV-0137` (Pixel 6a):** `980 mAh`, `214 min`, cap `4410 mAh`, `jank = 7.4%`, `crashes = 2`. Dreno absoluto $= 274,77\text{ mAh/h}$; Dreno relativo $= 6,23\%/\text{h}$ ($< 7\%/\text{h}$). Porém `crashCount7d = 2` ($\ge 1$). **Resultado previsto:** `ATENCAO` (gatilho: 2 falhas em 7 dias).
* **`DEV-0204` (Galaxy A54):** `3773 mWh` a `3.85 V` $\rightarrow 980,0\text{ mAh}$, `186 min`, cap `5000 mAh`, `jank = 4.1%`, `crashes = 0`. Dreno relativo $= 6,32\%/\text{h}$. Nenhum limiar atingido. **Resultado previsto:** `OK`.
* **`DEV-0311` (Moto G13):** `batteryCapacityMah = 0`. **Resultado previsto:** `INDETERMINADO` (mesmo possuindo `jank = 11.2%` e `crashes = 1`, a regra de integridade de dados precede a avaliação parcial).

---

## 3. Ameaças à Validade (REQ-10)

Mesmo com a implementação livre de defeitos de software e testada na JVM, as conclusões geradas por este modelo de classificação estão sujeitas às seguintes ameaças à validade no mundo real:

1. **Atribuição integral do consumo ao tempo de tela ativa (Viés de Background / Doze):**
   A equação $\text{drenoAbsoluto} = \text{energiaMah} / (\text{screenOnMinutes} / 60)$ assume implicitamente que 100% da energia consumida ocorreu enquanto a tela estava ligada. Dispositivos que executam serviços pesados em segundo plano com a tela desligada (sincronização contínua, *wakelocks* presos, roteamento Wi-Fi ou busca de sinal 4G/5G em área de sombra) terão todo o consumo de *background* dividido por poucos minutos de tela ativa, superestimando artificialmente o `drenoRelativo` e podendo gerar **falsos positivos de status `CRITICO`**.
2. **Assimetria temporal entre janelas de observação:**
   O indicador `crashCount7d` acumula falhas ao longo de 7 dias, enquanto `screenOnMinutes` e `energyConsumed` refletem o ciclo de descarga recente (tipicamente algumas horas). Um dispositivo que sofreu 3 falhas pontuais há 6 dias devido a um aplicativo de terceiros instável (já atualizado) continuará classificado como `CRITICO` mesmo operando com saúde energética e gráfica perfeitas hoje.
3. **Ausência de ponderação por *Tier* de hardware e taxa de atualização:**
   Aplicar o mesmo limiar fixo de `jankFramesPct` ($8\%$ e $15\%$) para dispositivos `low` (ex.: Helio P35 / G37 a 60 Hz) e `high` (ex.: Snapdragon 8 Gen 3 a 120 Hz) desconsidera que janelas de composição de quadros em 120 Hz ($8,33\text{ ms}$) são duas vezes mais estreitas que em 60 Hz ($16,67\text{ ms}$), além da diferença natural de capacidade de GPU entre categorias.
