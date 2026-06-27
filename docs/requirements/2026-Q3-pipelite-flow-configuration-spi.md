# Pipelite Flow Configuration SPI

## Overview & Objective

Oggi una `FlowDefinition` non ha alcun punto di riferimento riutilizzabile e scopribile: in un'app Spring viene costruita inline dentro un metodo `@Bean` (es. `Application.acquireFlow()`); in un'app plain-Java viene costruita ad-hoc in un metodo di setup. In entrambi i casi `PipeliteContext.registerFlowDefinition(FlowDefinition)` è **write-only** — una volta registrata, la flow non è più recuperabile da nessuno, nemmeno dall'applicazione stessa.

Questo requisito introduce in `pipelite-core` una **SPI nativa, basata su annotazioni e convenzione di metodo**, per definire e scoprire le `FlowDefinition` indipendentemente da Spring, e ridisegna `pipelite-spring-starter` perché diventi un'implementazione *trasparente* di questa SPI — non un secondo meccanismo di discovery parallelo. L'obiettivo finale (story separata, si veda `2026-Q3-pipelite-test-fixture.md`) è permettere a `pipelite-test-support` di referenziare la flow di produzione realmente definita dall'applicazione, invece di farla ridefinire al test.

## Background & Problem Statement

Analisi del codice esistente:

- `PipeliteContext`/`DefaultPipeliteContext` (`pipelite-core`): `registerFlowDefinition(FlowDefinition)` aggiunge a una `Collection<FlowDefinition>` privata; non esiste alcun getter (`getFlowDefinition(name)` o simile). `FlowRegistry` fa lookup solo a runtime per `sourceEndpointURI` su oggetti `Flow` (non `FlowDefinition`), non per nome.
- `pipelite-examples` (app Spring Boot): la flow è costruita inline in un metodo `@Bean` — non esiste come costante/factory riutilizzabile fuori da quel metodo.
- `pipelite-spring-starter`: `FlowDefinitionRegistrar` è un `BeanPostProcessor` che intercetta **ogni** bean Spring e, se `instanceof FlowDefinition`, lo registra. Scatta **dopo** che Spring ha già risolto via autowiring gli eventuali parametri del metodo `@Bean` (es. `@Bean FlowDefinition acquireFlow(SomeService svc)`): la DI è quindi già "consumata" da Spring prima che `pipelite-core` veda l'oggetto.
- `DefaultPipeliteContext.registerFlowDefinition` deduplica per nome (`FlowDefinitionImpl.equals()` confronta solo `flowName`, e la registrazione usa `Collection.contains`/`add`): una seconda registrazione con lo stesso nome viene scartata **silenziosamente**, senza errore.

**Conseguenza diretta**: se si aggiungesse un secondo meccanismo di discovery nativo (scanner a riflessione, indipendente da Spring) che scansiona le stesse classi già gestite da Spring, si creerebbe un conflitto silenzioso — quale delle due istanze "vince" dipende dall'ordine di esecuzione, e quella scartata potrebbe essere quella costruita correttamente (con DI risolta), lasciando attiva una flow rotta. Inoltre un meccanismo a riflessione puro non ha modo di risolvere parametri arbitrari di metodo (dipendenze come client HTTP, repository, configurazioni) come fa l'autowiring di Spring.

Questa SPI deve quindi essere progettata come **unica fonte di verità**, valida sia in plain Java sia sotto Spring, e non come alternativa che coesiste con `FlowDefinitionRegistrar`.

## Architectural Design

### Annotazioni (nuovo package, es. `io.pipelite.dsl.annotation`)

- `@FlowConfiguration` — annotazione di classe, marca una classe come contenitore di metodi factory di `FlowDefinition` (analoga concettualmente a `@Configuration` di Spring, ma nativa di Pipelite).
- `@DefineFlow` — annotazione di metodo, su un metodo che ritorna `FlowDefinition` (tipicamente il corpo usa la DSL esistente `Pipelite.defineFlow(name)...build()`). I parametri del metodo rappresentano le dipendenze richieste per costruire la flow.

### `FlowConfigurationScanner` (pipelite-core)

Componente che, data una `Class<?>` annotata `@FlowConfiguration` (e, in una iterazione successiva, un base-package da scansionare sul classpath):

