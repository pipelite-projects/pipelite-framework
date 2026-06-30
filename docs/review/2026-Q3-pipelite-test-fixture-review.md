# Code Review — PipeliteTestFixture (feature/issue-2)

**Branch:** `feature/issue-2-pipelitetestfixture`  
**Data:** 2026-06-30  
**Modulo:** `pipelite-test-support`

---

## Riepilogo prioritizzato

| # | Severità | Finding |
|---|----------|---------|
| 9 | Alta | `registerStepSnapshotCapture` muta i FlowNode originali — accumulo di post-processor con FlowDefinition riusate |
| 8 | Alta | Silent failure quando il flow lancia eccezione — identico a timeout, nessun diagnostico |
| 10 | Media | `copyExchange` condivide il riferimento all'output — snapshot corrompibile con payload mutabili |
| 6 | Media | Static `PENDING` non sicuro per parallel test execution |
| 11 | Media | Step con nome duplicato tra flow sovrascrive snapshot silenziosamente |
| 1 | Bassa | Dangling `@link` a `inspectStep` in `Expectation.java` e `StepSnapshotCapture.java` |
| 3 | Bassa | `timeout(0)` non validato |
| 7 | Bassa | `TEST_ID_PROPERTY` pubblica — rischio di collisione con codice utente |
| 4 | Info | `StepExpectation` non è `@FunctionalInterface` — asimmetria con `Expectation` non documentata |
| 5 | Info | `step(...).verify(exchange, null)` ignora `stepName()` silenziosamente |
| 12–16 | Info | Gap di test coverage su casi negativi/edge |
| 17 | Info | `StepExpectation` custom richiede classe anonima — non documentato |
| 18 | Info | `flowExecuted` semantics imprecise |
| 19 | Info | Due `ExchangeFactory` distinte tra processor mode e flow mode |
| 20 | Info | `flowConfiguration` risolve dipendenze per tipo concreto — collisioni silenziose |

---

## 1. API pubblica — catena given/when/then

Complessivamente solida. Il pattern hub con `import static io.pipelite.test.PipeliteTest.*` è consolidato (AssertJ, Hamcrest), le interfacce `GivenOperations / WhenOperations / ThenOperations / ExecutionTarget` separano bene le responsabilità, e i `@FunctionalInterface` per `Precondition`, `Action` ed `Expectation` rendono triviale l'estensione via lambda.

### Finding 1 — Dangling JavaDoc reference: metodo `inspectStep` che non esiste

`Expectation.java` riga 28 e `StepSnapshotCapture.java` righe 21-22 referenziano `{@link ThenOperations#inspectStep(String, Expectation...)}` che non esiste più (rimosso e sostituito da `step(...)` dentro `then(...)`). Il build segnala warning Javadoc e chiunque legga la documentazione è fuorviato.

### Finding 2 — Cast raw nel test rivela una fuga dell'API

`PipeliteTestFixtureBaseTest.java` righe 173-174:
```java
((ThenOperations) given(inputPayload("data"))).getOutputPayload();
```
Il fatto che `given(...)` ritorni `WhenOperations` obbliga il test a fare un cast esplicito per verificare la guardia. L'API ritorna un'interfaccia che non espone `getOutputPayload`, ma la classe sottostante la implementa comunque. Alternativa minima: un metodo factory separato nel test setup che ritorna direttamente `ThenOperations` per i test negativi.

### Finding 3 — `timeout(0)` non è validato

`Preconditions.timeout(long seconds)` accetta qualsiasi valore. Con `timeout(0)` o un valore negativo, `captureFuture.get(0, TimeUnit.SECONDS)` lancia immediatamente `TimeoutException` senza che il flow abbia avuto modo di partire. Il messaggio risultante è fuorviante. Serve una guardia esplicita.

---

## 2. Design di `StepExpectation` — instanceof dispatch

