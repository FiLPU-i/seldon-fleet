# Protocolo de Decisoes e Especificacao de Dominio - Seldon Fleet

Este documento estabelece o pre-registro dos criterios de normalizacao,
tratamento de defeitos, regras de classificacao e ameacas a validade
adotados no projeto Seldon Fleet (REQ-10).

---

## 1. Tratamento de Defeitos do Arquivo (Secao 3.3)

### 1.1. Defeito 1 - Unidade de Energia Inconsistente (mAh vs. mWh)
* **Problema:** Parte dos registros informa `energyConsumed` em `mAh` e outra parte em `mWh`.
* **Regra de Conversao:** Na camada `data/mapper`, a unidade e padronizada para `mAh` antes de qualquer calculo:
  * Se `energyUnit` for `mAh`: `energiaMah = energyConsumed`
  * Se `energyUnit` for `mWh` (com `nominalVoltage > 0`): `energiaMah = energyConsumed / nominalVoltage`
  * Se `nominalVoltage <= 0` ou unidade invalida: o registro e marcado como invalido e recebe status `INDETERMINADO`.

### 1.2. Defeito 2 - Registros Incompletos e Divisores Nulos
* **Problema:** Registros com `batteryCapacityMah <= 0` (ex.: `DEV-0311`) ou `screenOnMinutes <= 0` (ex.: `DEV-0745`) gerariam divisao por zero (`NaN` ou `Infinity`).
* **Decisao Adotada:** Classificar como `INDETERMINADO` (exibido na tela como `SEM DADOS`), mantendo o aparelho visivel na lista.
* **Justificativa:** Ocultar o aparelho esconderia uma falha de telemetria da equipe. Manter como `INDETERMINADO` preserva os 30 aparelhos na contagem (`REQ-05`) e impede erros matematicos (`REQ-03`).

---

## 2. Formulas e Regra de Classificacao (Secao 4)

Para aparelhos com dados validos (`batteryCapacityMah > 0` e `screenOnMinutes > 0`), o dominio calcula:

1. **Dreno Absoluto (mAh/h):**
   `drenoAbsoluto = energiaMah / (screenOnMinutes / 60.0)`

2. **Dreno Relativo (%/h):**
   `drenoRelativo = (drenoAbsoluto / batteryCapacityMah) * 100.0`

### 2.1. Ordem de Prioridade e Limiares
As regras sao avaliadas nesta ordem exata (a primeira que atender define o status):

| Ordem | Status no Dominio | Rotulo na Tela | Condicao de Disparo (qualquer uma basta) |
| :--- | :--- | :--- | :--- |
| 1 | `INDETERMINADO` | `SEM DADOS` | `batteryCapacityMah <= 0` OU `screenOnMinutes <= 0` OU `energyConsumed < 0` OU falha na conversao `mWh` |
| 2 | `CRITICO` | `CRITICO` | `drenoRelativo >= 12.0%/h` OU `crashCount7d >= 3` OU `jankFramesPct >= 15.0%` |
| 3 | `ATENCAO` | `ATENCAO` | `drenoRelativo >= 7.0%/h` OU `crashCount7d >= 1` OU `jankFramesPct >= 8.0%` |
| 4 | `OK` | `OK` | Nenhuma das condicoes anteriores |

### 2.2. Exemplos de Verificacao no Arquivo
* **DEV-0137 (Pixel 6a):** `980 mAh`, `214 min`, bateria `4410 mAh`, `crashes = 2`. Dreno relativo = `6.23%/h` (< 7%), mas possui `2 falhas` (>= 1). Status: `ATENCAO`.
* **DEV-0204 (Galaxy A54):** `3773 mWh` / `3.85 V` = `980 mAh`, `186 min`, bateria `5000 mAh`. Dreno relativo = `6.32%/h`, `jank = 4.1%`, `crashes = 0`. Status: `OK`.
* **DEV-0311 (Moto G13):** `batteryCapacityMah = 0`. Status: `INDETERMINADO`.

---

## 3. Ameacas a Validade (REQ-10)

1. **Consumo em Segundo Plano (Tela Desligada):** A formula divide toda a energia gasta apenas pelos minutos de tela ligada (`screenOnMinutes`). Se um aparelho gastar muita bateria com a tela apagada (servicos em background, sincronizacao ou sinal movel fraco), o `drenoRelativo` sera inflado e podera gerar um falso `CRITICO`.
2. **Janela de Tempo Diferente nos Crashes:** O campo `crashCount7d` soma falhas de 7 dias inteiros, enquanto bateria e tempo de tela refletem apenas o ciclo recente. Um aparelho que teve 3 falhas ha 5 dias num app ja corrigido continuara como `CRITICO` hoje.
3. **Mesmo Limiar de Jank para Hardware Diferente:** Cobrar o mesmo limite de `jankFramesPct` (8% e 15%) de aparelhos de entrada (`tier = low`, 60 Hz) e aparelhos topo de linha (`tier = high`, 120 Hz) nao considera a diferenca de capacidade grafica entre as categorias.