1. istanzia la classe (costruttore di default, o con parametri risolti via `DependencyRegistry` — vedi sotto);
2. individua i metodi annotati `@DefineFlow`;
3. per ciascuno, risolve i parametri via `DependencyRegistry` e invoca il metodo;
4. registra ogni `FlowDefinition` risultante sul `PipeliteContext`.

### `DependencyRegistry` (pipelite-core)

Interfaccia minimale, senza alcuna dipendenza da un container DI. Il nome evita deliberatamente il termine "component", per non sovrapporsi concettualmente ai `FlowNode` (`Processor`/`Consumer`/`Producer`, si veda il vincolo di scope più sotto):

```java
public interface DependencyRegistry {
    void register(String name, Object instance);
    Optional<Object> resolve(Class<?> type);
    Optional<Object> resolve(String name);
}
```

Il registro nativo di default è "a registro": l'applicazione plain-Java registra a mano le proprie dipendenze (`context.registerDependency("primary-client", instance)` — il tipo si deriva da `instance.getClass()`, non va dichiarato esplicitamente), in modo analogo (stesso spirito, non stessa interfaccia) al pattern `*Aware` già presente in `pipelite-core` (`ExchangeFactoryAware`, `PipeliteContextAware`, usati da `FlowFactory.injectDependencies`) per l'iniezione delle poche dipendenze interne dei `FlowNode`. Non è un tentativo di reimplementare un container DI generico: i metodi `@DefineFlow` senza dipendenze registrate restano a zero parametri, come oggi.

