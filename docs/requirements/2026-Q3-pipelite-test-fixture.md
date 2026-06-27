# Pipelite Fluent Testing Kit (PipeliteTestFixture)

## Overview & Objective

`PipeliteTestFixture` è il modulo di testing `pipelite-test-support`, riscritto in una Fluent API fortemente tipizzata basata sul paradigma BDD (Given-When-Then) e sullo Step Builder Pattern. Rispetto alla prima versione del requisito, l'API è stata rifinita in due punti chiave:

- ogni fase del ciclo Given-When-Then è **parametrica**: le precondizioni, l'azione e le aspettative sono passate come argomenti (`Precondition...`, `Action`, `Expectation...`) invece che incatenate come setter fluenti separati.
- il flusso sotto test **mantiene il proprio sink reale** (es. `toSink("kafka://orders-out")`): la fixture lo rediretta automaticamente verso un punto di cattura interno, così l'autore del test non deve mai scrivere `toSink("test://...")` né conoscere l'esistenza del protocollo `test://`.

Le asserzioni sono centralizzate in un modello ad oggetti estensibile chiamato `Expectations` ed eseguite tramite il metodo `then`. È supportata l'ispezione step-by-step dei flussi asincroni tramite `inspectStep`.

## Architectural Design & State Machine

La fluent chain è interamente guidata da tre metodi statici/di istanza che accettano direttamente gli oggetti che rappresentano "cosa fare":

```
PipeliteTestFixture.given(Precondition...) ──> WhenOperations
                                                      │
                                                .when(Action)
                                                      │
                                                      ▼
                                                ThenOperations
                                                      │
                                          ┌───────────┴───────────┐
                                    .then(Expectation...)   .inspectStep(name, Expectation...)
```

- `given(Precondition...)`: applica ogni `Precondition` a una fixture nuova e ritorna `WhenOperations`.
- `when(Action)`: esegue l'`Action` (modalità processor o modalità flow) e ritorna `ThenOperations`.
- `then(Expectation...)` / `inspectStep(stepName, Expectation...)`: valutano una o più `Expectation` rispettivamente sullo stato finale o su uno snapshot intermedio; ritornano `ThenOperations` per concatenare ulteriori verifiche.

Le sole API di estrazione dirette rimaste su `ThenOperations` (oltre a `then`/`inspectStep`) sono `getOutputPayload()`, `getOutputPayloadAs(Class)` e `getHeaderAs(String, Class)` — quest'ultimo mantenuto oltre la lettera della spec originale perché più scenari richiedono di asserire il valore esatto di un header, non solo la sua presenza.

## Interface Contracts (Fluent Specs)

### Precondition / Preconditions — fase Given

`Precondition` è un'interfaccia funzionale (`void apply(GivenOperations target)`) applicata da `given(...)`. `GivenOperations` è il target interno che le `Precondition` configurano (non pensato per essere chiamato direttamente dall'utente, se non scrivendo una `Precondition` custom):

- `header(String name, Object value)`
- `payload(Object payload)`
- `flowDefinition(FlowDefinition flowDefinition)` — registra un flusso nel contesto temporaneo; più flussi possono essere concatenati con `link://`.
- `timeout(long seconds)` — timeout di cattura (default 5s) usato da `Actions.supplyTo(...)`.

La factory `Preconditions` espone:

- `Preconditions.header(String name, Object value)`
- `Preconditions.inputPayload(Object payload)`
- `Preconditions.flowDefinition(FlowDefinition flowDefinition)`
- `Preconditions.timeout(long seconds)`

### Action / Actions — fase When

`Action` è un'interfaccia funzionale (`ThenOperations apply(ExecutionTarget target)`) eseguita da `WhenOperations.when(Action)`. `ExecutionTarget` è il target interno consumato dall'`Action`:

- `process(Processor processor)` — esegue un singolo `Processor` in isolamento (nessun runtime, nessun endpoint, completamente sincrono). Ritorna `ThenOperations`.
- `supplyTo(String entryPointEndpoint)` — avvia un `DefaultPipeliteContext` con tutti i flussi registrati, inietta l'exchange nell'endpoint indicato, attende che raggiunga il sink (reale, rediretto automaticamente — vedi sezione dedicata), poi ferma il contesto. Ritorna `ThenOperations`.