`PipeliteTestFixture.then(...)`:
```java
if (expectation instanceof StepExpectation) {
    final StepExpectation stepExpectation = (StepExpectation) expectation;
    ...
} else {
    expectation.verify(exchangeUnderInspection, contribution);
}
```

Il dispatch con `instanceof` è accettabile: l'alternativa Visitor sarebbe overengineering per una gerarchia di due livelli. Un `default boolean isStepScoped() { return false; }` sull'interfaccia `Expectation` sarebbe più pulito (evita l'instanceof, non richiede cast, è override-able), ma non è un difetto critico.

### Finding 4 — `StepExpectation` non è `@FunctionalInterface` — asimmetria dell'API

`Expectation` è `@FunctionalInterface`: un'expectation custom si scrive come lambda. `StepExpectation` richiede due metodi (`stepName()` + `verify()`), quindi obbliga a una classe anonima o concreta. Questa asimmetria non è documentata e sorprenderà l'utente.

### Finding 5 — `step(...).verify(exchange, contribution)` ha semantica ambigua

`StepExpectation` estende `Expectation`, quindi `step("x", hasHeader("y")).verify(someExchange, null)` è un'operazione valida che esegue le expectations sull'exchange passato ignorando `stepName()`. La semantica "questo si applica allo snapshot dello step X" esiste solo quando il dispatch è nel `then(...)` del fixture. Se qualcuno usa `StepExpectation` direttamente come `Expectation` (es. passandolo a `output(...)`), il `stepName()` viene ignorato silenziosamente.

---

## 3. Implementazione interna

### `CaptureChannelAdapter`

#### Finding 6 — Static `PENDING` è un rischio con test paralleli

```java
private static final ConcurrentHashMap<String, CompletableFuture<Exchange>> PENDING =
    new ConcurrentHashMap<>();
```

La mappa è `static final` e vive a livello di classloader. Con Maven Surefire in modalità `parallel=methods` o `classes`, due test che eseguono `supplyTo(...)` contemporaneamente condividono la stessa mappa. L'isolamento via UUID funziona in condizioni normali, ma se un test viene abortito prima del `finally { CaptureChannelAdapter.deregister(testId); }`, la entry rimane nella mappa per tutta la durata della JVM.

#### Finding 7 — `TEST_ID_PROPERTY` è pubblica e non bloccata

`CaptureChannelAdapter.TEST_ID_PROPERTY = "__pipelite_test_id__"` è `public`. Se un flow di produzione usa questa property key (es. per logging o tracing), interferisce con il meccanismo di cattura. Dovrebbe essere almeno `package-private`.

#### Finding 8 — Silent failure quando il flow lancia eccezione non gestita

Se un processor nel flow lancia un'eccezione non gestita che bypassa l'`ExceptionHandler`, il `CaptureProducer` non viene mai raggiunto, la `CompletableFuture` rimane pending e il fixture va in timeout. Il risultato è identico al caso "flow filtrato prima del sink": nessun modo di distinguere i due casi dall'esterno. Sarebbe utile un `ExceptionHandler` iniettato dal fixture che completa il future con `completeExceptionally`.

### `StepSnapshotCapture`

#### Finding 9 — `registerStepSnapshotCapture` muta i `FlowNode` originali

```java
// PipeliteTestFixture.java
for (FlowDefinition flowDefinition : flowDefinitions) {
    ...
    processorDefinition.getProcessor(FlowNode.class).addExchangePostProcessor(capture);
}
```

Il post-processor viene aggiunto ai `FlowNode` delle `FlowDefinition` originali passate dall'utente, non alle copie create da `redirectSinkToCapture`. Se l'utente riusa una `FlowDefinition` tra test (es. dichiarandola come variabile statica), ogni test accumula un ulteriore `StepSnapshotCapture` sul `FlowNode`. Non è un errore funzionale ma è un memory leak e un side effect su oggetti esterni.

L'invariante implicito ("le copie di `redirectSinkToCapture` condividono gli stessi `FlowNode`") non è testato: se in futuro le copie diventassero profonde, `registerStepSnapshotCapture` smetterebbe di funzionare silenziosamente.

#### Finding 10 — `copyExchange` condivide il riferimento all'output

`DefaultExchangeFactory.copyExchange` fa `copy.setOutput(current.getOutput())` — copia superficiale. Se un processor successivo modifica il payload del messaggio output in-place (aggiungendo elementi a una `Map` o `List`) anziché sostituirlo, lo snapshot catturato in precedenza rifletterà le mutazioni successive. I test attuali passano perché i processor sostituiscono sempre il payload con un nuovo scalare; con payload mutabili il comportamento sarebbe inaffidabile.

#### Finding 11 — Nomi di step duplicati tra flow sovrascrivono il snapshot silenziosamente

`stepSnapshots.put(ctx.getProcessorName(), ...)` usa il nome del processor come chiave. Se due flow nella stessa catena contengono uno step con lo stesso nome (es. entrambi si chiamano `"enrich"`), il secondo sovrascrive il primo. `step("enrich", ...)` verificherebbe lo snapshot sbagliato senza alcun warning.

---

## 4. Test coverage

La suite copre bene i casi nominali. Mancano:

### Finding 12 — Nessun test per expectation di processor mode usate in flow mode

`isSuccess()`, `isFailure()`, `isExecutionStopped()` lanciano `AssertionError` via `requireContribution(contribution)` quando `contribution == null` (flow mode). Non c'è un test che verifica che il messaggio di errore sia chiaro e che la combinazione non causi un `NullPointerException` inaspettato.

### Finding 13 — Nessun test per flow che lancia eccezione nel processor

Non esiste un test che mette un processor che lancia `RuntimeException` in flow mode. Il comportamento atteso (timeout, `capturedFlowExchange == null`) non è documentato né testato.

### Finding 14 — Nessun test per step con nomi duplicati tra flow

Come descritto nel Finding 11.

### Finding 15 — `payloadAs` con payload null non è testato

`Expectations.payloadAs` esegue `assertions.accept(type.cast(null))` quando il payload è null. `type.cast(null)` non lancia per tipi reference, ma il `Consumer` riceve `null` senza un messaggio chiaro. Edge case non esercitato.

### Finding 16 — `output()` con zero argomenti è un no-op silenzioso

`.then(output())` non fa nulla e non è segnalato. Potrebbe mascherare un test scritto male.

---

## 5. Estensibilità

### Finding 17 — `StepExpectation` custom richiede classe anonima

Non è documentato che `Expectations.step(String, Expectation...)` sia l'unico punto di accesso idiomatico. `StepExpectation` è pubblica come se fosse un'interfaccia da implementare direttamente, ma richiede due metodi. L'utente che vuole una step expectation custom deve scrivere una classe anonima completa.

---

## 6. Altre osservazioni

### Finding 18 — `flowExecuted` semantics imprecise

Il flag viene impostato a `true` all'inizio di `supplyTo(...)`, prima che il flow abbia effettivamente eseguito. `isFlowMode()` ritorna `true` anche se il flow è andato in timeout. Il nome è impreciso: indica "flow mode attivato", non "flow eseguito con successo".

### Finding 19 — Due `ExchangeFactory` distinte

Il fixture crea il proprio `exchangeFactory` (usato in processor mode). In flow mode usa `context.getExchangeFactory()` (passato a `StepSnapshotCapture`). Se le due factory dovessero divergere nella configurazione, il comportamento sarebbe asimmetrico tra le modalità. Per ora coincidono perché entrambe sono `DefaultExchangeFactory`, ma è un accoppiamento implicito.

### Finding 20 — `flowConfiguration` risolve dipendenze per tipo concreto

```java
registry.register(dep.getClass().getName(), dep);
```

Con due dipendenze della stessa classe concreta, la seconda sovrascrive la prima silenziosamente. Se il metodo `@DefineFlow` dichiara un parametro di tipo interfaccia, la risoluzione potrebbe fallire se lo scanner cerca il nome dell'interfaccia come chiave.