**Istanze multiple dello stesso tipo (qualificatore per nome)**: poiché la registrazione è per nome e non per tipo, è possibile registrare più istanze della stessa classe sotto nomi diversi (es. due `DataSource` distinte, `"primary"` e `"secondary"`). La risoluzione per tipo (`resolve(Class<?>)`, usata implicitamente quando un parametro `@DefineFlow` dichiara solo il tipo) resta valida solo se esiste **esattamente una** istanza assegnabile a quel tipo nel registro: zero corrispondenze o più di una devono fallire con un'eccezione esplicita (coerentemente con l'NFR sui messaggi di errore più sotto) — mai una scelta arbitraria tra più candidati. La risoluzione per nome (`resolve(String)`) è il modo per disambiguare in presenza di istanze multiple, ma il *binding* tra un parametro di un metodo `@DefineFlow` e un nome registrato specifico (l'equivalente di un `@Qualifier` di Spring) non è specificato in questa iterazione — si veda Open Questions.

### `PipeliteContext` — nuove API additive

- `void registerFlowConfigurationClass(Class<?> configurationClass)` — usa `FlowConfigurationScanner` + il `DependencyRegistry` del context.
- `void registerDependency(String name, Object instance)` — registra un'istanza nel `DependencyRegistry` nativo del context, disponibile per la risoluzione dei parametri dei metodi `@DefineFlow`.
- `Optional<FlowDefinition> getFlowDefinition(String flowName)` — lookup di lettura, oggi assente, necessario sia per riusare le flow da altri moduli (incluso `pipelite-test-support`) sia per qualunque altro consumer futuro.

Nessuna modifica alle firme esistenti (`registerFlowDefinition`, `isRegistered`) — solo aggiunte.

### Breaking change: `registerFlowDefinition` non deduplica più silenziosamente

`DefaultPipeliteContext.registerFlowDefinition` oggi scarta silenziosamente una seconda registrazione con lo stesso `flowName` (`Collection.contains`/`add` basato su `FlowDefinitionImpl.equals()`). Questo comportamento va rimosso: una registrazione con `flowName` già presente deve lanciare un'eccezione esplicita (con il nome della flow duplicata), non essere ignorata.

Motivazione: `registerFlowDefinition` è l'unico punto di convergenza comune sia per il path nativo (`FlowConfigurationScanner` + `DependencyRegistry`) sia per il path Spring (bean definition per ciascun metodo `@DefineFlow`, autowiring standard di Spring). La garanzia di "mutua esclusione verificabile" richiesta più sotto (si veda Non-Functional & Quality Requirements) non può essere implementata solo nello scanner nativo, perché lo scanner nativo non ha visibilità sul path Spring: il fail-fast deve quindi vivere nel punto in cui i due path convergono, altrimenti una doppia registrazione (es. la stessa classe `@FlowConfiguration` scansionata sia manualmente via `registerFlowConfigurationClass` sia automaticamente dallo starter Spring come bean) verrebbe mascherata da un dedup silenzioso indistinguibile, dall'esterno, da una registrazione singola corretta.

Questo è un cambiamento di comportamento backward-incompatibile per le applicazioni esistenti che facessero affidamento (implicitamente) sul dedup silenzioso; è accettato come parte della major release che introduce questa SPI.

## Integrazione trasparente con `pipelite-spring-starter`

Per evitare la doppia registrazione descritta nel Problem Statement, `pipelite-spring-starter` non introduce un secondo scanner indipendente: **adotta le stesse annotazioni** e riusa lo stesso `FlowConfigurationScanner` di `pipelite-core`, cambiando solo come la classe `@FlowConfiguration` diventa bean e chi risolve i parametri dei metodi `@DefineFlow`.

- Lo stile attuale (`@Bean public FlowDefinition x()`, intercettato da `FlowDefinitionRegistrar` via `instanceof FlowDefinition`) viene **deprecato** (`@Deprecated` + warning di log ad ogni intercettazione) con un periodo di compatibilità — continua a funzionare, coesiste con il nuovo stile nella stessa app — poi rimosso in una major successiva.
- **Come una classe `@FlowConfiguration` diventa bean**: `@EnablePipelite` guadagna l'attributo `Class<?>[] flowConfigurations() default {}` e il suo `@Import` diventa `@Import({PipeliteConfigurationImportSelector.class, FlowConfigurationRegistrar.class})` (due selettori nello stesso array — `@Import` non è `@Repeatable`, quindi la forma array è l'unica possibile). `FlowConfigurationRegistrar` implementa `ImportBeanDefinitionRegistrar`: legge `flowConfigurations` dall'`AnnotationMetadata` della classe annotata `@EnablePipelite` e registra una bean definition per ciascuna classe elencata nel `BeanDefinitionRegistry`. Spring costruisce questi bean con la normale constructor injection, come qualunque altro bean — nessuna classpath-scanning, nessun obbligo di annotare la classe anche con `@Component`. Questo avviene nella fase 1 del bootstrap (registrazione delle bean definition), prima che `PipeliteContext` esista: `FlowConfigurationRegistrar` non lo usa né lo referenzia, non c'è dipendenza d'ordine.
- **Come vengono invocati i metodi `@DefineFlow`**: un `BeanPostProcessor` (`FlowConfigurationBeanPostProcessor`, registrato dall'autoconfigurazione, stesso punto del ciclo di vita di `FlowDefinitionRegistrar` — `postProcessAfterInitialization`) intercetta **qualsiasi** bean nel contesto Spring la cui classe sia annotata `@FlowConfiguration`, indipendentemente dal meccanismo con cui quel bean è stato registrato (via `flowConfigurations = {…}` in `@EnablePipelite`, via component scan, via `@Bean` esplicito — non importa). Questo è il comportamento desiderato: il `BeanPostProcessor` è la sola fonte di verità per l'invocazione dei metodi `@DefineFlow` lato Spring; la dichiarazione in `flowConfigurations` serve solo a rendere la classe un bean, non a filtrarla in fase di post-processing. Per ciascun bean intercettato viene invocato `FlowConfigurationScanner.scan(beanInstance, springDependencyRegistry)` — usando l'overload che opera su un'istanza già costruita (si veda sotto), non sulla `Class<?>` come nel caso nativo, perché qui l'istanza l'ha già creata Spring con le sue dipendenze (anche di costruttore) già risolte. Ogni `FlowDefinition` risultante viene registrata su `pipeliteContext.registerFlowDefinition(...)`.
- **Risoluzione dei parametri**: un adapter `SpringDependencyRegistry` implementa `DependencyRegistry` delegando a `ApplicationContext.getBean(...)`: `resolve(Class<?>)` traduce `NoSuchBeanDefinitionException` in `Optional.empty()` (fa scattare lo stesso `UnresolvableDependencyException` del caso nativo) e `NoUniqueBeanDefinitionException` nella stessa `AmbiguousDependencyException` — stesso contratto di errore, nativo o Spring. `register(String, Object)` non ha senso in questo adapter (Spring è già la fonte delle istanze): non supportato.
- **Refactor richiesto in `pipelite-core`**: `FlowConfigurationScanner` guadagna l'overload `scan(Object configurationInstance, DependencyRegistry)`, che condivide con `scan(Class<?>, DependencyRegistry)` la sola logica di discovery/invocazione dei metodi `@DefineFlow`, senza istanziare nulla — necessario per non costruire una seconda istanza parallela del bean Spring già esistente.
- Risultato: lo stesso `FlowConfigurationScanner` è usato in entrambi i mondi; cambia solo chi crea l'istanza della classe `@FlowConfiguration` (reflection nativa vs. Spring) e chi risolve i parametri dei metodi `@DefineFlow` (`DependencyRegistry` nativo vs. `SpringDependencyRegistry`). Non sono due scanner paralleli, è lo stesso meccanismo con un'implementazione diversa di una sola interfaccia.

## Impact on `pipelite-test-support` (story collegata, non in scope qui)

Una volta disponibili `PipeliteContext.getFlowDefinition(name)` e le annotazioni `@FlowConfiguration`/`@DefineFlow`, `pipelite-test-support` potrà aggiungere una nuova `Precondition` (es. `Preconditions.flow(MyFlows.class, "acquire-flow")`) che scansiona direttamente la classe di configurazione di produzione — via riflessione nativa, senza bootstrap di Spring — eliminando la necessità di ridefinire la flow nel test. I dettagli di questa integrazione (firme esatte, gestione dei metodi `@DefineFlow` con dipendenze nei test) sono materia della story `2026-Q3-pipelite-test-fixture.md` e non vengono specificati qui.

## Non-Functional & Quality Requirements

- **Backward compatibility**: nessuna rottura per le app esistenti durante il periodo di deprecazione di `FlowDefinitionRegistrar`. Eccezione esplicita: il dedup silenzioso per `flowName` duplicato in `registerFlowDefinition` viene rimosso (si veda "Breaking change" più sopra) — comportamento accettato come parte della major release.
- **No new Spring dependency**: né `pipelite-core` né `pipelite-test-support` acquisiscono una dipendenza da Spring; solo `pipelite-spring-starter` (che già dipende da Spring) implementa il lato Spring-aware dello scanner.
- **Mutua esclusione verificabile**: deve esistere un test di integrazione in `pipelite-spring-starter` che verifichi che una classe `@FlowConfiguration` registrata in un contesto Spring produca **esattamente una** registrazione della flow (nessun doppio conteggio, nessuna corsa critica fra scanner nativo e Spring-aware); la garanzia si appoggia sul fail-fast di `registerFlowDefinition` su nomi duplicati, non su un controllo locale allo scanner.
- **Error messages**: se un metodo `@DefineFlow` richiede una dipendenza che il `DependencyRegistry` nativo non sa risolvere — perché assente o perché ambigua (più istanze assegnabili allo stesso tipo, si veda sopra) — deve essere lanciata un'eccezione esplicita con il nome del metodo e del tipo non risolvibile (mai un `NullPointerException` silenzioso a runtime, mai una scelta arbitraria tra candidati).

## Open Questions / Out of Scope per la prima iterazione

- Scansione di un intero package sul classpath (vs. registrazione esplicita classe-per-classe) — iterazione successiva.
- `DependencyRegistry` componibili/concatenabili — iterazione successiva.
- Ciclo di vita del registro nativo (singleton per `PipeliteContext` vs. per singola chiamata a `registerFlowConfigurationClass`) — da decidere in fase di design implementativo.
- Binding esplicito tra un parametro di un metodo `@DefineFlow` e un nome registrato specifico nel `DependencyRegistry` (l'equivalente di un `@Qualifier` di Spring), necessario per disambiguare quando sono registrate più istanze dello stesso tipo — iterazione successiva; nella prima iterazione, in presenza di istanze multiple dello stesso tipo, la risoluzione per solo tipo fallisce esplicitamente.
- Rimozione effettiva (vs. sola deprecazione) di `FlowDefinitionRegistrar` — da pianificare come breaking change in una major release separata.