La factory `Actions` espone `Actions.process(Processor)` e `Actions.supplyTo(String entryPointEndpoint)`.

### ThenOperations — fase Then

- `then(Expectation... expectations)` — valuta una o più aspettative sullo stato finale dell'exchange (e, in modalità processor, della `TestProcessContribution`). Ritorna `ThenOperations` per concatenare ulteriori chiamate.
- `inspectStep(String stepName, Expectation... expectations)` — valuta le aspettative sullo snapshot dell'exchange catturato subito dopo l'esecuzione dello step indicato, su **qualunque** flusso registrato (anche concatenati via `link://`). Lancia `AssertionError` se lo step non è mai stato raggiunto.
- `getOutputPayload()` / `getOutputPayloadAs(Class<T>)` — estraggono il payload di output (fallback sul payload di input se non impostato).
- `getHeaderAs(String name, Class<T> expectedType)` — estensione oltre la spec originale.

## The Expectations Engine & Decoupling

Le asserzioni implementano l'interfaccia funzionale `Expectation`:

```java
@FunctionalInterface
public interface Expectation {
    void verify(Exchange exchange, TestProcessContribution contribution);
}
```

`contribution` è `null` quando si verifica un exchange in modalità flow (via `supplyTo` o `inspectStep`), poiché le contribution esistono solo per le esecuzioni isolate di un `Processor`.

La factory finale `Expectations` espone i seguenti metodi statici:

- `Expectations.isSuccess()` — verifica `TestProcessContribution.isSuccess()` (solo modalità processor).
- `Expectations.isFailure()` — verifica `TestProcessContribution.isFailure()` (solo modalità processor).
- `Expectations.isExecutionStopped()` — verifica che il processor abbia invocato `stopExecution()` (solo modalità processor).
- `Expectations.failureCause(Throwable expected)` — verifica che la causa di fallimento sia esattamente l'istanza indicata (solo modalità processor).
- `Expectations.isExecutionCompleted()` — verifica che il flusso abbia raggiunto il proprio sink prima del timeout configurato (solo modalità flow).
- `Expectations.isNotExecutionCompleted()` — verifica il contrario (es. flusso filtrato o fermato) (solo modalità flow).
- `Expectations.hasHeader(String name)` — verifica la presenza di un header sull'exchange corrente.
- `Expectations.headerEquals(String name, Object expectedValue)` — verifica presenza **e** valore esatto di un header.
- `Expectations.noHeader(String name)` — verifica l'assenza di un header.
- `Expectations.payloadEquals(Object expected)` — verifica l'uguaglianza del payload effettivo (gestisce anche `null`).
- `Expectations.payloadAs(Class<T> type, Consumer<T> assertions)` — verifica che il payload sia di tipo `T` e ne passa l'istanza a un blocco di asserzioni custom fornito dall'utente, per i casi (es. un singolo campo di un payload complesso) che una semplice `payloadEquals` non può esprimere — punto di estensione pensato per l'integrazione nativa con JUnit/AssertJ.

Per casi non coperti dai metodi predefiniti, qualunque lambda `(exchange, contribution) -> { ... }` è una `Expectation` valida e può essere passata direttamente a `then`/`inspectStep`.

## Step-by-Step Verification (Inversion of Control)

Per abilitare `inspectStep(String stepName, Expectation...)` **non è stata necessaria alcuna modifica a `pipelite-core`**. L'implementazione si aggancia a un punto di estensione già pubblico:

- `FlowNode.addExchangePreProcessor`/`addExchangePostProcessor` (interfaccia pubblica in `pipelite-spi`) è già invocato da ogni nodo del flusso subito dopo l'esecuzione del proprio `Processor`/step.
- I `FlowNode` di ogni `ProcessorDefinition` sono creati in modo **eager** al momento della build della `FlowDefinition` (in `FlowDefinitionBuilder.process()`/`transformPayload()`/ecc.) e sono raggiungibili dall'esterno tramite `flowDefinition.iterateProcessorDefinitions()` → `processorDefinition.getProcessor(FlowNode.class)`.
- Prima di registrare ogni flusso nel `DefaultPipeliteContext`, la fixture (`PipeliteTestFixture.registerStepSnapshotCapture`) registra su ciascun `FlowNode` un `ExchangePostProcessor` interno, `StepSnapshotCapture`, che — usando `ExchangeFactory.copyExchange(Exchange)` — salva una copia immutabile dell'exchange in una `ConcurrentHashMap<String, Exchange>` indicizzata per nome dello step (`ctx.getProcessorName()`).
- `FlowFactory.setPrePostProcessors()` *aggiunge* i propri processor alla stessa collezione, senza mai sostituire quelli registrati dalla fixture: i due meccanismi coesistono senza conflitti.
- Se l'utente invoca `inspectStep` per uno step non attraversato dal flusso (es. perché un processor precedente ha chiamato `stopExecution()`), la fixture lancia immediatamente un `AssertionError`.
- Lo snapshot funziona anche **attraverso più flussi concatenati** via `link://`: `registerStepSnapshotCapture` itera su tutti i `FlowDefinition` registrati, non solo su quello d'ingresso.

Questo è più semplice del design originariamente previsto (un `StepExecutionListener` registrato sul `DefaultPipeliteContext`): non esiste un simile listener pubblico sul contesto, ma il punto di aggancio a livello di singolo `FlowNode` si è rivelato sufficiente e meno invasivo.

## Real Sink Redirection

Requisito emerso durante l'implementazione, non presente nella spec originale: la fixture **non deve richiedere** che il flusso sotto test termini con `toSink("test://...")`. Il flusso registrato dall'utente resta quello di produzione (es. `toSink("kafka://orders-out")`).

Prima di registrare ogni `FlowDefinition` nel contesto, `PipeliteTestFixture.redirectSinkToCapture(FlowDefinition)` costruisce una copia (stesso nome di flusso, stessa `SourceDefinition`, le stesse istanze di `ProcessorDefinition`/`FlowNode`, stesso `ExceptionHandler`) con il sink terminale sostituito da un endpoint di cattura interno (`CaptureChannelAdapter.CAPTURE_ENDPOINT_URL`, gestito dal `ChannelAdapter` del protocollo `test://`) — **a meno che** il protocollo del sink originale (estratto via `ChannelURL.parse(url).getProtocol()`) sia `link`, nel qual caso viene lasciato intatto poiché rappresenta un hop interno verso un altro flusso registrato, non il vero sink finale. Un flusso senza sink (es. un sotto-flusso da recipient-list) viene copiato senza sink.

Conseguenza pratica: il vero `ChannelAdapter` (kafka, http, ecc.) non viene mai risolto né invocato durante un test — provato da un test dedicato che usa `toSink("kafka://...")` quando l'adapter Kafka non è nemmeno sul classpath di `pipelite-test-support` (dipendenza `test`-scope di `pipelite-core`, non transitiva).

## Non-Functional & Quality Requirements

- **Backward Compatibility**: i metodi di estrazione payload/header (`getOutputPayloadAs`, `getHeaderAs`) restano disponibili su `ThenOperations` anche se la spec originale ne elencava solo alcuni.
- **Thread Safety**: poiché Pipelite processa i flussi asincroni su thread dedicati tramite `EventDrivenConsumer`, sia la mappa degli snapshot degli step (`StepSnapshotCapture`) sia la mappa delle `CompletableFuture` di cattura (`CaptureChannelAdapter`) sono completamente thread-safe (`ConcurrentHashMap`).
- **Error Messages**: in caso di fallimento di una `Expectation`, o di uno step non raggiunto in `inspectStep`, viene lanciato un `AssertionError` con un messaggio esplicativo chiaro (es. "Expected component to succeed, but failed with cause: [Exception]").
- **No pipelite-core changes**: l'intera fixture, incluse `inspectStep` e la redirezione del sink reale, è implementata esclusivamente in `pipelite-test-support` usando estensioni pubbliche già esposte da `pipelite-spi`/`pipelite-core` (`FlowNode`, `ExchangeFactory.copyExchange`, `ChannelURL`, `FlowDefinitionImpl`/`SinkDefinitionImpl`).
